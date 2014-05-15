(ns cmr.search.data.elastic-search-index
  "Implements the search index protocols for searching against Elasticsearch."
  (:require [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.rest.response :as esrsp]
            [clojure.string :as s]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.lifecycle :as lifecycle]
            [cmr.common.cache :as cache]
            [cmr.transmit.index-set :as index-set]
            [cmr.search.models.results :as results]
            [cmr.search.data.query-to-elastic :as q2e]
            [cmr.search.services.parameters :as p]
            [cmr.search.services.collection-concept-id-extractor :as ex]
            [cmr.search.data.elastic-results-to-query-results :as rc]
            [cmr.system-trace.core :refer [deftracefn]]
            [cmr.common.services.errors :as e]))

;; id of the index-set that CMR is using, hard code for now
(def index-set-id 1)

(defn- fetch-concept-type-index-names
  "Fetch index names for each concept type from index-set app"
  []
  (let [fetched-index-set (index-set/get-index-set index-set-id)]
    (get-in fetched-index-set [:index-set :concepts])))

(defn- get-granule-index-names
  "Fetch index names associated with concepts."
  [context]
  (let [cache-atom (-> context :system :cache)
        index-names (cache/cache-lookup cache-atom :concept-indices #(fetch-concept-type-index-names))]
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

(defn- get-granule-indexes
  "Returns the granule indexes that should be searched based on the input query"
  [context query]
  (let [coll-concept-ids (ex/extract-collection-concept-ids query context)]
    (if (empty? coll-concept-ids)
      "_all"
      (s/join "," (collection-concept-ids->index-names context coll-concept-ids)))))

(defn concept-type->index-info
  "Returns index info based on input concept type. For granule concept type, it will walks through
  the query and figures out only the relevant granule index names and return those."
  [context concept-type query]
  (if (= :collection concept-type)
    {:index-name  "1_collections"
     :type-name "collection"
     :fields ["entry-title"
              "provider-id"
              "short-name"
              "version-id"]}
    {:index-name (get-granule-indexes context query)
     :type-name "granule"
     :fields ["granule-ur"
              "provider-id"]}))

(defrecord ElasticSearchIndex
  [
   host
   port
   ;; The connection to elastic
   conn
   ]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    (let [{:keys [host port]} this]
      (assoc this :conn (esr/connect (str "http://" host ":" port)))))

  (stop [this system]
        this))

(defn context->conn
  [context]
  (get-in context [:system :search-index :conn]))

(deftracefn send-query-to-elastic
  "Created to trace only the sending of the query off to elastic search."
  [context query page-size page-num]
  (let [concept-type (:concept-type query)
        elastic-query (q2e/query->elastic query)
        sort-params (q2e/query->sort-params query)
        {:keys [index-name type-name fields]} (concept-type->index-info context concept-type query)
        conn (context->conn context)]
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
  (let [page-size (:page-size query)
        page-num (:page-num query)
        e-results (send-query-to-elastic context query page-size page-num)
        results (rc/elastic-results->query-results (:concept-type query) e-results)]
    (when (and (= :unlimited page-size) (> (:hits results) (count (:references results)))
               (e/internal-error! "Failed to retrieve all hits.")))
    results))

(defn create-elastic-search-index
  "Creates a new instance of the elastic search index."
  [host port]
  (->ElasticSearchIndex host port nil))
