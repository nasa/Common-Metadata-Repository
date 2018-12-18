(ns cmr.indexer.test.data.collection-granule-aggregation-cache
  (:require
   [clj-time.coerce :as c]
   [clj-time.core :as t]
   [clojure.edn :as edn]
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :refer [for-all]]
   [cmr.common.test.test-check-ext :as ext-gen :refer [defspec]]
   [cmr.common.util :as u]
   [cmr.indexer.data.collection-granule-aggregation-cache :as cgac]))

(def sample-response
  {:took 7,
   :timed_out false,
   :_shards {:total 35, :successful 35, :failed 0},
   :hits {:total 5, :max_score 0.0, :hits []},
   :aggregations {:collection-concept-id
                  {:sum_other_doc_count 0
                   :buckets
                   [{:key "C1-PROV1",
                     :doc_count 3,
                     :min-temporal {:value 1.0413792E12}
                     :max-temporal {:value 1.1991456E12}
                     :no-end-date {:doc_count 0}}
                    {:key "C2-PROV1",
                     :doc_count 2,
                     :min-temporal {:value 9.783072E11}
                     :max-temporal {:value 1.0729152E12}
                     :no-end-date {:doc_count 1}}]}}})

(def sample-coll-gran-aggregates
  {"C1-PROV1" {:granule-start-date (t/date-time 2003)
               :granule-end-date (t/date-time 2008)}
   "C2-PROV1" {:granule-start-date (t/date-time 2001)
               :granule-end-date nil}})


(deftest parse-aggregations-response-test
  (is (= sample-coll-gran-aggregates (#'cgac/parse-aggregations sample-response))))

(deftest coll-gran-aggregates-to-cached-value
  (is (= sample-coll-gran-aggregates
         (-> sample-coll-gran-aggregates
             (#'cgac/coll-gran-aggregates->cachable-value)
             ;; We have to be able to convert the cached value to and from EDN.
             pr-str
             edn/read-string
             (#'cgac/cached-value->coll-gran-aggregates)))))

(defn times->longs
  "Converts all the times in a collection granule aggregate to long values. This makes it easier to
   compare and construct for testing."
  [coll-gran-aggregate]
  (u/map-values (fn [m]
                  (u/map-values #(when % (c/to-long %)) m))
                coll-gran-aggregate))

(defn longs->times
  "Converts all the collection granule aggregate with real time values to longs. This makes it easier
   to compare and construct for testing."
  [coll-gran-aggregate]
  (u/map-values (fn [m]
                  (u/map-values #(when % (c/from-long %)) m))
                coll-gran-aggregate))

(def collection-id-gen
  "A generate of a small set of collection ids"
  (gen/elements ["C1-PROV1" "C2-PROV1" "C3-PROV1" "C4-PROV1" "C1-PROV2"]))

(def granule-dates
  "Generator of maps containing granule start and end dates where end date is optional"
  (gen/fmap (fn [[start end]]
              {:granule-start-date start
               :granule-end-date end})
            ;; generate 1 to 2 dates as longs in order from smallest to largest.
            (gen/fmap sort (gen/vector gen/s-pos-int 1 2))))

(def collection-granule-aggregate-gen
  "A generator for collection granule aggregates"
  (gen/fmap #(into {} %)
            (gen/vector (gen/tuple collection-id-gen granule-dates))))

(defn merged-matches?
  "Returns true if merged map contains the correct merged data from collection granule aggregates 1 and 2."
  [merged cg1 cg2]
  (every?
   true?
   (for [[coll {:keys [granule-start-date granule-end-date]}] merged
         :let [gt1 (get cg1 coll)
               gt2 (get cg2 coll)]]
     (if (and gt1 gt2)
       (let [{cg1-start :granule-start-date cg1-end :granule-end-date} gt1
             {cg2-start :granule-start-date cg2-end :granule-end-date} gt2]

         (and ;The start date is equal the smaller of the two
              (= granule-start-date (min cg1-start cg2-start))

              (or ;The granule end date is nil if one of the others is nil
                  (and (nil? granule-end-date) (or (nil? cg1-end) (nil? cg2-end)))
                  ;; else all three are present and granule end date is equal to the largest date.
                  (and (some? granule-end-date) (some? cg1-end) (some? cg2-end)
                       (= granule-end-date (max cg1-end cg2-end))))))


       (let [{start :granule-start-date end :granule-end-date} (or gt1 gt2)]
         (and (= granule-start-date start)
              (= granule-end-date end)))))))

(defspec merging-collection-granule-aggrates-spec 100
  (for-all [[cg1 cg2] (gen/tuple collection-granule-aggregate-gen collection-granule-aggregate-gen)]
    (let [merged (#'cgac/merge-coll-gran-aggregates (longs->times cg1) (longs->times cg2))
          merged (times->longs merged)]
      ;; every collection in either is in the result
      (and (every? #(contains? merged %) (concat (keys cg1) (keys cg2)))
           (merged-matches? merged cg1 cg2)))))

;; Use this when debugging failures from previous spec
(comment
 (do
  (def cg1 (first failing-value))
  (def cg2 (second failing-value))
  (def merged (times->longs (#'cgac/merge-coll-gran-aggregates (longs->times cg1) (longs->times cg2)))))

 (every? #(contains? merged %) (concat (keys cg1) (keys cg2)))
 (every? true? (merged-matches merged cg1 cg2)))
