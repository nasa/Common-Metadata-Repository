(ns cmr.search.data.elastic-search-index
  "Implements the search index protocols for searching against Elasticsearch."
  (:require [clojure.string :as s]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.query :as q]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.util :as util]
            [cmr.common.lifecycle :as lifecycle]
            [cmr.common.cache :as cache]
            [cmr.transmit.index-set :as index-set]
            [cmr.common-app.services.search.query-model :as qm]
            [cmr.search.services.query-walkers.collection-concept-id-extractor :as cex]
            [cmr.search.services.query-walkers.provider-id-extractor :as pex]
            [cmr.common.services.errors :as e]
            [cmr.common.concepts :as concepts]
            [cmr.common-app.services.search.elastic-search-index :as common-esi]))

;; id of the index-set that CMR is using, hard code for now
(def index-set-id 1)

(defn- fetch-concept-type-index-names
  "Fetch index names for each concept type from index-set app"
  [context]
  (let [fetched-index-set (index-set/get-index-set context index-set-id)]
    (get-in fetched-index-set [:index-set :concepts])))

(def index-cache-name
  :index-names)

(defn- get-granule-index-names
  "Fetch index names associated with concepts."
  [context]
  (let [index-names (cache/get-value (cache/context->cache context index-cache-name)
                                     :concept-indices
                                     (partial fetch-concept-type-index-names context))]
    (get index-names :granule)))

(defn- collection-concept-id->index-name
  "Return the granule index name for the input collection concept id"
  [indexes coll-concept-id]
  (get indexes (keyword coll-concept-id) (get indexes :small_collections)))

(defn- collection-concept-ids->index-names
  "Return the granule index names for the input collection concept ids"
  [context coll-concept-ids]
  (let [indexes (get-granule-index-names context)]
    (distinct (map #(collection-concept-id->index-name indexes %) coll-concept-ids))))

(defn- provider-ids->index-names
  "Return the granule index names for the input provider-ids"
  [context provider-ids]
  (let [indexes (get-granule-index-names context)]
    (cons (get indexes :small_collections)
          (map #(format "%d_c*_%s" index-set-id (s/lower-case %))
               provider-ids))))

(def all-granule-indexes
  "Returns all possible granule indexes in a string that can be used by elasticsearch query"
  (format "%d_c*,%d_small_collections,-%d_collections" index-set-id index-set-id index-set-id))

(defn- get-granule-indexes
  "Returns the granule indexes that should be searched based on the input query"
  [context query]
  (let [coll-concept-ids (seq (cex/extract-collection-concept-ids query))
        provider-ids (seq (pex/extract-provider-ids query))]
    (cond
      coll-concept-ids
      ;; Use collection concept ids to limit the indexes queried
      (s/join "," (collection-concept-ids->index-names context coll-concept-ids))

      provider-ids
      ;; Use provider ids to limit the indexes queried
      (s/join "," (provider-ids->index-names context provider-ids))

      :else
      all-granule-indexes)))

(defmethod common-esi/concept-type->index-info :granule
  [context _ query]
  {:index-name (get-granule-indexes context query)
   :type-name "granule"})

(defmethod common-esi/concept-type->index-info :collection
  [context _ query]
  {:index-name (if (:all-revisions? query)
                 "1_all_collection_revisions"
                 "1_collections")
   :type-name "collection"})

(defmethod common-esi/concept-type->index-info :tag
  [context _ query]
  {:index-name "1_tags"
   :type-name "tag"})

(defn context->conn
  [context]
  (get-in context [:system :search-index :conn]))

(defn get-collection-permitted-groups
  "NOTE: Use for debugging only. Gets collections along with their currently permitted groups. This
  won't work if more than 10,000 collections exist in the CMR."
  [context]
  (let [index-info (common-esi/concept-type->index-info context :collection nil)
        results (esd/search (context->conn context)
                            (:index-name index-info)
                            [(:type-name index-info)]
                            :query (q/match-all)
                            :size 10000
                            :fields ["permitted-group-ids"])
        hits (get-in results [:hits :total])]
    (when (> hits (count (get-in results [:hits :hits])))
      (e/internal-error! "Failed to retrieve all hits."))
    (into {} (for [hit (get-in results [:hits :hits])]
               [(:_id hit) (get-in hit [:fields :permitted-group-ids])]))))

(defn get-collection-granule-counts
  "Returns the collection granule count by searching elasticsearch by aggregation"
  [context provider-ids]
  (let [condition (if (seq provider-ids)
                    (qm/string-conditions :provider-id provider-ids true)
                    qm/match-all)
        query (qm/query {:concept-type :granule
                         :condition condition
                         :page-size 0
                         :aggregations {:by-provider
                                        {:terms {:field :provider-id
                                                 :size 10000}
                                         :aggs {:by-collection-id
                                                {:terms {:field :collection-concept-seq-id
                                                         :size 10000}}}}}})
        results (common-esi/execute-query context query)
        extra-provider-count (get-in results [:aggregations :by-provider :sum_other_doc_count])]
    ;; It's possible that there are more providers with granules than we expected.
    ;; :sum_other_doc_count will be greater than 0 in that case.
    (when (> extra-provider-count 0)
      (e/internal-error! "There were [%s] more providers with granules than we ever expected to see."))

    (into {} (for [provider-bucket (get-in results [:aggregations :by-provider :buckets])
                   :let [extra-collection-count (get-in provider-bucket [:by-collection-id :sum_other_doc_count])]
                   coll-bucket (get-in provider-bucket [:by-collection-id :buckets])
                   :let [provider-id (:key provider-bucket)
                         coll-seq-id (:key coll-bucket)
                         num-granules (:doc_count coll-bucket)]]
               (do
                 ;; It's possible that there are more collections in the provider than we expected.
                 ;; :sum_other_doc_count will be greater than 0 in that case.
                 (when (> extra-collection-count 0)
                   (e/internal-error!
                     (format "Provider %s has more collections ([%s]) with granules than we support"
                             provider-id extra-collection-count)))

                 [(concepts/build-concept-id {:sequence-number coll-seq-id
                                              :provider-id provider-id
                                              :concept-type :collection})
                  num-granules])))))

