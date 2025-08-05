(ns cmr.indexer.services.index-set-service
  "Provide functions to store, retrieve, delete index-sets"
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.common.config :as common-config]
   [cmr.common.log :as log :refer [info]]
   [cmr.common.rebalancing-collections :as rebalancing-collections]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.indexer.config :as config]
   [cmr.indexer.data.index-set-elasticsearch :as es]
   [cmr.indexer.services.messages :as m]
   [cmr.indexer.indexer-util :as indexer-util])
  (:import
   (clojure.lang ExceptionInfo)))


(defn- add-searchable-generic-types
  "Add the list of supported generic document types to a list of fixed searchable
   concept types presumable from searchable-concept-types"
  [initial-list]
  (reduce (fn [data, item] (conj data (keyword (str "generic-" (name item)))))
          initial-list
          (keys (common-config/approved-pipeline-documents))))

(def searchable-gran-concept-types
  "Defines the concept types that are indexed in elasticsearch and thus searchable."
  [:deleted-granule
   :granule])

(def searchable-non-gran-concept-types
  "Defines the concept types that are indexed in elasticsearch and thus searchable."
  [:autocomplete
   :collection
   :service
   :subscription
   :tag
   :tool
   :variable])

(defn gen-valid-index-name
  "Join parts, lowercase letters and change '-' to '_'."
  [prefix-id suffix]
  (string/lower-case (string/replace (format "%s_%s" prefix-id suffix) #"-" "_")))

(defn- build-indices-list-w-config
  "Given an index-set, build list of indices with config."
  [idx-set es-cluster-name]
  (let [prefix-id (get-in idx-set [:index-set :id])]
    (for [concept-type (cond
                         (= es-cluster-name cmr.elastic-utils.config/gran-elastic-name)
                         (add-searchable-generic-types searchable-gran-concept-types)

                         (= es-cluster-name cmr.elastic-utils.config/non-gran-elastic-name)
                         (add-searchable-generic-types searchable-non-gran-concept-types))
          idx (get-in idx-set [:index-set concept-type :indexes])]
      (let [mapping (get-in idx-set [:index-set concept-type :mapping])
            {idx-name :name settings :settings} idx]
        {:index-name (gen-valid-index-name prefix-id idx-name)
         :settings settings
         :mapping mapping}))))

(defn get-index-names
  "Given an index set build list of index names."
  [idx-set es-cluster-name]
  (let [prefix-id (get-in idx-set [:index-set :id])]
    (for [concept-type (cond
                         (= es-cluster-name cmr.elastic-utils.config/gran-elastic-name)
                         (add-searchable-generic-types searchable-gran-concept-types)

                         (= es-cluster-name cmr.elastic-utils.config/non-gran-elastic-name)
                         (add-searchable-generic-types searchable-non-gran-concept-types)

                         :else (throw (Exception. (str "Bad es-cluster name given in get index names. Was given " es-cluster-name "  but expected gran-elastic or non-gran-elastic."))))
          idx (get-in idx-set [:index-set concept-type :indexes])]
      (gen-valid-index-name prefix-id (:name idx)))))

(defn given-index-names->es-index-names
  "Map given names with generated elastic index names."
  [index-names-array prefix-id]
  (apply merge
         (for [index-name index-names-array]
           {(keyword index-name) (gen-valid-index-name prefix-id index-name)})))

(defn prune-index-set
  "Returns the index set with only the id, name, and a map of concept types to
  the index name map."
  [index-set es-cluster-name]
  (let [prefix (:id index-set)
        generic-searchable-concept-types (cond
                                           (= es-cluster-name cmr.elastic-utils.config/gran-elastic-name)
                                           ;; we will not add generics to the searchable gran concept types
                                           searchable-gran-concept-types

                                           (= es-cluster-name cmr.elastic-utils.config/non-gran-elastic-name)
                                           (add-searchable-generic-types searchable-non-gran-concept-types)

                                           :else (throw (Exception. (str "Es-cluster name expected was not given. Given es-cluster-name was " es-cluster-name)))
                                           )
        ;_ (println "10636 - generic-searchable-concept-types for es-cluster-name " es-cluster-name "  is " generic-searchable-concept-types)
        ]
    {:id (:id index-set)
     :name (:name index-set)
     :concepts (into {} (for [concept-type generic-searchable-concept-types]
                          [concept-type
                           (into {} (for [idx (get-in index-set [concept-type :indexes])]
                                      [(keyword (:name idx)) (gen-valid-index-name prefix (:name idx))]))]))}))

;; TODO 10636 move this func to the commons lib
(defn deep-merge
  "Recursively merges two maps.
   If a key exists in both and its value is also a map,
   it recursively merges those inner maps. Otherwise,
   it prefers the value from the second map."
  [m1 m2]
  (merge-with
    (fn [v1 v2]
      (cond
        (and (map? v1) (map? v2)) (deep-merge v1 v2)
        :else v2))
    m1 m2))

(defn get-index-sets
  "Fetch all index-sets in elastic."
  [context]
  (let [{:keys [index-name mapping]} (config/idx-cfg-for-index-sets cmr.elastic-utils.config/non-gran-elastic-name)
        idx-mapping-type (first (keys mapping))
        non-gran-index-set-array (es/get-index-sets (indexer-util/context->es-store context cmr.elastic-utils.config/non-gran-elastic-name) index-name idx-mapping-type)

        {:keys [index-name mapping]} (config/idx-cfg-for-index-sets cmr.elastic-utils.config/gran-elastic-name)
        idx-mapping-type (first (keys mapping))

        gran-index-set-array (es/get-index-sets (indexer-util/context->es-store context cmr.elastic-utils.config/gran-elastic-name) index-name idx-mapping-type)

        all-index-set-array (map deep-merge gran-index-set-array non-gran-index-set-array)
        result (map #(select-keys (:index-set %) [:id :name :concepts])
                    all-index-set-array)]
    result))

(defn index-set-exists?
  "Check index-set existence"
  [context es-cluster-name index-set-id]
  (let [{:keys [index-name mapping]} (config/idx-cfg-for-index-sets es-cluster-name)
        idx-mapping-type (first (keys mapping))]
    (es/index-set-exists? (indexer-util/context->es-store context es-cluster-name) index-name idx-mapping-type index-set-id)))

(defn get-index-set
  "Fetch index-set associated with an index-set id."
  [context es-cluster-name index-set-id]
  (or (es/get-index-set context es-cluster-name index-set-id)
        (errors/throw-service-error :not-found
                                    (m/index-set-not-found-msg index-set-id))))

(defn index-set-id-validation
  "Verify id is a positive integer."
  [index-set]
  (let [index-set-id (get-in index-set [:index-set :id])
        json-index-set-str (json/generate-string index-set)]
    (when-not (and (integer? index-set-id) (pos? index-set-id))
      (m/invalid-id-msg index-set-id json-index-set-str))))

(defn id-name-existence-check
  "Check for index-set id and name."
  [index-set]
  (let [index-set-id (get-in index-set [:index-set :id])
        index-set-name (get-in index-set [:index-set :name])
        json-index-set-str (json/generate-string index-set)]
    (when-not (and index-set-id index-set-name)
      (m/missing-id-name-msg json-index-set-str))))

(defn index-cfg-validation
  "Verify if required elements are present to create an elastic index."
  [index-set es-cluster-name]
  (let [indices-w-config (build-indices-list-w-config index-set es-cluster-name)
        json-index-set-str (json/generate-string index-set)]
    (when-not (every? true? (map #(and (boolean (% :index-name))
                                       (boolean (% :settings)) (boolean (% :mapping))) indices-w-config))
      (m/missing-idx-cfg-msg json-index-set-str))))

(defn index-set-existence-check
  "Check index-set existence"
  [context es-cluster-name index-set]
  (let [index-set-id (get-in index-set [:index-set :id])
        {:keys [index-name mapping]} (config/idx-cfg-for-index-sets es-cluster-name)
        idx-mapping-type (first (keys mapping))]
    (when (es/index-set-exists? (indexer-util/context->es-store context es-cluster-name) index-name idx-mapping-type index-set-id)
      (m/index-set-exists-msg index-set-id))))

(defn validate-requested-index-set
  "Verify input index-set is valid."
  [context es-cluster-name index-set allow-update?]
  (when-not allow-update?
    (when-let [error (index-set-existence-check context es-cluster-name index-set)]
      (errors/throw-service-error :conflict error))
    (when-let [error (id-name-existence-check index-set)]
      (errors/throw-service-error :invalid-data error)))
  (when-let [error (index-set-id-validation index-set)]
    (errors/throw-service-error :invalid-data error))
  (when-let [error (index-cfg-validation index-set es-cluster-name)]
    (errors/throw-service-error :invalid-data error)))

;; TODO 10636 this is where :concepts is being set in index-set
(defn index-requested-index-set
  "Index requested index-set along with generated elastic index names"
  [context index-set es-cluster-name]
  (let [index-set-w-es-index-names (assoc-in index-set [:index-set :concepts]
                                             (:concepts (prune-index-set (:index-set index-set) es-cluster-name)))
        encoded-index-set-w-es-index-names (-> index-set-w-es-index-names
                                               json/generate-string
                                               util/string->gzip-base64)
        es-doc {:index-set-id (get-in index-set [:index-set :id])
                :index-set-name (get-in index-set [:index-set :name])
                :index-set-request encoded-index-set-w-es-index-names}
        doc-id (str (:index-set-id es-doc))
        {:keys [index-name mapping]} (config/idx-cfg-for-index-sets es-cluster-name)
        idx-mapping-type (first (keys mapping))]
    (es/save-document-in-elastic context index-name idx-mapping-type doc-id es-doc)))

(defn create-index-set
  "Create indices listed in index-set for specific elastic cluster. Rollback occurs if indices creation or
  index-set doc indexing fails."
  [context es-cluster-name index-set]
  (validate-requested-index-set context es-cluster-name index-set false)
  ;(println "10636- INSIDE create-index-set for es-cluster-name " es-cluster-name " with index-set = " index-set)
  (let [index-names (get-index-names index-set es-cluster-name)
        ;_ (println "index-names = " index-names)
        indices-w-config (build-indices-list-w-config index-set es-cluster-name)
        ;_ (println "indices-w-config is " indices-w-config)
        es-store (indexer-util/context->es-store context es-cluster-name)]
    (when-let [generic-docs (keys (common-config/approved-pipeline-documents))]
      (info "This instance of CMR will publish Elasticsearch indices for the following generic document types:" generic-docs))
    ;; rollback index-set creation if index creation fails
    (try
      (dorun (map #(es/create-index es-store %) indices-w-config))
      (catch ExceptionInfo e
        ;; TODO: Generic work: why does this fail to roll back with bad generics?
        (info "failed to create index, roll back, this does not always work")
        (dorun (map #(es/delete-index es-store %) index-names))
        (m/handle-elastic-exception "attempt to create indices of index-set failed" e)))
    (try
      (index-requested-index-set context index-set es-cluster-name)
      (catch ExceptionInfo e
        (dorun (map #(es/delete-index es-store %) index-names))
        (m/handle-elastic-exception "attempt to index index-set doc failed"  e)))))

(defn update-index-set
  "Updates indices in the index set"
  [context es-cluster-name index-set]
  (validate-requested-index-set context es-cluster-name index-set true)
  (let [indices-w-config (build-indices-list-w-config index-set es-cluster-name)
        es-store (indexer-util/context->es-store context es-cluster-name)]

    (doseq [idx indices-w-config]
      (es/update-index es-store idx))

    (index-requested-index-set context index-set es-cluster-name)))

(defn delete-index-set
  "Delete all indices having 'id_' as the prefix in all the elastic clusters, followed by
  index-set doc delete"
  [context index-set-id es-cluster-name]
    (let [index-names (get-index-names (get-index-set context es-cluster-name index-set-id) es-cluster-name)
          {:keys [index-name mapping]} (config/idx-cfg-for-index-sets es-cluster-name)
          idx-mapping-type (first (keys mapping))]
      (dorun (map #(es/delete-index (indexer-util/context->es-store context es-cluster-name) %) index-names))
      (es/delete-document context index-name idx-mapping-type index-set-id)))

(defn- add-rebalancing-collection
  "Adds a new rebalancing collections to the set of rebalancing collections."
  [rebalancing-colls concept-id]
  (if rebalancing-colls
    (if (contains? (set rebalancing-colls) concept-id)
      (errors/throw-service-error
        :bad-request
        (format
          "The index set already contains rebalancing collection [%s]"
          concept-id))
      (conj rebalancing-colls concept-id))
    #{concept-id}))

(defn- remove-rebalancing-collection
  "Removes a rebalancing collections from the set of rebalancing collections."
  [rebalancing-colls concept-id]
  (let [rebalancing-colls-set (set rebalancing-colls)]
    (if (contains? rebalancing-colls-set concept-id)
      (seq (disj rebalancing-colls-set concept-id))
      (errors/throw-service-error
        :bad-request
        (format
          "The index set does not contain the rebalancing collection [%s]"
          concept-id)))))

(defn- validate-granule-index-does-not-exist
  "Validates that a granule index does not already exist in the index set for the given collection
  concept ID."
  [index-set collection-concept-id]
  (let [existing-index-names (->> (get-in index-set [:index-set :granule :indexes]) (map :name) set)]
    (when (contains? existing-index-names collection-concept-id)
      (errors/throw-service-error
       :bad-request
       (format
         "The collection [%s] already has a separate granule index."
         collection-concept-id)))))

(defn- validate-granule-index-exists
  "Validates that a granule index exists in the index set for the given collection concept ID."
  [index-set collection-concept-id]
  (let [existing-index-names (->> (get-in index-set [:index-set :granule :indexes]) (map :name) set)]
    (when-not (contains? existing-index-names collection-concept-id)
      (errors/throw-service-error
       :bad-request
       (format
         "The collection [%s] does not have a separate granule index."
         collection-concept-id)))))

(defn- add-new-granule-index
  "Adds a new granule index for the given collection. Validates the collection
  does not already have an index."
  [index-set collection-concept-id]
  (validate-granule-index-does-not-exist index-set collection-concept-id)
  (let [individual-index-settings (get-in index-set [:index-set :granule :individual-index-settings])]
    (update-in index-set [:index-set :granule :indexes]
               conj
               {:name collection-concept-id
                :settings individual-index-settings})))

(defn- remove-granule-index-from-index-set
  "Removes the separate granule index for the given collection from the index set. Validates the
  collection index is listed in the index-set."
  [index-set collection-concept-id]
  (validate-granule-index-exists index-set collection-concept-id)
  (update-in index-set [:index-set :granule :indexes]
             (fn [indexes]
               (remove #(= collection-concept-id (:name %))
                       indexes))))

(defn mark-collection-as-rebalancing
  "Marks the given collection as rebalancing in the index set."
  [context index-set-id concept-id target]
  (info (format "Starting to rebalance granules for collection [%s] to target [%s]."
                concept-id target))
  (rebalancing-collections/validate-target target concept-id)
  (let [index-set (as-> (get-index-set context cmr.elastic-utils.config/gran-elastic-name index-set-id) index-set
                        (update-in
                          index-set
                          [:index-set :granule :rebalancing-collections]
                          add-rebalancing-collection concept-id)
                        (update-in
                          index-set
                          [:index-set :granule :rebalancing-targets]
                          assoc concept-id target)
                        (update-in
                         index-set
                         [:index-set :granule :rebalancing-status]
                         assoc concept-id "IN_PROGRESS")
                        (if (= "small-collections" target)
                          (do
                             (validate-granule-index-exists index-set concept-id)
                             index-set)
                          (add-new-granule-index index-set concept-id)))]
    ;; Update the index set. This will create the new collection indexes as needed.
    (update-index-set context cmr.elastic-utils.config/gran-elastic-name index-set)))

(defn finalize-collection-rebalancing
  "Removes the collection from the list of rebalancing collections"
  [context index-set-id concept-id]
  (let [gran-index-set (get-index-set context cmr.elastic-utils.config/gran-elastic-name index-set-id)
        target (get-in gran-index-set [:index-set :granule :rebalancing-targets (keyword concept-id)])
        _ (info (format "Finalizing rebalancing granules for collection [%s] to target [%s]."
                        concept-id target))
        _ (rebalancing-collections/validate-target target concept-id)
        index-set (as-> gran-index-set index-set
                        (update-in
                          index-set
                          [:index-set :granule :rebalancing-collections]
                          remove-rebalancing-collection concept-id)
                        (update-in
                          index-set
                          [:index-set :granule :rebalancing-targets]
                          dissoc (keyword concept-id))
                        (update-in
                         index-set
                         [:index-set :granule :rebalancing-status]
                         dissoc (keyword concept-id))
                        (if (= "small-collections" target)
                          (remove-granule-index-from-index-set index-set concept-id)
                          index-set))]
    ;; Update the index set. This will create the new collection indexes as needed.
    (update-index-set context cmr.elastic-utils.config/gran-elastic-name index-set)))

(defn update-collection-rebalancing-status
  "Update the collection rebalancing status."
  [context index-set-id concept-id status]
  (rebalancing-collections/validate-status status)
  (info (format "Updating collection rebalancing status for collection [%s] to status [%s]."
                concept-id status))
  (let [index-set (get-index-set context cmr.elastic-utils.config/gran-elastic-name index-set-id)]
    (when-not (get-in index-set [:index-set :granule :rebalancing-status (keyword concept-id)])
      (errors/throw-service-error
       :bad-request
       (format
         "The index set does not contain the rebalancing collection [%s]"
         concept-id)))
    (update-index-set
     context
     cmr.elastic-utils.config/gran-elastic-name
     (update-in
      index-set
      [:index-set :granule :rebalancing-status]
      assoc (keyword concept-id) status))))

(defn reset
  "Put elastic in a clean state after deleting indices associated with index-sets and index-set docs."
  [context]
  (let [{:keys [index-name]} (config/idx-cfg-for-index-sets cmr.elastic-utils.config/gran-elastic-name)
        gran-index-set-ids (es/get-index-set-ids
                             (indexer-util/context->es-store context cmr.elastic-utils.config/gran-elastic-name)
                             index-name
                             "_doc")
        {:keys [index-name]} (config/idx-cfg-for-index-sets cmr.elastic-utils.config/non-gran-elastic-name)
        non-gran-index-set-ids (es/get-index-set-ids
                                 (indexer-util/context->es-store context :non-gran-elastic)
                                 index-name
                                 "_doc")]
    ;; delete indices assoc with index-set
    (doseq [id gran-index-set-ids]
      (delete-index-set context (str id) cmr.elastic-utils.config/gran-elastic-name))

    (doseq [id non-gran-index-set-ids]
      (delete-index-set context (str id) cmr.elastic-utils.config/non-gran-elastic-name))))
