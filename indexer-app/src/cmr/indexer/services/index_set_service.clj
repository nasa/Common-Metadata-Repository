(ns cmr.indexer.services.index-set-service
  "Provide functions to store, retrieve, delete index-sets"
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.common.config :as common-config]
   [cmr.common.log :as log :refer [error info]]
   [cmr.common.rebalancing-collections :as rebalancing-collections]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.elastic-utils.es-helper :as es-helper]
   [cmr.elastic-utils.es-index-helper :as esi-helper]
   [cmr.elastic-utils.index-util :as es-util]
   [cmr.indexer.common.index-set-util :as index-set-util]
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

(defn get-canonical-key-name
  "Removes the leading number prefix (e.g., '1_') and trailing shard count (e.g., '_100_shards')
     from an index name. If the index name represents a concept ID (starts with a letter followed
     by numbers), formats it as a concept ID with hyphen separator.
     Examples:
     '1_small_collections_100_shards' -> 'small_collections'
     '1_c2317033465_nsidc_ecs' -> 'C2317033465-NSIDC_ECS'
     '1_collections_v2' -> 'collections-v2'
     '1_v123_nsidc_ecs' -> 'V123-NSIDC_ECS'"
  [index-name]
  (when index-name
    (let [;; Remove leading number and trailing shard count
          cleaned (-> index-name
                      (string/replace #"^\d+_" "")
                      (string/replace #"_\d+_shards$" ""))
            ;; Check if it starts with a concept pattern (letter followed by digits)
          is-concept? (re-matches #"^[a-z]\d+_.*" cleaned)]
      (if is-concept?
          ;; Format as concept ID: uppercase and replace first underscore with hyphen
        (let [first-underscore-idx (string/index-of cleaned "_")]
          (if first-underscore-idx
            (str (string/upper-case (subs cleaned 0 first-underscore-idx))
                 "-"
                 (string/upper-case (subs cleaned (inc first-underscore-idx))))
            (string/upper-case cleaned)))
          ;; Regular index: replace last underscore with hyphen if it has version pattern
        (if (re-find #"_v\d+" cleaned)
          (string/replace cleaned #"_(?=v\d+$)" "-")
          cleaned)))))

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
                                      [(keyword (get-canonical-key-name (:name idx))) (gen-valid-index-name prefix (:name idx))]))]))}))

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
    (when (es/index-set-exists? (indexer-util/context->es-store context) index-name idx-mapping-type index-set-id)
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
        es-store (indexer-util/context->es-store context)]
    (when-let [generic-docs (keys (common-config/approved-pipeline-documents))]
      (info "This instance of CMR will publish Elasticsearch indices for the following generic document types:" generic-docs))
    ;; rollback index-set creation if index creation fails
    (try
      (dorun (map #(es/create-index-and-alias es-store %) indices-w-config))
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
        es-store (indexer-util/context->es-store context)]

    (doseq [idx indices-w-config]
      (es/update-index es-store idx))

    (index-requested-index-set context index-set)))

(defn delete-index-set
  "Delete all indices having 'id_' as the prefix in the elastic, followed by
  index-set doc delete"
  [context index-set-id]
  (let [index-names (get-index-names (index-set-util/get-index-set context index-set-id))
        {:keys [index-name mapping]} config/idx-cfg-for-index-sets
        idx-mapping-type (first (keys mapping))]
    (dorun (map #(es/delete-index (indexer-util/context->es-store context) %) index-names))
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

(defn get-resharding-indexes
  "get the set of resharding indexes and targets from index-set"
  [index-set]
  (reduce (fn [acc x]
            (if-let [index-targets (:resharding-targets x)]
              (-> acc
                  (into (map name (keys index-targets)))
                  (into (vals index-targets)))
              acc))
          #{}
          (vals (:index-set index-set))))

(defn index-name->concept-id
  "Extracts a concept ID from a granule index name. Does not work for non-granule indexes.
   Examples:
   '1_c2317035855_nsidc_ecs' -> 'C2317035855-NSIDC_ECS'
   '1_c2317035855_nsidc_ecs_5_shards' -> 'C2317035855-NSIDC_ECS'"
  [index-name]
  (when index-name
    (let [;; Remove shard count suffix if present
          without-shards (string/replace index-name #"_\d+_shards$" "")
          ;; Remove leading number prefix (e.g., "1_")
          without-prefix (string/replace without-shards #"^\d+_" "")
          ;; Find the first underscore after the concept type+number
          ;; Concept pattern: letter followed by numbers/underscores until we hit provider
          ;; Match: concept letter + any combo of numbers/underscores that form the concept ID
          first-underscore-idx (string/index-of without-prefix "_")]
      (when first-underscore-idx
        (let [concept-part (subs without-prefix 0 first-underscore-idx)
              provider-part (subs without-prefix (inc first-underscore-idx))]
          (str (string/upper-case concept-part)
               "-"
               (string/upper-case provider-part)))))))

(defn- is-rebalancing?
  "Evaluates to true if the index is being used for rebalancing"
  [index-set index]
  ;; if rebalancing-targets length == 0 then return false
  ;; if the index is the small-collections index and any rebalancing is happening, return true
  ;; if the concept-id for the index is in the rebalancing set, return true
  ;; else return false
  (let [rebalancing-collections (get-in index-set [:index-set :granule :rebalancing-collections])]
    (if (= 0 (count rebalancing-collections))
      ;; no rebalancing is happening
      false
      ;; rebalancing is happening
      (let [small-collections-index (get-in index-set [:index-set :concepts :granule :small_collections])]
        (if (= index small-collections-index)
          ;; the index is the small-collections index and rebalancing always uses that index
          true
          ;; the index is not the small-collections index, so get the concept-id for it and see
          ;; if it is in the resharding set (non-granule indexes will never be in that set)
          (let [concept-id (index-name->concept-id index)]
            (if (some #{concept-id} rebalancing-collections)
              true
              false)))))))

(defn- is-resharding?
  "Evaluates to true if the index is being resharded or is the target of resharding"
  [index-set index]
  (contains? (get-resharding-indexes index-set) index))

(defn- is-resharding-blocking-rebalancing?
  "Returns true if a reshard operation is running that prevents the given collection and target
   from being rebalanced."
  [index-set concept-id]
  ;; get the index names for the concept-id and small_collections
  ;; then check to see if either index is being resharded
  (let [separate-index (get-in index-set [:index-set :concepts :granule (keyword concept-id)])
        small-collections-index (get-in index-set [:index-set :concepts :granule :small_collections])]
    (or (is-resharding? index-set separate-index) (is-resharding? index-set small-collections-index))))

(defn- add-resharding-index
  "Adds a new resharding index to the set of resharding indexes"
  [resharding-indexes index]
  (if resharding-indexes
    (if (contains? (set resharding-indexes) index)
      (errors/throw-service-error
       :bad-request
       (format
        "The index set already contains resharding index [%s]"
        index))
      (conj resharding-indexes index))
    #{index}))

(defn- remove-resharding-index
  "Removes index from the set of resharding indexes"
  [resharding-indexes index]
  (if resharding-indexes
    (let [resharding-index-set (set resharding-indexes)]
      (if (contains? resharding-index-set index)
        (seq (disj resharding-index-set index))
        (errors/throw-service-error
         :bad-request
         (format
          "The index set does not contain resharding index [%s]"
          index))))
    #{}))

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

(defn- get-index-config
  "Get the configuration for the given index from the index-set"
  [index-set concept-type canonical-index-name]
  (let [indexes (get-in index-set [:index-set concept-type :indexes])]
    (some (fn [index-config]
            (when (= (:name index-config) canonical-index-name)
              index-config))
          (seq indexes))))

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
  (let [index-set (index-set-util/get-index-set context index-set-id)
        ;; Don't allow rebalancing a collection while resharding a related index.
        _ (when (is-resharding-blocking-rebalancing? index-set concept-id)
            (errors/throw-service-error
             :bad-request
             (format "Cannot rebalance [%s] while its related indexes are being resharded."
                     concept-id)))
        index-set (-> index-set
                      (update-in
                       [:index-set :granule :rebalancing-collections]
                       add-rebalancing-collection concept-id)
                      (update-in
                       [:index-set :granule :rebalancing-targets]
                       assoc concept-id target)
                      (update-in
                       [:index-set :granule :rebalancing-status]
                       assoc concept-id "IN_PROGRESS")
                      ((fn [index-set]
                         (if (= "small-collections" target)
                           (do
                             (validate-granule-index-exists index-set concept-id)
                             index-set)
                           (add-new-granule-index index-set concept-id)))))]
    ;; Update the index set. This will create the new collection indexes as needed.
    (update-index-set context index-set)))

(defn finalize-collection-rebalancing
  "Removes the collection from the list of rebalancing collections"
  [context index-set-id concept-id]
  (let [index-set (index-set-util/get-index-set context index-set-id)
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
  (let [index-set (index-set-util/get-index-set context index-set-id)]
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

(defn get-resharded-index-name
  "Creates a name for a new index using the old name and the new shard count. Strips off the old
   shard count if present."
  [index shard-count]
  (-> (string/replace index #"_\d+_shards$" "") ;; strip off trailing shard count
      ;; append shard count
      (str "_" shard-count "_shards")))

(defn get-concept-type-for-index
  "Given an index name return the matching concept type by looking the index up in the index-set"
  [index-set index]
  (let [index-key (keyword (get-canonical-key-name index))]
    (some (fn [[concept-type indexes]]
            (when (some #(= index-key %) (keys indexes))
              concept-type))
          (get-in index-set [:index-set :concepts]))))

(defn start-index-resharding
  "Reshards an index to have the given number of shards"
  [context index-set-id index params]
  (when (#{"acls" "groups"} index)
    (errors/throw-service-error
     :bad-request
     "Resharding is not allowed for acls or groups."))
  (let [num-shards (parse-long (:num_shards params))
        canonical-index-name (string/replace-first index #"^\d+_" "")
        target-index (get-resharded-index-name index num-shards)
        _ (info (format "Starting to reshard index [%s] to [%s]" index target-index))
        target-index-no-index-set-id (string/replace-first target-index #".*?_" "")
        index-set (index-set-util/get-index-set context index-set-id)
        ;; Don't allow conflicts with rebalancing
        _ (when (is-rebalancing? index-set index)
            (errors/throw-service-error
             :bad-request
             (format "%s cannot be resharded as it is being used for rebalancing." index)))
        _ (when (is-rebalancing? index-set target-index)
            (errors/throw-service-error
             :bad-request
             (format "%s cannot be resharded as its target %s is being used for rebalancing."
                     index target-index)))
        ;; search for index name in index-set :concepts to get concept type and to validate the
        ;; index exists
        concept-type (get-concept-type-for-index index-set index)
        _ (when-not concept-type (errors/throw-service-error
                                  :not-found
                                  (format "Index [%s] does not exist." index)))
        ;; get the index configuration from the index-set under :<concept-type> :indexes then
        ;; change the shard count and index name to create a new configuration
        new-index-config (-> (get-index-config index-set concept-type canonical-index-name)
                             (assoc-in [:settings :index :number_of_shards] num-shards)
                             (assoc :name target-index-no-index-set-id))
        ;; update the index-set to have the new index config and to mark the original index
        ;; as resharding
        new-index-set (-> index-set
                          (update-in
                           [:index-set concept-type :indexes] conj new-index-config)
                          (update-in
                           [:index-set concept-type :resharding-indexes]
                           add-resharding-index index)
                          (update-in
                           [:index-set concept-type :resharding-targets]
                           assoc (keyword index) target-index)
                          (update-in
                           [:index-set concept-type :resharding-status]
                           assoc (keyword index) "IN_PROGRESS"))]
    ;; this will create the new index with the new shard count
    (update-index-set context new-index-set)))

(defn get-reshard-status
  "Get the resharding status for the given index"
  [context index-set-id index]
  (let [index-set (index-set-util/get-index-set context index-set-id)
        concept-type (get-concept-type-for-index index-set index)]
    (when-not concept-type
      (errors/throw-service-error
       :not-found
       (format
        "The index [%s] does not exist." index)))
    (if-let [target (get-in index-set [:index-set concept-type :resharding-targets (keyword index)])]
      (if-let [status (get-in index-set [:index-set concept-type :resharding-status (keyword index)])]
        {:original-index index
         :reshard-index target
         :reshard-status status}
        (errors/throw-service-error
         :internal-error
         (format
          "The status of resharding index [%s] is not found." index)))
      (errors/throw-service-error
       :not-found
       (format
        "The index [%s] is not being resharded." index)))))

(defn update-resharding-status
  "Update the resharding status for the given index"
  [context index-set-id index status]
  ;; resharding has the same valid statuses as rebalancing
  (rebalancing-collections/validate-status status)
  (let [index-set (index-set-util/get-index-set context index-set-id)
        concept-type (get-concept-type-for-index index-set index)]
    (when-not (get-in index-set [:index-set concept-type :resharding-status (keyword index)])
      (errors/throw-service-error
       :bad-request
       (format
        "The index set does not contain the resharding index [%s]." index)))
    (update-index-set
     context
     (update-in
      index-set
      [:index-set concept-type :resharding-status]
      assoc (keyword index) status))))

(defn- validate-resharding-complete
  "Validate that resharding has completed successfully for the given index "
  [context index-set-id index]
  (let [status (get-reshard-status context index-set-id index)]
    (when-not (= (:reshard-status status) "COMPLETE")
      (errors/throw-service-error
       :bad-request
       (format "Index [%s] has not completed resharding" index)))))

(defn finalize-index-resharding
  "Complete the resharding of the given index"
  [context index-set-id index]
  (validate-resharding-complete context index-set-id index)
  (let [index-set (index-set-util/get-index-set context index-set-id)
        ;; search for index name in index-set :concepts to get concept type
        concept-type (get-concept-type-for-index index-set index)
        _ (when-not concept-type
            (errors/throw-service-error
             :not-found
             (format "Index [%s] does not exist." index)))
        target (get-in index-set [:index-set concept-type :resharding-targets (keyword index)])
        canonical-index-name (string/replace-first index #"^\d+_" "")
        es-store (indexer-util/context->es-store context)
        new-index-set (-> index-set
                          ;; delete the old index config from the index-set
                          (update-in [:index-set concept-type :indexes]
                                     #(filter (fn [config]
                                                (not (= canonical-index-name (:name config))))
                                              %))
                          (update-in [:index-set concept-type :resharding-indexes] remove-resharding-index index)
                          (update-in [:index-set concept-type :resharding-targets]
                                     dissoc (keyword index))
                          (update-in [:index-set concept-type :resharding-status]
                                     dissoc (keyword index)))]
    (try
            ;; move alias
      (es-util/move-index-alias (indexer-util/context->conn context)
                                index
                                target
                                (esi-helper/index-alias (string/replace index #"_\d+_shards$" "")))
            ;; delete old index
      (es/delete-index es-store index)
            ;; persist index-set changes
      (update-index-set context new-index-set)
      (catch Exception e
        (error e (format "Failed to finalize resharding for [%s] -> [%s]" index target))
        (errors/throw-service-error :internal-error
                                    (format "Failed to finalize resharding for [%s]; see server logs." index))))))

(defn reset
  "Put elastic in a clean state after deleting indices associated with index-
  sets and index-set docs."
  [context]
  (let [{:keys [index-name]} config/idx-cfg-for-index-sets
        index-set-ids (es/get-index-set-ids
                       (indexer-util/context->es-store context)
                       index-name
                       "_doc")]
    ;; delete indices assoc with index-set
    (doseq [id index-set-ids]
      (delete-index-set context (str id)))))
