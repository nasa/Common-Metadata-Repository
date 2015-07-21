(ns cmr.search.services.query-execution.highlight-results-feature
  "This enables returning highlighted snippets with collection search results based on the
  provided keyword search."
  (:require [cmr.search.services.query-execution :as query-execution]
            [cmr.search.models.results :as r]
            [clojure.string :as str]))

(defn- get-keyword-conditions
  "Returns a list of the keyword text conditions from a condition"
  [condition]
  (let [sub-condition (:condition condition)
        sub-conditions (:conditions condition)
        keyword-sub-conditions (for [a-condition (if sub-condition
                                                   (conj sub-conditions sub-condition)
                                                   sub-conditions)
                                 :let [keyword-condition (get-keyword-conditions a-condition)]
                                 :when keyword-condition]
                                 keyword-condition)
        all-keyword-conditions (if (= :keyword (:field condition))
                                 (conj keyword-sub-conditions condition)
                                 keyword-sub-conditions)]
    (seq (flatten all-keyword-conditions))))

(defn- build-highlight-query
  "Creates the highlight query that will be passed into elastic"
  [query]
  (when-let [keyword-conditions (get-keyword-conditions (:condition query))]
    (let [conditions-as-string (str/join " " (map #(:query-str %) keyword-conditions))]
       {:fields {:summary {:highlight_query {:query_string {:query conditions-as-string}}}}})))

(defmethod query-execution/pre-process-query-result-feature :highlights
  [_ query _]
  (assoc query :highlights (build-highlight-query query)))

(defn- replace-fields-with-highlighted-fields
  "Replaces the appropriate fields with the highlighted snippets for each field.

  Note this algorithm requires that both the number of hits and the order of results is identical
  in the query-results and elastic-results."
  [query-results elastic-results]
  (let [all-highlights (map #(get-in % [:highlight :summary])
                            (get-in elastic-results [:hits :hits]))]
    (for [item (:items query-results)
          :let [highlight (first all-highlights)
                all-highlights (rest all-highlights)]]
      (if (seq highlight)
        (assoc item :summary (str/join ";" highlight))
        item))))

(defmethod query-execution/post-process-query-result-feature :highlights
  [context query elastic-results query-results feature]
  (assoc query-results
         :items (replace-fields-with-highlighted-fields query-results elastic-results)))
