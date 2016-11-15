(ns cmr.indexer.data.concepts.collection.community-usage-metrics
  "Contains functions to retrieve the community usage relevancy score for the collection"
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [cmr.common.util :as util]
    [cmr.common-app.humanizer :as humanizer]
    [cmr.indexer.data.concepts.collection.science-keyword :as sk]
    [cmr.indexer.data.metrics-fetcher :as metrics-fetcher]))

(defn collection-community-usage-score
  "Given a umm-spec collection, returns the community usage relevancy score"
  [context collection]
  (when-let [metrics (seq (metrics-fetcher/get-community-usage-metrics context))]
    (let [{:keys [Version ShortName]} collection
          usage-entry (filter #(and (= (:version %) Version)
                                    (= (:short-name %) ShortName))
                              metrics)]
      (when (seq usage-entry)
        {:usage-relevancy-score (:access-count (first usage-entry))}))))
