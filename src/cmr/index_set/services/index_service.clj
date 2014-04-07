(ns cmr.index-set.services.index-service
  "Provide functions to index concept"
  (:require [clojure.string :as s]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.index-set.data.elasticsearch :as es]
            [cmr.umm.echo10.collection :as collection]
            [cheshire.core :as json]
            [cmr.common.services.errors :as errors]
            [clojurewerkz.elastisch.rest.index :as esi]
            [cmr.index-set.config.elasticsearch-config :as es-config]
            [cmr.system-trace.core :refer [deftracefn]]))

;; configured list of cmr concepts
(def cmr-concepts [:collection :granule])

(defn- build-indices-list-w-config
  "given a index-set, build list of indices with config"
  [idx-set]
  (let [prefix-id (-> idx-set :index-set :id)]
    (for [concept cmr-concepts
          suffix-index-name (-> idx-set
                                :index-set
                                concept
                                :index-names)]
      (let [indices-config (-> idx-set
                               :index-set
                               concept)
            settings (:settings indices-config)
            mapping (:mapping indices-config)]
        {:index-name (str prefix-id "_" suffix-index-name)
         :settings settings
         :mapping mapping}))))

(defn get-index-names
  "given a index set build list of index names"
  [idx-set]
  (let [prefix-id (-> idx-set :index-set :id)]
    (for [concept cmr-concepts
          suffix-index-name (-> idx-set
                                :index-set
                                concept
                                :index-names)]
      (str prefix-id "_" suffix-index-name))))

(deftracefn index-requested-index-set
  "Index requested index-set"
  [context index-set]
  (try
    (let [es-doc {:index-set-id (-> index-set :index-set :id)
                  :index-set-name (-> index-set :index-set :name)
                  :index-set-request (json/generate-string index-set)}
          doc-id (str (:index-set-id es-doc))
          {:keys [index-name mapping]} es-config/index-w-config
          es-mapping-type (first (keys mapping))]
      (es/save-document-in-elastic context index-name es-mapping-type doc-id es-doc))
    (catch Exception e
      ;; allow caller to perform rollback on data layer errors
      (throw e))))

(deftracefn get-index-set
  "Fetch index-set associated with an id"
  [context index-set-id]
  (let [{:keys [index-name mapping]} es-config/index-w-config
        es-mapping-type (first (keys mapping))]
    (es/get-index-set index-name es-mapping-type index-set-id)))

(deftracefn validate-requested-index-set
  "verify input index-set is valid"
  [context index-set]
  (comment "note: 'index-name' in let binding is where the requested index-set will be indexed")
  (let [index-set-id (-> index-set :index-set :id)
        index-set-name (-> index-set :index-set :name)
        indices-w-config (build-indices-list-w-config index-set)
        index-name (:index-name es-config/index-w-config)]
    (cond (not (and index-set-id index-set-name))
          (errors/throw-service-error :invalid-data (format "%s - given index-set: %s"
                                                            "missing id or name"
                                                            (json/generate-string index-set)))
          (not (apply = (map #(and (contains? % :index-name) (contains? % :settings) (contains? % :mapping))
                             (build-indices-list-w-config index-set))))
          (errors/throw-service-error :invalid-data (format "%s - given index-set: %s"
                                                            "missing index names or settings or mapping"
                                                            (json/generate-string index-set)))

          (not (get-index-set context (str index-set-id)))
          (errors/throw-service-error :conflict (format "index-set id: %s already exists"
                                                        index-set-id
                                                        (json/generate-string index-set)))
          :else true)))

(deftracefn create-indices-listed-in-index-set
  "Create indices listed in index-set. Rollback occurs if indices creation or index-set doc indexing fails."
  [context index-set]
  (info (format "Creating index-set: %s" index-set))
  (let [valid-index-set? (validate-requested-index-set context index-set)
        index-names (get-index-names index-set)
        indices-w-config (build-indices-list-w-config index-set)
        es-cfg (-> context :system :index :config)]
    (info index-names)
    (if valid-index-set?
      (when-not (esi/exists? (:index-name es-config/index-w-config))
        (errors/internal-error! "index-sets index does not exist in elastic"))
      (errors/throw-service-error :invalid-data (format "invalid index-set: %s" (json/generate-string index-set))))

    ;; rollback index-set creation if index creation fails
    (try
      (dorun (map #(es/create-index %) indices-w-config))
      (catch Exception e
        (dorun (map #(es/delete-index % es-cfg) index-names))
        (errors/internal-error! (format "failed to create index-set: %s" (json/generate-string index-set) e))))

    ;; rollback if requested index-set was not indexed into elastic
    (try
      (index-requested-index-set context index-set)
      (catch Exception e
        (dorun (map #(es/delete-index % es-cfg) index-names))
        (errors/internal-error! (format "failed to create index-set: %s" (json/generate-string index-set) e))))))


(deftracefn delete-indices-listed-in-index-set
  "delete all indices having 'id_' as the prefix in the elastic"
  [context index-set-id]
  (let [
        index-names (get-index-names (get-index-set context index-set-id))
        es-cfg (-> context :system :index :config)]
    (info index-names)
    (dorun (map #(es/delete-index % es-cfg) index-names))))


(comment
  (create-indices-listed-in-index-set nil sample-index-set)
  (index-requested-index-set nil sample-index-set)
  (delete-indices-listed-in-index-set nil "3"))
