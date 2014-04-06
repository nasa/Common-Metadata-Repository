(ns cmr.index-set.services.index-service
  "Provide functions to index concept"
  (:require [clojure.string :as s]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.index-set.data.elasticsearch :as es]
            [cmr.umm.echo10.collection :as collection]
            [cheshire.core :as json]
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
        { :index-name (str prefix-id "_" suffix-index-name)
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
  (info (format "Indexing requested index-set: %s" index-set))
  (let [es-doc {:index-set-id (-> index-set :index-set :id)
                :index-set-name (-> index-set :index-set :name)
                :index-set-request (json/generate-string index-set)}
        doc-id (str (:index-set-id es-doc))
        {:keys [index-name mapping]} es-config/index-w-config
        es-mapping-type (first (keys mapping))]
    (es/save-document-in-elastic context index-name es-mapping-type doc-id es-doc)))

;; TODO - implement rollback (remove indices alleardy created in this index-set if index creation in elastic fails)
(deftracefn create-indices-listed-in-index-set
  "create indices listed in index-set"
  [context index-set]
  (let [index-names (build-indices-list-w-config index-set)]
    (info index-names)
    (dorun (map #(es/create-index %) index-names))
    (index-requested-index-set context index-set)))

(deftracefn get-index-set
  "Fetch index-set associated with an id"
  [context index-set-id]
  (let [{:keys [index-name mapping]} es-config/index-w-config
        es-mapping-type (first (keys mapping))]
    (es/get-index-set index-name es-mapping-type index-set-id)))

(deftracefn delete-indices-listed-in-index-set
  "delete all indices having 'id_' as the prefix in the elastic"
  [context index-set-id]
  (let [
        index-names (get-index-names (get-index-set context index-set-id))
        es-config (-> context :system :index :config)]
    (info index-names)
    (dorun (map #(es/delete-index % es-config) index-names))))


(comment
  (create-indices-listed-in-index-set nil sample-index-set)
  (index-requested-index-set nil sample-index-set)
  (delete-indices-listed-in-index-set nil "3"))
