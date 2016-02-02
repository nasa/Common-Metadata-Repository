(ns cmr.search.services.query-execution.highlight-results-feature
  "This enables returning highlighted snippets with collection search results based on the
  provided keyword search."
  (:require [cmr.common-app.services.search.query-execution :as query-execution]
            [cmr.common-app.services.search.results :as r]
            [cmr.common-app.services.search.query-to-elastic :as q2e]
            [clojure.string :as str]
            [cmr.common.util :as util]))

(defn get-keyword-conditions
  "Returns a list of the keyword text conditions from a condition"
  [cndn]
  (let [{:keys [field condition conditions]} cndn]
    (if (= :keyword field)
      [cndn]
      (flatten (map get-keyword-conditions (if condition
                                             (conj conditions condition)
                                             conditions))))))

(defn- add-tag
  "Add a tag to the highlight query map if its value is not nil."
  [query-map tag value]
  (if value
    (assoc query-map tag [value])
    query-map))

(defn- add-fragment-options
  "Add highlight fragment options to highlight query if available."
  [query-map snippet-length num-snippets]
  (let [fragment-options (util/remove-nil-keys
                           {:fragment_size snippet-length :number_of_fragments num-snippets})]
    (if (empty? fragment-options)
      query-map
      (update-in query-map [:fields :summary] merge fragment-options))))

(defn- build-highlight-query
  "Creates the highlight query that will be passed into elastic"
  [query]
  (when-let [keyword-conditions (get-keyword-conditions (:condition query))]
    (let [{:keys [begin-tag end-tag snippet-length num-snippets]}
          (get-in query [:result-options :highlights])
          conditions-as-string (str/join " " (map :query-str keyword-conditions))
          query-map {:fields
                     {:summary {:highlight_query
                                {:query_string
                                 {:query (q2e/escape-query-string conditions-as-string)}}}}}]
      (-> query-map
          (add-tag :pre_tags begin-tag)
          (add-tag :post_tags end-tag)
          (add-fragment-options snippet-length num-snippets)))))

(defmethod query-execution/pre-process-query-result-feature :highlights
  [_ query _]
  (assoc query :highlights (build-highlight-query query)))

(defn- get-highlighted-summary-map
  "Returns a map of id to highlighted summary snippets for a set of elastic results."
  [elastic-results]
  (into {} (map (fn [hit] (let [id (:_id hit)
                                summary (get-in hit [:highlight :summary])]
                            {id summary}))
                (get-in elastic-results [:hits :hits]))))

(defn- replace-fields-with-highlighted-fields
  "Replaces the appropriate fields with the highlighted snippets for each field."
  [query-results elastic-results]
  (let [highlighted-summary-map (get-highlighted-summary-map elastic-results)]
    (for [item (:items query-results)
          :let [highlight (get highlighted-summary-map (:id item))]]
      (if (seq highlight)
        (assoc item :highlighted-summary-snippets highlight)
        item))))

(defmethod query-execution/post-process-query-result-feature :highlights
  [context query elastic-results query-results feature]
  (assoc query-results
         :items (replace-fields-with-highlighted-fields query-results elastic-results)))
