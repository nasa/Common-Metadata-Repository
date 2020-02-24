(ns cmr.elastic-utils.es-helper
  "Defines helper functions for invoking ES"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojurewerkz.elastisch.rest :as rest]
   [clojurewerkz.elastisch.rest.document :as doc]
   [clojurewerkz.elastisch.rest.utils :refer [join-names]]))

(defn search
  "Performs a search query across one or more indexes and one or more mapping types"
  [conn index mapping-type opts]
  (let [qk [:search_type :scroll :routing :preference :ignore_unavailable]
        qp (select-keys opts qk)
        body (apply dissoc (concat [opts] qk))]
    (rest/post conn (rest/search-url conn (join-names index))
               {:body body
                :query-params qp})))

(defn count-query
  "Performs a count query over one or more indexes and types"
  [conn index mapping-type query]
  (doc/count conn index mapping-type query))

(defn scroll
  "Performs a scroll query, fetching the next page of results from a query given a scroll id"
  [conn scroll-id opts]
  (doc/scroll conn scroll-id opts))
