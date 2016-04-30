(ns cmr.indexer.test.data.collection-granule-aggregation-cache
  (require [clojure.test :refer :all]
           [clj-time.core :as t]
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


(deftest parse-aggregations-response-test
  (is (= {"C1-PROV1" {:granule-start-date (t/date-time 2003)
                      :granule-end-date (t/date-time 2008)}
          "C2-PROV1" {:granule-start-date (t/date-time 2001)
                      :granule-end-date nil}}
         (#'cgac/parse-aggregations sample-response))))
