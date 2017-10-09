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
  "Groovy script used by elastic to bin the keyword score based on bin-size"
  (slurp (io/resource "bin_keyword_score.groovy")))

(defconfig community-usage-bin-size
  "When sort-bin-keyword-scores is true, the keyword score should
  be rounded to the nearest keyword-score-bin-size"
  {:type Double
   :default 400.0})

(def community-usage-bin-script
  "Groovy script used by elastic to bin the community usage value based on bin-size"
  (slurp (io/resource "bin_community_usage.groovy")))

(defn score-sort-order
  "Determine the keyword sort order based on the sort-use-relevancy-score config and the presence
   of temporal range parameters in the query.

   The algorithm is to currently compare scores in the following order (we only go to the next level
   in the scoring system in the case of a tie at the higher level):
   1. keyword boost
   2. temporal overlap
   3. community usage
   4. temporal end date of the collection
   5. processing level."
  [query]
  (let [use-keyword-sort? (keywords-extractor/contains-keyword-condition? query)
        use-temporal-sort? (and (temporal-conditions/contains-temporal-conditions? query)
                                (sort-use-temporal-relevancy))]
    (seq
     (concat
       (when use-keyword-sort?
         (if (sort-bin-keyword-scores)
           [{:_script {:params {:binSize (keyword-score-bin-size)}
                       :script keyword-score-bin-script
                       :type :number
                       :order :desc}}]
           [{:_score {:order :desc}}]))
       (when use-temporal-sort?
         [{:_script (temporal-to-elastic/temporal-overlap-sort-script query)}])
       ;; We only include this if one of the others is present
       (when (and (or use-temporal-sort? use-keyword-sort?)
                  (sort-use-relevancy-score))
         [{:_script {:params {:binSize  (community-usage-bin-size)}
                     :type :number
                     :script community-usage-bin-script
                     :order :desc
                     :missing 0}}])
       ;; If end-date is nil, collection is ongoing so use today so ongoing
       ;; collections will be at the top
       (when use-keyword-sort?
         [{:end-date {:order :desc
                      :missing (time-coerce/to-long (time/now))}}
          {:processing-level-id.lowercase.humanized {:order :desc}}])))))

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
