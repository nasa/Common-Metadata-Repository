(ns cmr.indexer.data.concepts.collection.community-usage-metrics
  "Contains functions to retrieve the community usage relevancy score for the collection"
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [cmr.common.util :as util]
    [cmr.common-app.humanizer :as humanizer]
    [cmr.indexer.data.concepts.collection.science-keyword :as sk]
    [cmr.indexer.data.metrics-fetcher :as metrics-fetcher]))

(def not-provided-version
  "In EMS community usage CSV, the version value when the version is unknown"
  "N/A")

(defn collection-community-usage-score
  "Given a umm-spec collection, returns the community usage relevancy score.
  The score comes from the ingested EMS metrics, if available. The score is equal to:
  the access count for that collection/version (if exists, should only be 1) + the access count for
  that collection/'N/A' (if exists). This is temporary behavior and the 'N/A' version entries should
  be applied to the highest version of the collection. "
  [context collection]
  (when-let [metrics (seq (metrics-fetcher/get-community-usage-metrics context))]
    (let [{:keys [Version ShortName]} collection]
      (when-let [usage-entries (seq (filter #(and (= (:short-name %) ShortName)
                                                  (or (= (:version %) Version)
                                                      (= (:version %) not-provided-version)))
                                            metrics))]
        {:usage-relevancy-score (apply + (map :access-count usage-entries))}))))
