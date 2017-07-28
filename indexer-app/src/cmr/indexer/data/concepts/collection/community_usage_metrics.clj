(ns cmr.indexer.data.concepts.collection.community-usage-metrics
  "Contains functions to retrieve the community usage relevancy score for the collection"
  (:require
    [cmr.indexer.data.concepts.collection.collection-util :as util]
    [cmr.indexer.data.metrics-fetcher :as metrics-fetcher]))

(def not-provided-version
  "In EMS community usage CSV, the version value when the version is unknown"
  "N/A")

(defn collection-community-usage-score
  "Given a umm-spec collection, returns the community usage relevancy score.
  The score comes from the ingested EMS metrics, if available. The metrics are cached with the
  collection short name as the key and a list of version/access-count combos as the data.
  The score is the access count that matches that collection and version. Otherwise no score is
  returned.
  EMS metrics that have 'N/A' as the version are applied to every version of the collection"
  [context collection parsed-version-id]
  (let [metrics (metrics-fetcher/get-community-usage-metrics context)]
    (when (seq metrics)
      (when-let [usage-entries (seq (filter #(or (= (util/parse-version-id (:version %))
                                                    parsed-version-id)
                                                 (= not-provided-version (:version %)))
                                            (get metrics (:ShortName collection))))]
        {:usage-relevancy-score (apply + (map :access-count usage-entries))}))))
