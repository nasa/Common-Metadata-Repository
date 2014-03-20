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
            [cmr.system-trace.core :refer [deftracefn]]))

(def concept-type->index-info
  {:collection {:index-name "collections"
                :type-name "collection"
                :fields ["entry-title"
                         "provider-id"
                         "short-name"
                         "version-id"]}})

(defn- elastic-results->query-results
  "Converts the Elasticsearch results into the results expected from execute-query"
  [concept-type elastic-results]
  ;; TODO we'll eventually switch on concept type. The fields in elastic will be different for granules and collections.
  (let [hits (get-in elastic-results [:hits :total])
        elastic-matches (get-in elastic-results [:hits :hits])
        refs (map (fn [match]
                    (let [{concept-id :_id
                           revision-id :_version
                           {entry-title :entry-title
                            provider-id :provider-id} :fields} match]
                      (results/map->Reference
                        {:concept-id concept-id
                         :revision-id revision-id
                         :provider-id provider-id
                         :entry-title entry-title})))
                  elastic-matches)]
    (results/map->Results {:hits hits :references refs})))


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
    (elastic-results->query-results concept-type results )))

(defn create-elastic-search-index
  "Creates a new instance of the elastic search index."
  [host port]
  (->ElasticSearchIndex host port))
