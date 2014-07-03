(ns cmr.search.data.elastic-search-index
  "Implements the search index protocols for searching against Elasticsearch."
  (:require [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.rest.response :as esrsp]
            [clojure.string :as s]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.lifecycle :as lifecycle]
            [cmr.common.cache :as cache]
            [cmr.elastic-utils.connect :as es]
            [cmr.transmit.index-set :as index-set]
            [cmr.search.models.results :as results]
            [cmr.search.data.query-to-elastic :as q2e]
            [cmr.search.services.collection-concept-id-extractor :as cex]
            [cmr.search.services.provider-id-extractor :as pex]
            [cmr.search.data.elastic-results-to-query-results :as rc]
            [cmr.system-trace.core :refer [deftracefn]]
            [cmr.common.services.errors :as e]))

;; id of the index-set that CMR is using, hard code for now
(def index-set-id 1)

(defn- fetch-concept-type-index-names
  "Fetch index names for each concept type from index-set app"
  [context]
  (let [fetched-index-set (index-set/get-index-set context index-set-id)]
    (get-in fetched-index-set [:index-set :concepts])))

(defn- get-granule-index-names
  "Fetch index names associated with concepts."
  [context]
  (let [cache-atom (-> context :system :cache)
        index-names (cache/cache-lookup cache-atom :concept-indices
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
      (format "%d_c*,%d_small_collections,-%d_collections" index-set-id index-set-id index-set-id))))



(defn concept-type->index-info
  "Returns index info based on input concept type. For granule concept type, it will walks through
  the query and figures out only the relevant granule index names and return those."
  [context concept-type query]
  (if (= :collection concept-type)
    {:index-name  "1_collections"
     :type-name "collection"}
    {:index-name (get-granule-indexes context query)
     :type-name "granule"}))

(def concept-type->result-format->fields
  {:collection {:json ["entry-title"
                       "provider-id"
                       "short-name"
                       "version-id"]
                :xml ["entry-title"
                      "provider-id"
                      "short-name"
                      "version-id"]
                :echo10 []}
   :granule {:json ["granule-ur"
                    "provider-id"]
             :xml ["granule-ur"
                   "provider-id"]
             :csv ["granule-ur"
                   "producer-gran-id"
                   "start-date"
                   "end-date"
                   "downloadable-urls"
                   "cloud-cover"
                   "day-night"
                   "size"]
             :echo10 ["collection-concept-id"]}})

(defrecord ElasticSearchIndex
  [
   config

   ;; The connection to elastic
   conn
   ]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    (assoc this :conn (es/try-connect (:config this))))

  (stop [this system]
        this))

(defn context->conn
  [context]
  (get-in context [:system :search-index :conn]))

(deftracefn send-query-to-elastic
  "Created to trace only the sending of the query off to elastic search."
  [context query]
  (let [{:keys [page-size page-num concept-type result-format]} query
        elastic-query (q2e/query->elastic query)
        sort-params (q2e/query->sort-params query)
        index-info (concept-type->index-info context concept-type query)
        {:keys [index-name type-name]} index-info
        fields (get-in concept-type->result-format->fields [concept-type result-format])
        conn (context->conn context)]
    (debug "Executing against indexes [" index-name "] the elastic query:" (pr-str elastic-query))
    (if (= :unlimited page-size)
      (esd/search conn
                  index-name
                  [type-name]
                  :query elastic-query
                  :version true
                  :fields fields
                  :sort sort-params
                  :size 10000) ;10,000 == "unlimited"
      (esd/search conn
                  index-name
                  [type-name]
                  :query elastic-query
                  :version true
                  :sort sort-params
                  :size page-size
                  :from (* (dec page-num) page-size)
                  :fields fields))))

(defn execute-query
  "Executes a query to find concepts. Returns concept id, native id, and revision id."
  [context query]
  (let [{:keys [page-size concept-type result-format]} query
        e-results (send-query-to-elastic context query)
        results (rc/elastic-results->query-results context concept-type e-results result-format)]
    (debug "Elastic query took" (:took e-results) "ms")
    (when (and (= :unlimited page-size) (> (:hits results) (count (:references results)))
               (e/internal-error! "Failed to retrieve all hits.")))
    results))

(defn create-elastic-search-index
  "Creates a new instance of the elastic search index."
  [config]
  (->ElasticSearchIndex config nil))
