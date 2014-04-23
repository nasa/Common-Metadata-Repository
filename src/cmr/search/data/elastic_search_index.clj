(ns cmr.search.data.elastic-search-index
  "Implements the search index protocols for searching against Elasticsearch."
  (:require [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.rest.response :as esrsp]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.lifecycle :as lifecycle]
            [cmr.search.models.results :as results]
            [cmr.search.data.query-to-elastic :as q2e]

            ;; Query To Elastic implementations
            ;; Must be required here to be available in uberjar
            [cmr.search.data.temporal-to-elastic]

            [cmr.search.data.elastic-results-to-query-results :as rc]
            [cmr.system-trace.core :refer [deftracefn]]))

;; TODO - somehow search app to get this info from index-set app
;; TODO - define proper index-set/indexer/search apps workflow w.r.t elastic indices
(def concept-type->index-info
  {:collection {:index-name  "1_collections" ;; "collections"
                :type-name "collection"
                :fields ["entry-title"
                         "provider-id"
                         "short-name"
                         "version-id"]}
   :granule {:index-name "1_granules" ;; "granules"
             :type-name "small_collections"
             :fields ["granule-ur"
                      "provider-id"]}})

(defrecord ElasticSearchIndex
  [
   host
   port
   ]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    (let [{:keys [host port]} this]
      (esr/connect! (str "http://" host ":" port)))
    this)

  (stop [this system]
        this))

(deftracefn send-query-to-elastic
  "Created to trace only the sending of the query off to elastic search."
  [context elastic-query concept-type]
  (let [{:keys [index-name type-name fields]} (concept-type->index-info concept-type)]
    (esd/search index-name
                [type-name]
                :query elastic-query
                :version true
                :size 10
                :fields fields)))


(defn execute-query
  "Executes a query to find concepts. Returns concept id, native id, and revision id."
  [context query]
  (let [{:keys [concept-type]} query
        results (send-query-to-elastic context (q2e/query->elastic query) concept-type)]
    (rc/elastic-results->query-results concept-type results)))

(defn create-elastic-search-index
  "Creates a new instance of the elastic search index."
  [host port]
  (->ElasticSearchIndex host port))
