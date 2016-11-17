(ns cmr.indexer.data.concepts.collection.community-usage-metrics
  "Contains functions to retrieve the community usage relevancy score for the collection"
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [cmr.common.util :as util]
    [cmr.common-app.humanizer :as humanizer]
    [cmr.indexer.data.concepts.collection.science-keyword :as sk]
    [cmr.indexer.data.metrics-fetcher :as metrics-fetcher]))

(defn- normalize-score
  "Normalize score to be a value between 0 and 1 by getting the max score from the metrics
  and calculating the score as a percent of that."
  [score metrics]
  (let [max-score (apply max (map :access-count metrics))]
    (if (> max-score 0)
      (/ score max-score)
      0)))

(defn collection-community-usage-score
  "Given a umm-spec collection, returns the community usage relevancy score"
  [context collection]
  (when-let [metrics (seq (metrics-fetcher/get-community-usage-metrics context))]
    (let [{:keys [Version ShortName]} collection
          usage-entry (filter #(and (= (:version %) Version)
                                    (= (:short-name %) ShortName))
                              metrics)]
      ;; There should be only one entry that matches
      (when (seq usage-entry)
        {:usage-relevancy-score (normalize-score (:access-count (first usage-entry)) metrics)}))))
