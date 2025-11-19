(ns cmr.indexer.services.index-set-service
  "Provide functions to store, retrieve, delete index-sets"
  (:require
   [cheshire.core :as json]
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common.config :as common-config]
   [cmr.common.log :as log :refer [error info warn]]
   [cmr.common.rebalancing-collections :as rebalancing-collections]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.elastic-utils.es-helper :as es-helper]
   [cmr.elastic-utils.es-index-helper :as esi-helper]
   [cmr.elastic-utils.index-util :as es-util]
   [cmr.elastic-utils.config :as es-config]
   [cmr.indexer.common.index-set-util :as index-set-util]
   [cmr.indexer.config :as config]
   [cmr.indexer.data.index-set :as index-set]
   [cmr.indexer.data.index-set-elasticsearch :as es]
   [cmr.indexer.services.messages :as m]
   [cmr.indexer.indexer-util :as indexer-util])
  (:import
   (clojure.lang ExceptionInfo)))


(defn add-searchable-generic-types
  "Add the list of supported generic document types to a list of fixed searchable
   concept types presumable from searchable-concept-types.
   Given a specific elastic cluster, we will or will not filter out granules from the searchable generic types."
  [initial-list es-cluster-name]
  (let [approved-pipeline-documents (common-config/approved-pipeline-documents)
        filtered-pipeline-documents (cond
                                      (= es-config/gran-elastic-name es-cluster-name)
                                      (into {} (for [[k v] approved-pipeline-documents
                                            :when (.startsWith (name k) "granule")]
                                        [k v]))

                                      (= es-config/elastic-name es-cluster-name)
                                      (into {} (for [[k v] approved-pipeline-documents
                                            :when (not (.startsWith (name k) "granule"))]
                                        [k v]))

                                      :else (throw (Exception. (es-config/invalid-elastic-cluster-name-msg es-cluster-name))))]
    (reduce (fn [data, item] (conj data (keyword (str "generic-" (name item)))))
            initial-list
            (keys filtered-pipeline-documents))))

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
  "Given an index-set, build list of indices that includes the given indices and the required concepts in our config."
  [idx-set es-cluster-name]
  (let [prefix-id (get-in idx-set [:index-set :id])]
    (for [concept-type (cond
                         (= es-cluster-name es-config/gran-elastic-name)
                         (add-searchable-generic-types searchable-gran-concept-types es-cluster-name)

                         (= es-cluster-name es-config/elastic-name)
                         (add-searchable-generic-types searchable-non-gran-concept-types es-cluster-name)

                         :else (throw (Exception. (es-config/invalid-elastic-cluster-name-msg es-cluster-name))))
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
                         (= es-cluster-name es-config/gran-elastic-name)
                         (add-searchable-generic-types searchable-gran-concept-types es-cluster-name)

                         (= es-cluster-name es-config/elastic-name)
                         (add-searchable-generic-types searchable-non-gran-concept-types es-cluster-name)

                         :else (throw (Exception. (es-config/invalid-elastic-cluster-name-msg es-cluster-name))))
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
                                           (= es-cluster-name es-config/gran-elastic-name)
                                           ;; we will not add generics to the searchable gran concept types
                                           (add-searchable-generic-types searchable-gran-concept-types es-cluster-name)

                                           (= es-cluster-name es-config/elastic-name)
                                           (add-searchable-generic-types searchable-non-gran-concept-types es-cluster-name)

                                           :else (throw (Exception. (es-config/invalid-elastic-cluster-name-msg es-cluster-name))))
        ]
    {:id (:id index-set)
     :name (:name index-set)
     :concepts (into {} (for [concept-type generic-searchable-concept-types]
                          [concept-type
                           (into {} (for [idx (get-in index-set [concept-type :indexes])]
                                      [(keyword (index-set/get-canonical-key-name (:name idx))) (gen-valid-index-name prefix (:name idx))]))]))}))

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

(defn index-requested-index-set
  "Index requested index-set along with generated elastic index names"
  [context index-set es-cluster-name]
  (let [indexes-w-added-concepts (prune-index-set (:index-set index-set) es-cluster-name)
        index-set-w-es-index-names (assoc-in index-set [:index-set :concepts]
                                             (:concepts indexes-w-added-concepts))
        encoded-index-set-w-es-index-names (-> index-set-w-es-index-names
                                               json/generate-string
                                               util/string->gzip-base64)
        es-doc {:index-set-id (get-in index-set [:index-set :id])
                :index-set-name (get-in index-set [:index-set :name])
                :index-set-request encoded-index-set-w-es-index-names}
        doc-id (str (:index-set-id es-doc))
        {:keys [index-name mapping]} (config/idx-cfg-for-index-sets es-cluster-name)
        idx-mapping-type (first (keys mapping))]
    (es/save-document-in-elastic context index-name idx-mapping-type doc-id es-doc es-cluster-name)))

(defn split-index-set-by-cluster
  "Given an index set with combined indexes from all clusters, we will split it based on cluster.
  combined-index-set is a map structured like below:
  {:index-set {:name cmr-index-set :id 1 :granule {} :collection {} :concepts {}...}}

  and will return a map like the one below:
  {
  :gran-elastic {:index-set {:name cmr-index-set :id 1 :collection {} :concepts {}...}}
  :elastic {:index-set {:name cmr-index-set :id 1 :granule {} :concepts {}}}
  }
  "
  [combined-index-set]
  (let [;; setup keys we need to extract from the combined index set
        combined-concepts-map (get-in combined-index-set [:index-set :concepts])
        inner-combined-index-set (:index-set combined-index-set)
        gran-index-keys (keys (:index-set (index-set/gran-index-set nil)))

        ;; re-build the outer map for gran index set
        gran-outer-map-index-set (select-keys inner-combined-index-set gran-index-keys)
        ;; rebuild the inner concepts map for the gran index set
        gran-concepts-map-index-set (select-keys combined-concepts-map gran-index-keys)
        ;; combine
        gran-index-set (assoc gran-outer-map-index-set :concepts gran-concepts-map-index-set)

        ;; all other indices given in the index set will go the non gran index set. This relies heavily on the gran index set template.
        non-gran-index-keys-to-extract (set/difference (set (keys inner-combined-index-set)) (set gran-index-keys))
        non-gran-outer-map-index-set (select-keys inner-combined-index-set non-gran-index-keys-to-extract)
        non-gran-index-set-without-concepts (-> non-gran-outer-map-index-set
                                         (dissoc :concepts)
                                         (assoc :name (:name inner-combined-index-set)
                                                :id (:id inner-combined-index-set)
                                                :create-reason (:create-reason inner-combined-index-set)))
        non-gran-concepts-map-index-set (select-keys combined-concepts-map (set/difference (set (keys combined-concepts-map)) (set gran-index-keys)))
        non-gran-index-set (assoc non-gran-index-set-without-concepts :concepts non-gran-concepts-map-index-set)]

    {(keyword es-config/gran-elastic-name) {:index-set gran-index-set}
     (keyword es-config/elastic-name) {:index-set non-gran-index-set}}))

(defn create-index-set
  "Create indices listed in index-set for specific elastic cluster. Rollback occurs if indices creation or
  index-set doc indexing fails.
  We combine the work of both index sets here because if one fails to one cluster, we want to rollback all the indices to all clusters together.

  We expect the split-index-map to have the following structure:
  {
  :gran-elastic {:index-set {:name cmr-index-set :id 1 :collection {} :concepts {}...}}
  :elastic {:index-set {:name cmr-index-set :id 1 :granule {} :concepts {}}}
  }
  "
  [context split-index-set-map]
  (let [;; setup gran index set configs
        gran-index-set ((keyword es-config/gran-elastic-name) split-index-set-map)
        gran-index-names (get-index-names gran-index-set es-config/gran-elastic-name)
        gran-indices-w-config (build-indices-list-w-config gran-index-set es-config/gran-elastic-name)
        gran-es-store (indexer-util/context->es-store context es-config/gran-elastic-name)

        ;; setup non-gran index set configs
        non-gran-index-set ((keyword es-config/elastic-name) split-index-set-map)
        non-gran-index-names (get-index-names non-gran-index-set es-config/elastic-name)
        non-gran-indices-w-config (build-indices-list-w-config non-gran-index-set es-config/elastic-name)
        non-gran-es-store (indexer-util/context->es-store context es-config/elastic-name)]

    (when-let [generic-docs (keys (common-config/approved-pipeline-documents))]
      (info "This instance of CMR will publish Elasticsearch indices for the following generic document types:" generic-docs))

    ;; create indices and rollback if index creation fails
    (try
      (dorun (map #(es/create-index-and-alias gran-es-store %) gran-indices-w-config))
      (dorun (map #(es/create-index-and-alias non-gran-es-store %) non-gran-indices-w-config))
      (catch ExceptionInfo e
        ;; TODO: Generic work: why does this fail to roll back with bad generics?
        (println "failed to create index, roll back, this does not always work")
        (dorun (map #(es/delete-index gran-es-store %) gran-index-names))
        (dorun (map #(es/delete-index non-gran-es-store %) non-gran-index-names))
        (m/handle-elastic-exception "attempt to create indices of index-set failed" e)))

    ;; create index-sets and rollback if index-set creation fails
    (try
      (index-requested-index-set context gran-index-set es-config/gran-elastic-name)
      (index-requested-index-set context non-gran-index-set es-config/elastic-name)
      (catch ExceptionInfo e
        (warn "failed to create index sets, roll back, this does not always work")
        (dorun (map #(es/delete-index gran-es-store %) gran-index-names))
        (dorun (map #(es/delete-index non-gran-es-store %) non-gran-index-names))
        (m/handle-elastic-exception "attempt to index index-set doc failed" e)))))

(defn update-index-set
  "Updates indices in the index set"
  [context es-cluster-name index-set]
  (let [indices-w-config (build-indices-list-w-config index-set es-cluster-name)
        es-store (indexer-util/context->es-store context es-cluster-name)]

    (doseq [idx indices-w-config]
      (es/update-index es-store idx))

    (index-requested-index-set context index-set es-cluster-name)))

(defn put-index-set
  "Upsert the given index-set to the index-sets index in ES."
  [context index-set]
  (let [split-index-set-map (split-index-set-by-cluster index-set)]
    ;; Validation for both index sets need to happen before we update anything
    (validate-requested-index-set context es-config/gran-elastic-name index-set true)
    (validate-requested-index-set context es-config/elastic-name index-set true)
    ;; upsert indexes and index set based on the split index set
    (update-index-set context es-config/gran-elastic-name ((keyword es-config/gran-elastic-name) split-index-set-map))
    (update-index-set context es-config/elastic-name ((keyword es-config/elastic-name) split-index-set-map))))

(defn delete-index-set
  "Delete all indices having 'id_' as the prefix the given elastic cluster, followed by
  index-set doc delete"
  [context index-set-id es-cluster-name]
  (let [index-names (get-index-names (index-set-util/get-index-set context es-cluster-name index-set-id) es-cluster-name)
        {:keys [index-name mapping]} (config/idx-cfg-for-index-sets es-cluster-name)
        idx-mapping-type (first (keys mapping))]
    (dorun (map #(es/delete-index (indexer-util/context->es-store context es-cluster-name) %) index-names))
    (es/delete-document context index-name idx-mapping-type index-set-id es-cluster-name)))

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
  "Extracts a concept ID from a granule index name. Works for both underscore- and dash-delimited names.
   Examples:
   '1_c2317035855_nsidc_ecs' -> 'C2317035855-NSIDC_ECS'
   '1_c2317035855_nsidc_ecs_5_shards' -> 'C2317035855-NSIDC_ECS'
   'C2317035855-NSIDC_ECS' -> 'C2317035855-NSIDC_ECS'"
  [index-name]
  (when index-name
    (let [;; Remove shard count suffix if present
          without-shards (string/replace index-name #"_\d+_shards$" "")
          ;; Remove leading number prefix (e.g., "1_")
          without-prefix (string/replace without-shards #"^\d+_" "")]
      (if (re-matches #"(?i)c\d+-[a-z0-9_]+" without-prefix)
        ;; Already in canonical C#######-PROVIDER format
        (string/upper-case without-prefix)
        ;; Otherwise parse underscore format
        (let [first-underscore-idx (string/index-of without-prefix "_")]
          (when first-underscore-idx
            (let [concept-part (subs without-prefix 0 first-underscore-idx)
                  provider-part (subs without-prefix (inc first-underscore-idx))]
              (str (string/upper-case concept-part)
                   "-"
                   (string/upper-case provider-part)))))))))

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

(defn- collection-ids-from-granule-indexes
  "Extracts the collection concept IDs corresponding to individual granule indexes in the index set."
  [index-set]
  (->> (get-in index-set [:index-set :granule :indexes])
       (map :name)
       (map index-name->concept-id)
       set))

(defn- validate-granule-index-does-not-exist
  "Validates that a granule index does not already exist in the index set for the given collection
  concept ID."
  [index-set collection-concept-id]
  (let [existing-collections (collection-ids-from-granule-indexes index-set)]
    (when (contains? existing-collections collection-concept-id)
      (errors/throw-service-error
       :bad-request
       (format
        "The collection [%s] already has a separate granule index."
        collection-concept-id)))))

(defn- validate-granule-index-exists
  "Validates that a granule index exists in the index set for the given collection concept ID."
  [index-set collection-concept-id]
  (let [existing-collections (collection-ids-from-granule-indexes index-set)]
    (when-not (contains? existing-collections collection-concept-id)
      (errors/throw-service-error
       :bad-request
       (format
        "The collection [%s] does not have a separate granule index."
        collection-concept-id)))))

(defn- add-new-granule-index-to-index-set
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
  "Returns the index configuration from the index-set that matches the canonical index name."
  [index-set concept-type canonical-index-name]
  (let [canonical (index-set/get-canonical-key-name canonical-index-name)]
    (some #(when (= (index-set/get-canonical-key-name (:name %)) canonical) %)
          (get-in index-set [:index-set concept-type :indexes]))))

(defn- remove-granule-index-from-index-set
  "Removes the separate granule index for the given collection from the index set. Validates the
  collection index is listed in the index-set."
  [index-set collection-concept-id]
  (validate-granule-index-exists index-set collection-concept-id)
  (update-in index-set [:index-set :granule :indexes]
             (fn [indexes]
               (remove #(= collection-concept-id (index-name->concept-id (:name %)))
                       indexes))))

(defn mark-collection-as-rebalancing
  "Marks the given collection as rebalancing in the index set."
  [context index-set-id concept-id target]
  (info (format "Starting to rebalance granules for collection [%s] to target [%s]."
                concept-id target))
  (rebalancing-collections/validate-target target concept-id)
  (let [gran-index-set (index-set-util/get-index-set context es-config/gran-elastic-name index-set-id)
        ;; Don't allow rebalancing a collection while resharding a related index.
        _ (when (is-resharding-blocking-rebalancing? gran-index-set concept-id)
            (errors/throw-service-error
             :bad-request
             (format "Cannot rebalance [%s] while its related indexes are being resharded."
                     concept-id)))
        gran-index-set (-> gran-index-set
                      (update-in
                       [:index-set :granule :rebalancing-collections]
                       add-rebalancing-collection concept-id)
                      (update-in
                       [:index-set :granule :rebalancing-targets]
                       assoc concept-id target)
                      (update-in
                       [:index-set :granule :rebalancing-status]
                       assoc concept-id "IN_PROGRESS")
                      ((fn [gran-index-set]
                         (if (= "small-collections" target)
                           (do
                             (validate-granule-index-exists gran-index-set concept-id)
                             gran-index-set)
                           (add-new-granule-index-to-index-set gran-index-set concept-id)))))]
    ;; Update the index set. This will create the new collection indexes as needed.
    (validate-requested-index-set context es-config/gran-elastic-name gran-index-set true)
    (update-index-set context es-config/gran-elastic-name gran-index-set)))

(defn finalize-collection-rebalancing
  "Removes the collection from the list of rebalancing collections"
  [context index-set-id concept-id]
  (let [gran-index-set (index-set-util/get-index-set context es-config/gran-elastic-name index-set-id)
        target (get-in gran-index-set [:index-set :granule :rebalancing-targets (keyword concept-id)])
        _ (info (format "Finalizing rebalancing granules for collection [%s] to target [%s]."
                        concept-id target))
        _ (rebalancing-collections/validate-target target concept-id)
        gran-index-set (as-> gran-index-set index-set
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
    (validate-requested-index-set context es-config/gran-elastic-name gran-index-set true)
    (update-index-set context es-config/gran-elastic-name (util/remove-nils-empty-maps-seqs gran-index-set))))

(defn update-collection-rebalancing-status
  "Update the collection rebalancing status."
  [context index-set-id concept-id status]
  (rebalancing-collections/validate-status status)
  (info (format "Updating collection rebalancing status for collection [%s] to status [%s]."
                concept-id status))
  (let [index-set (index-set-util/get-index-set context es-config/gran-elastic-name index-set-id)]
    (when-not (get-in index-set [:index-set :granule :rebalancing-status (keyword concept-id)])
      (errors/throw-service-error
       :bad-request
       (format
        "The index set does not contain the rebalancing collection [%s]"
        concept-id)))
    (validate-requested-index-set context es-config/gran-elastic-name index-set true)
    (update-index-set
     context
     es-config/gran-elastic-name
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
  (let [index-key (keyword (index-set/get-canonical-key-name index))]
    (some (fn [[concept-type indexes]]
            (when (some #(= index-key %) (keys indexes))
              concept-type))
          (get-in index-set [:index-set :concepts]))))

;; TODO there is a func that is already very similar to this. Find it and use that one instead.
(defn- verify-elastic-name-not-blank
  [elastic-name]
  (when (string/blank? elastic-name)
    (errors/throw-service-error :bad-request "Empty elastic name is not allowed")))

(defn start-index-resharding
  "Reshards an index to have the given number of shards"
  [context index-set-id index params]
  (when (#{"acls" "groups"} index)
    (errors/throw-service-error
     :bad-request
     "Resharding is not allowed for acls or groups."))
  (let [elastic-name (:elastic_name params)
        _ (verify-elastic-name-not-blank elastic-name)
        num-shards (parse-long (:num_shards params))
        canonical-index-name (string/replace-first index #"^\d+_" "")
        target-index (get-resharded-index-name index num-shards)
        _ (info (format "Starting to reshard index [%s] to [%s]" index target-index))
        target-index-no-index-set-id (string/replace-first target-index #".*?_" "")
        index-set (index-set-util/get-index-set context elastic-name index-set-id)
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
        ;; Find the original index configuration
        orig-index-config (get-index-config index-set concept-type canonical-index-name)

        ;; Copy and modify it for the new index
        new-index-config (-> orig-index-config
                             (assoc :name target-index-no-index-set-id)
                             (assoc-in [:settings :index :number_of_shards] num-shards))
        ;; Update index-set: add new entry, mark resharding status
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
    (update-index-set context elastic-name new-index-set)))

(defn update-resharding-status
  "Update the resharding status for the given index"
  [context index-set-id index status elastic-name]
  (info "CMR 11008 updating reshard status for index " index " to status " status)
  (when (string/blank? elastic-name)
    (errors/throw-service-error :bad-request "Empty elastic name is not allowed"))
  ;; resharding has the same valid statuses as rebalancing
  (rebalancing-collections/validate-status status)
  (let [index-set (index-set-util/get-index-set context elastic-name index-set-id)
        concept-type (get-concept-type-for-index index-set index)]
    (when-not (get-in index-set [:index-set concept-type :resharding-status (keyword index)])
      (errors/throw-service-error
       :bad-request
       (format
        "The index set does not contain the resharding index [%s]." index)))
    (update-index-set
     context
     elastic-name
     (update-in
      index-set
      [:index-set concept-type :resharding-status]
      assoc (keyword index) status))))

;; TODO
(defn get-reshard-status
  "Get the resharding status for the given index"
  [context index-set-id index params]
  (let [elastic-name (:elastic_name params)
        _ (verify-elastic-name-not-blank elastic-name)
        conn (indexer-util/context->conn context elastic-name)
        ;; check if es _reindex is still happening when we started the reshard asynchronously
        reindexing-still-in-progress (es-helper/reindexing-still-in-progress? conn index)
        ;; determine if reshard status needs to be updated based on elasticsearch's async _reindex status
        _ (info "CMR 11008 reindexing still in progress = " reindexing-still-in-progress)
        _ (when-not reindexing-still-in-progress
            (update-resharding-status context index-set-id index "COMPLETE" elastic-name))
        ;; getting the most updated index-set
        index-set (index-set-util/get-index-set context elastic-name index-set-id)
        concept-type (get-concept-type-for-index index-set index)
        _ (when-not concept-type
            (errors/throw-service-error :not-found (format "The index [%s] does not exist." index)))]

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

(defn- validate-resharding-complete
  "Validate that resharding has completed successfully for the given index "
  [context index-set-id index elastic-name]
  (let [status (get-reshard-status context index-set-id index {:elastic_name elastic-name})]
    (when-not (= (:reshard-status status) "COMPLETE")
      (errors/throw-service-error
       :bad-request
       (format "Index [%s] has not completed resharding" index)))))

(defn finalize-index-resharding
  "Complete the resharding of the given index"
  [context index-set-id index params]
  (let [elastic-name (:elastic_name params)
        _ (when (string/blank? elastic-name)
            (errors/throw-service-error :bad-request "Empty elastic name is not allowed"))
        _ (validate-resharding-complete context index-set-id index elastic-name)
        index-set (index-set-util/get-index-set context elastic-name index-set-id)
        ;; search for index name in index-set :concepts to get concept type
        concept-type (get-concept-type-for-index index-set index)
        _ (when-not concept-type
            (errors/throw-service-error
             :not-found
             (format "Index [%s] does not exist in elastic cluster [%s]." index elastic-name)))
        target (get-in index-set [:index-set concept-type :resharding-targets (keyword index)])
        es-store (indexer-util/context->es-store context elastic-name)
        prefix-id (get-in index-set [:index-set :id])
        new-index-set (-> index-set
                          ;; delete the old index config from the index-set
                          (update-in [:index-set concept-type :indexes]
                                     (fn [indexes]
                                       (remove (fn [config]
                                                 (= (gen-valid-index-name prefix-id (:name config))
                                                    index))
                                               indexes)))
                          (update-in [:index-set concept-type :resharding-indexes] remove-resharding-index index)
                          (update-in [:index-set concept-type :resharding-targets]
                                     dissoc (keyword index))
                          (update-in [:index-set concept-type :resharding-status]
                                     dissoc (keyword index))
                          util/remove-nils-empty-maps-seqs)]
    (try
      ;; move alias
      (es-util/move-index-alias (indexer-util/context->conn context elastic-name)
                                index
                                target
                                (esi-helper/index-alias (string/replace index #"_\d+_shards$" "")))
      ;; delete old index
      (es/delete-index es-store index)
      ;; persist index-set changes
      (update-index-set context elastic-name new-index-set)
      (catch Exception e
        (error e (format "Failed to finalize resharding for [%s] -> [%s]" index target))
        (errors/throw-service-error :internal-error
                                    (format "Failed to finalize resharding for [%s]; see server logs." index))))))

(defn reset
  "Put elastic in a clean state after deleting indices associated with index-sets and index-set docs."
  [context]
  (let [{:keys [index-name]} (config/idx-cfg-for-index-sets es-config/gran-elastic-name)
        gran-index-set-ids (es/get-index-set-ids
                             (indexer-util/context->es-store context es-config/gran-elastic-name)
                             index-name
                             "_doc")
        {:keys [index-name]} (config/idx-cfg-for-index-sets es-config/elastic-name)
        non-gran-index-set-ids (es/get-index-set-ids
                                 (indexer-util/context->es-store context es-config/elastic-name)
                                 index-name
                                 "_doc")]
    ;; delete indices assoc with index-set
    (doseq [id gran-index-set-ids]
      (delete-index-set context (str id) es-config/gran-elastic-name))

    (doseq [id non-gran-index-set-ids]
      (delete-index-set context (str id) es-config/elastic-name))))
