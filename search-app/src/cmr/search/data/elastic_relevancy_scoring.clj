(ns cmr.search.data.elastic-relevancy-scoring
  "Functions related to the calculation of relevancy scoring and sorting of results within
  Elasticsearch."
  (:require
   [clj-time.coerce :as time-coerce]
   [clj-time.core :as time]
   [clojure.java.io :as io]
   [cmr.common.config :refer [defconfig]]
   [cmr.search.data.temporal-ranges-to-elastic :as temporal-to-elastic]
   [cmr.search.services.query-execution.temporal-conditions-results-feature :as temporal-conditions]
   [cmr.search.services.query-walkers.keywords-extractor :as keywords-extractor]))

(defconfig sort-use-relevancy-score
  "Indicates whether in keyword search sorting if the community usage relevancy score should be used
  to sort collections. If true, consider the usage score as a tie-breaker when keyword relevancy
  scores or the same. If false, no tie-breaker is applied.
  This config is here to allow for the usage score to be turned off until elastic indexes are
  updated-since so keyword search will not be broken"
  {:type Boolean
   :default true})

(defconfig sort-use-temporal-relevancy
  "Indicates whether when searching using a temporal range if we should use temporal overlap
  relevancy to sort. If true, use the temporal overlap script in elastic. This config allows
  temporal overlap calculations to be turned off if needed for performance."
  {:type Boolean
   :default true})

(defconfig sort-bin-keyword-scores
  "When sorting by keyword score, set to true if we want to bin the keyword scores
  i.e. round to the nearest keyword-score-bin-size"
  {:type Boolean
   :default true})

(defconfig keyword-score-bin-size
  "When sort-bin-keyword-scores is true, the keyword score should
  be rounded to the nearest keyword-score-bin-size"
  {:type Double
   :default 0.2})

(def keyword-score-bin-script
  "Painless script used by elastic to bin the keyword score based on bin-size"
  (slurp (io/resource "bin_keyword_score.painless")))

(defconfig community-usage-bin-size
  "When sort-use-relevancy-score is true, the community usage score should
  be rounded to the nearest community-usage-bin-size"
  {:type Double
   :default 400.0})

(def community-usage-bin-script
  "Painless script used by elastic to bin the community usage value based on bin-size"
  (slurp (io/resource "bin_community_usage.painless")))

(defn score-sort-order
  "Determine the keyword sort order based on the sort-use-relevancy-score config and the presence
   of temporal range parameters in the query.

   The algorithm is to currently compare scores in the following order (we only go to the next level
   in the scoring system in the case of a tie at the higher level):
   1. community usage, if usage_score sort key present
   2. keyword boost
   3. temporal overlap
   4. relevancy with community usage secondary
   5. temporal end date of the collection
   6. processing level."
  [query]
  (let [use-keyword-sort? (keywords-extractor/contains-keyword-condition? query)
        use-usage-sort? (seq (->> query
                                  :sort-keys
                                  (filter #(= :usage-relevancy-score (:field %)))))
        use-temporal-sort? (and (temporal-conditions/contains-temporal-conditions? query)
                                (sort-use-temporal-relevancy))]
    (seq
      (concat
        (when use-usage-sort?
          [{:_script {:type :number
                      :script {:params {:binSize (community-usage-bin-size)}
                               :source community-usage-bin-script}
                      :order :desc}}])
        (when use-keyword-sort?
          (if (sort-bin-keyword-scores)
            [{:_script {:type :number
                        :script {:params {:binSize (keyword-score-bin-size)}
                                 :source keyword-score-bin-script}
                        :order :desc}}]
            [{:_score {:order :desc}}]))
        (when use-temporal-sort?
          [{:_script (temporal-to-elastic/temporal-overlap-sort-script query)}])
        ;; We only include this if one of the others is present
        (when (and (or use-temporal-sort? use-keyword-sort?)
                   (not use-usage-sort?)
                   (sort-use-relevancy-score))
          [{:_script {:type :number
                      :script {:params {:binSize (community-usage-bin-size)}
                               :source community-usage-bin-script
                                        ; :missing 0
                               }
                      :order :desc}}])
        ;; If end-date is nil, collection is ongoing so use today so ongoing
        ;; collections will be at the top
        (when use-keyword-sort?
          [{:end-date {:order :desc
                       :missing (time-coerce/to-long (time/now))}}
           {:processing-level-id-lowercase-humanized {:order :desc}}])))))

(defn- temporal-sort-order
  "If there are temporal ranges in the query and temporal relevancy sorting is turned on,
  define the temporal sort order based on the sort-usage-relevancy-score. If sort-use-temporal-relevancy
  is turned off, return nil so the default sorting gets used."
  [query]
  (when (and (temporal-conditions/contains-temporal-conditions? query)
             (sort-use-temporal-relevancy))
    (if (sort-use-relevancy-score)
      [{:_script (temporal-to-elastic/temporal-overlap-sort-script query)}
       {:usage-relevancy-score {:order :desc :missing 0}}]
      [{:_script (temporal-to-elastic/temporal-overlap-sort-script query)}])))
