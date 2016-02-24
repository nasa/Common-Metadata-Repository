(ns cmr.search.services.query-execution.tags-results-feature
  "This enables the :include-tags feature for collection search results. When it is enabled
  collection search results will include the list of tags that are associated with the collection."
  (:require [clojure.string :as str]
            [cmr.common.util :as util]
            [cmr.common-app.services.search.query-execution :as query-execution]))

(defn- escape-wildcards
  [value]
  (-> value
      ;; Escape * and ?
      (str/replace "*" ".*")
      (str/replace "?" ".?")))

(defmethod query-execution/post-process-query-result-feature :tags
  [context query elastic-results query-results feature]
  (let [include-tags (get-in query [:result-options :tags])
        include-patterns (map #(re-pattern (escape-wildcards %)) include-tags)
        match-include-patterns (fn [tag]
                                 (when tag (some #(re-find % tag) include-patterns)))]
    (util/update-in-each query-results
                         [:items]
                         (fn [item]
                           (update item :tags #(seq (filter match-include-patterns %)))))))


