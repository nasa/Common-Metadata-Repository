(ns cmr.index-set.services.index-service
  "Provide functions to store, retrieve, delete index-sets"
  (:require [clojure.string :as s]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.index-set.data.elasticsearch :as es]
            [cmr.umm.echo10.collection :as collection]
            [cheshire.core :as json]
            [cmr.common.services.errors :as errors]
            [cmr.index-set.services.messages :as m]
            [clojure.walk :as walk]
            [cheshire.core :as cheshire]
            [clojurewerkz.elastisch.rest.index :as esi]
            [cmr.index-set.config.elasticsearch-config :as es-config]
            [cmr.system-trace.core :refer [deftracefn]]))

;; configured list of cmr concepts
(def cmr-concepts [:collection :granule])

(defn gen-valid-index-name
  "Join parts, lowercase letters and change '-' to '_'."
  [prefix-id suffix]
  (s/lower-case (s/replace (format "%s_%s" prefix-id suffix) #"-" "_")))

(defn- build-indices-list-w-config
  "Given a index-set, build list of indices with config."
  [idx-set]
  (let [prefix-id (-> idx-set :index-set :id)]
    (for [concept cmr-concepts
          suffix-index-name (-> idx-set :index-set concept :index-names)]
      (let [indices-config (-> idx-set :index-set concept)
            settings (:settings indices-config)
            mapping (:mapping indices-config)]
        (into {} (list
                   (when suffix-index-name
                     {:index-name (gen-valid-index-name prefix-id suffix-index-name)})
                   (when settings
                     {:settings settings})
                   (when mapping
                     {:mapping mapping})))))))

(defn get-index-names
  "Given a index set build list of index names."
  [idx-set]
  (let [prefix-id (-> idx-set :index-set :id)]
    (for [concept cmr-concepts
          suffix-index-name (-> idx-set :index-set concept :index-names)]
      (gen-valid-index-name prefix-id suffix-index-name))))

(defn given-index-names->es-index-names
  "Map given names with generated elastic index names."
  [index-names-array prefix-id]
  (apply merge
         (for [index-name index-names-array]
           {(keyword index-name)  (gen-valid-index-name prefix-id index-name)})))

(defn prune-index-set
  "Given a full index-set, just retain index-set-id, index-set-name, concepts, index-names info."
  [index-set]
  (let [id (:id index-set)
        name (:name index-set)
        stripped-index-set (first (walk/postwalk #(if (map? %) (dissoc % :create-reason :settings :mapping) %) (list index-set)))]
    {:id id
     :name name
     :concepts (apply merge (for [k (keys stripped-index-set)]
                              (when (map? (k stripped-index-set))
                                {k (given-index-names->es-index-names (first (vals (k stripped-index-set))) id)})))}))

(deftracefn get-index-sets
  "Fetch all index-sets in elastic."
  [context]
  (let [{:keys [index-name mapping]} es-config/idx-cfg-for-index-sets
        idx-mapping-type (first (keys mapping))
        index-sets (es/get-index-sets index-name idx-mapping-type)]
    (vec (map #(let [{:keys [id name concepts]} (:index-set %)]
                 {:id id :name name :concepts concepts}) index-sets))))

(deftracefn index-set-exists?
  "Check index-set existsence"
  [context index-set-id]
  (let [{:keys [index-name mapping]} es-config/idx-cfg-for-index-sets
        idx-mapping-type (first (keys mapping))]
    (es/index-set-exists? index-name idx-mapping-type index-set-id)))

(deftracefn get-index-set
  "Fetch index-set associated with an index-set id."
  [context index-set-id]
  (let [{:keys [index-name mapping]} es-config/idx-cfg-for-index-sets
        idx-mapping-type (first (keys mapping))]
    (es/get-index-set index-name idx-mapping-type index-set-id)))

(deftracefn validate-requested-index-set
  "Verify input index-set is valid."
  [context index-set]
  (let [index-set-id (-> index-set :index-set :id)
        index-set-name (-> index-set :index-set :name)
        indices-w-config (build-indices-list-w-config index-set)
        json-index-set-str (json/generate-string index-set)]
    (cond (not (and (integer? index-set-id) (> index-set-id 0)))
          (errors/throw-service-error :invalid-data
                                      (get-in m/err-msg-fmts [:create :invalid-id])
                                      index-set-id
                                      json-index-set-str)
          (not (and index-set-id index-set-name))
          (errors/throw-service-error :invalid-data
                                      (get-in m/err-msg-fmts [:create :missing-id-name])
                                      json-index-set-str)
          ;; bail out if index-config defs do not have required 3 elements
          (not (apply = (map #(and (contains? % :index-name)
                                   (contains? % :settings) (contains? % :mapping)) indices-w-config)))
          (errors/throw-service-error :invalid-data
                                      (get-in m/err-msg-fmts [:create :missing-idx-cfg])
                                      json-index-set-str)
          ;; bail out if req index-set exists
          (index-set-exists? context (str index-set-id))
          (errors/throw-service-error :conflict
                                      (get-in m/err-msg-fmts [:create :index-set-exists])
                                      index-set-id)
          :else true)))

(deftracefn index-requested-index-set
  "Index requested index-set along with generated elastic index names"
  [context index-set]
  (try
    (let [ index-set-w-es-index-names (assoc-in index-set [:index-set :concepts]
                                                (:concepts (prune-index-set (:index-set index-set))))
          es-doc {:index-set-id (-> index-set :index-set :id)
                  :index-set-name (-> index-set :index-set :name)
                  :index-set-request (json/generate-string index-set-w-es-index-names)}
          doc-id (str (:index-set-id es-doc))
          {:keys [index-name mapping]} es-config/idx-cfg-for-index-sets
          idx-mapping-type (first (keys mapping))]
      (es/save-document-in-elastic context index-name idx-mapping-type doc-id es-doc))
    (catch Exception e
      ;; allow caller to perform rollback on data layer errors
      (throw e))))

(defn- handle-error
  "Handle 4xx type errors correctly"
  [e]
  (let [status (get-in (ex-data e) [:object :status])
        body (cheshire/decode (get-in (ex-data e) [:object :body]) true)
        error (:error body)]
    (when (= status 400) (errors/throw-service-error :bad-request error))
    (when (= status 404) (errors/throw-service-error :not-found error))
    (when (= status 409) (errors/throw-service-error :conflict error))
    (when (= status 422) (errors/throw-service-error :invalid-data error))
    (if (some #{400 404 409 422} [status])
      (error e)
      (errors/internal-error! e (get-in m/err-msg-fmts [:create :fail]) error))))

(deftracefn create-indices-listed-in-index-set
  "Create indices listed in index-set. Rollback occurs if indices creation or index-set doc indexing fails."
  [context index-set]
  (info (format "Creating index-set: %s" index-set))
  (let [valid-index-set? (validate-requested-index-set context index-set)
        index-names (get-index-names index-set)
        indices-w-config (build-indices-list-w-config index-set)
        es-cfg (-> context :system :index :config)
        json-index-set-str (json/generate-string index-set)]
    (if valid-index-set?
      (when-not (esi/exists? (:index-name es-config/idx-cfg-for-index-sets))
        (errors/internal-error! (get-in m/err-msg-fmts [:create :missing-idx])))
      (errors/throw-service-error :invalid-data
                                  (get-in m/err-msg-fmts [:create :invalid-index-set])
                                  json-index-set-str))
    ;; rollback index-set creation if index creation fails
    (try
      (dorun (map #(es/create-index %) indices-w-config))
      (catch Exception e
        (dorun (map #(es/delete-index % es-cfg) index-names))
        (handle-error e)))
    (try
      (index-requested-index-set context index-set)
      (catch Exception e
        (dorun (map #(es/delete-index % es-cfg) index-names))
        (handle-error e)))))

(deftracefn delete-indices-listed-in-index-set
  "Delete all indices having 'id_' as the prefix in the elastic, followed by index-set doc delete"
  [context index-set-id]
  (let [index-names (get-index-names (get-index-set context index-set-id))
        es-cfg (-> context :system :index :config)
        {:keys [index-name mapping]} es-config/idx-cfg-for-index-sets
        idx-mapping-type (first (keys mapping))]
    (dorun (map #(es/delete-index % es-cfg) index-names))
    (es/delete-document-in-elastic context es-cfg index-name idx-mapping-type index-set-id)))

(deftracefn reset
  "Put elastic in a clean state after deleting indices associated with index-sets and index-set docs."
  [context]
  (let [{:keys [index-name mapping]} es-config/idx-cfg-for-index-sets
        idx-mapping-type (first (keys mapping))
        index-set-ids (es/get-index-set-ids index-name idx-mapping-type)
        es-cfg (-> context :system :index :config)]
    ;; delete indices assoc with index-set
    (doseq [id index-set-ids]
      (delete-indices-listed-in-index-set context (str id)))))

