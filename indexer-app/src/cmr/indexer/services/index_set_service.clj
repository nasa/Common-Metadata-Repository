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
   [cmr.indexer.services.messages :as m])
  (:import
   (clojure.lang ExceptionInfo)))


(defn- add-searchable-generic-types
  "Add the list of supported generic document types to a list of fixed searchable
   concept types presumable from searchable-concept-types"
  [initial-list]
  (reduce (fn [data, item] (conj data (keyword (str "generic-" (name item)))))
          initial-list
          (keys (common-config/approved-pipeline-documents))))

(def searchable-concept-types
  "Defines the concept types that are indexed in elasticsearch and thus searchable."
  [:autocomplete
   :collection
   :deleted-granule
   :granule
   :service
   :subscription
   :tag
   :tool
   :variable])

(defn context->es-store
  [context]
  (get-in context [:system :db]))

(defn gen-valid-index-name
  "Join parts, lowercase letters and change '-' to '_'."
  [prefix-id suffix]
  (string/lower-case (string/replace (format "%s_%s" prefix-id suffix) #"-" "_")))

(defn- build-indices-list-w-config
  "Given an index-set, build list of indices with config."
  [idx-set]
  (let [prefix-id (get-in idx-set [:index-set :id])]
    (for [concept-type (add-searchable-generic-types searchable-concept-types)
          idx (get-in idx-set [:index-set concept-type :indexes])]
      (let [mapping (get-in idx-set [:index-set concept-type :mapping])
            {idx-name :name settings :settings} idx]
        {:index-name (gen-valid-index-name prefix-id idx-name)
         :settings settings
         :mapping mapping}))))

(defn get-index-names
  "Given a index set build list of index names."
  [idx-set]
  (let [prefix-id (get-in idx-set [:index-set :id])]
    (for [concept-type (add-searchable-generic-types searchable-concept-types)
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
  [index-set]
  (let [prefix (:id index-set)]
    {:id (:id index-set)
     :name (:name index-set)
     :concepts (into {} (for [concept-type (add-searchable-generic-types searchable-concept-types)]
                          [concept-type
                           (into {} (for [idx (get-in index-set [concept-type :indexes])]
                                      [(keyword (:name idx)) (gen-valid-index-name prefix (:name idx))]))]))}))

(defn get-index-sets
  "Fetch all index-sets in elastic."
  [context]
  (let [{:keys [index-name mapping]} config/idx-cfg-for-index-sets
        idx-mapping-type (first (keys mapping))
        index-sets (es/get-index-sets (context->es-store context) index-name idx-mapping-type)]
    (map #(select-keys (:index-set %) [:id :name :concepts])
         index-sets)))

(defn index-set-exists?
  "Check index-set existsence"
  [context index-set-id]
  (let [{:keys [index-name mapping]} config/idx-cfg-for-index-sets
        idx-mapping-type (first (keys mapping))]
    (es/index-set-exists? (context->es-store context) index-name idx-mapping-type index-set-id)))

(defn get-index-set
  "Fetch index-set associated with an index-set id."
  [context index-set-id]
  (or (es/get-index-set context index-set-id)
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
  [index-set]
  (let [indices-w-config (build-indices-list-w-config index-set)
        json-index-set-str (json/generate-string index-set)]
    (when-not (every? true? (map #(and (boolean (% :index-name))
                                       (boolean (% :settings)) (boolean (% :mapping))) indices-w-config))
      (m/missing-idx-cfg-msg json-index-set-str))))

(defn index-set-existence-check
  "Check index-set existence"
  [context index-set]
  (let [index-set-id (get-in index-set [:index-set :id])
        {:keys [index-name mapping]} config/idx-cfg-for-index-sets
        idx-mapping-type (first (keys mapping))]
    (when (es/index-set-exists? (context->es-store context) index-name idx-mapping-type index-set-id)
      (m/index-set-exists-msg index-set-id))))

(defn validate-requested-index-set
  "Verify input index-set is valid."
  [context index-set allow-update?]
  (when-not allow-update?
    (when-let [error (index-set-existence-check context index-set)]
      (errors/throw-service-error :conflict error))
    (when-let [error (id-name-existence-check index-set)]
      (errors/throw-service-error :invalid-data error)))
  (when-let [error (index-set-id-validation index-set)]
    (errors/throw-service-error :invalid-data error))
  (when-let [error (index-cfg-validation index-set)]
    (errors/throw-service-error :invalid-data error)))

(defn index-requested-index-set
  "Index requested index-set along with generated elastic index names"
  [context index-set]
  (let [index-set-w-es-index-names (assoc-in index-set [:index-set :concepts]
                                             (:concepts (prune-index-set (:index-set index-set))))
        encoded-index-set-w-es-index-names (-> index-set-w-es-index-names
                                               json/generate-string
                                               util/string->gzip-base64)
        es-doc {:index-set-id (get-in index-set [:index-set :id])
                :index-set-name (get-in index-set [:index-set :name])
                :index-set-request encoded-index-set-w-es-index-names}
        doc-id (str (:index-set-id es-doc))
        {:keys [index-name mapping]} config/idx-cfg-for-index-sets
        idx-mapping-type (first (keys mapping))]
    (es/save-document-in-elastic context index-name idx-mapping-type doc-id es-doc)))

(defn create-index-set
  "Create indices listed in index-set. Rollback occurs if indices creation or
  index-set doc indexing fails."
  [context index-set]
  (validate-requested-index-set context index-set false)
  (let [index-names (get-index-names index-set)
        indices-w-config (build-indices-list-w-config index-set)
        es-store (context->es-store context)]
    (when-let [generic-docs (keys (common-config/approved-pipeline-documents))]
      (info "This instance of CMR will publish Elasticsearch indices for the following generic document types:" generic-docs))
    ;; rollback index-set creation if index creation fails
    (try
      (dorun (map #(es/create-index es-store %) indices-w-config))
      (catch ExceptionInfo e
        ;; TODO: Generic work: why does this fail to roll back with bad generics?
        (println "failed to create index, roll back, this does not always work")
        (dorun (map #(es/delete-index es-store %) index-names))
        (m/handle-elastic-exception "attempt to create indices of index-set failed" e)))
    (try
      (index-requested-index-set context index-set)
      (catch ExceptionInfo e
        (dorun (map #(es/delete-index es-store %) index-names))
        (m/handle-elastic-exception "attempt to index index-set doc failed"  e)))))

(defn update-index-set
  "Updates indices in the index set"
  [context index-set]
  (info "Updating index-set" (pr-str index-set))
  (validate-requested-index-set context index-set true)
  (let [indices-w-config (build-indices-list-w-config index-set)
        es-store (context->es-store context)]

    (doseq [idx indices-w-config]
      (es/update-index es-store idx))

    (index-requested-index-set context index-set)))

(defn delete-index-set
  "Delete all indices having 'id_' as the prefix in the elastic, followed by
  index-set doc delete"
  [context index-set-id]
  (let [index-names (get-index-names (get-index-set context index-set-id))
        {:keys [index-name mapping]} config/idx-cfg-for-index-sets
        idx-mapping-type (first (keys mapping))]
    (dorun (map #(es/delete-index (context->es-store context) %) index-names))
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
  (let [index-set (as-> (get-index-set context index-set-id) index-set
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
    (update-index-set context index-set)))

(defn finalize-collection-rebalancing
  "Removes the collection from the list of rebalancing collections"
  [context index-set-id concept-id]
  (let [index-set (get-index-set context index-set-id)
        target (get-in index-set [:index-set :granule :rebalancing-targets (keyword concept-id)])
        _ (info (format "Finalizing rebalancing granules for collection [%s] to target [%s]."
                        concept-id target))
        _ (rebalancing-collections/validate-target target concept-id)
        index-set (as-> index-set index-set
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
    (update-index-set context index-set)))

(defn update-collection-rebalancing-status
  "Update the collection rebalancing status."
  [context index-set-id concept-id status]
  (rebalancing-collections/validate-status status)
  (info (format "Updating collection rebalancing status for collection [%s] to status [%s]."
                concept-id status))
  (let [index-set (get-index-set context index-set-id)]
    (when-not (get-in index-set [:index-set :granule :rebalancing-status (keyword concept-id)])
      (errors/throw-service-error
       :bad-request
       (format
         "The index set does not contain the rebalancing collection [%s]"
         concept-id)))
    (update-index-set
     context
     (update-in
      index-set
      [:index-set :granule :rebalancing-status]
      assoc (keyword concept-id) status))))

(defn reset
  "Put elastic in a clean state after deleting indices associated with index-
  sets and index-set docs."
  [context]
  (let [{:keys [index-name]} config/idx-cfg-for-index-sets
        index-set-ids (es/get-index-set-ids
                       (context->es-store context)
                       index-name
                       "_doc")]
    ;; delete indices assoc with index-set
    (doseq [id index-set-ids]
      (delete-index-set context (str id)))))
