(ns cmr.search.test.services.query-walkers.temporal-range-extractor
  "Tests for extracting temporal ranges from queries"
 (:require
  [clojure.test :refer :all]
  [clj-time.core :as time]
  [cmr.common-app.services.search.query-model :as qm]
  [cmr.search.models.query :as query]
  [cmr.search.services.query-walkers.temporal-range-extractor :as range-extractor]
  [cmr.search.test.models.helpers :as query-helper]))


(deftest extract-temporal-range-test
  (let [query (qm/query {:condition
                         (query/map->TemporalCondition {:start-date (time/date-time 2014 01 01)
                                                        :end-date (time/date-time 2016 01 01)})})]
   (is (range-extractor/contains-temporal-ranges? query))
   (is (= [{:start-date (time/date-time 2014 01 01) :end-date (time/date-time 2016 01 01)}]
          (range-extractor/extract-temporal-ranges query)))))

(deftest extract-temporal-range-test-no-start
 (let [query (qm/query {:condition
                        (query/map->TemporalCondition {:end-date (time/date-time 2016 01 01)})})]
   (is (range-extractor/contains-temporal-ranges? query))
   (is (= [{:end-date (time/date-time 2016 01 01)}]
          (range-extractor/extract-temporal-ranges query)))))

(deftest extract-temporal-range-test-no-end
  (let [query (qm/query {:condition
                         (query/map->TemporalCondition {:start-date (time/date-time 2014 01 01)})})]
   (is (range-extractor/contains-temporal-ranges? query))
   (is (= [{:start-date (time/date-time 2014 01 01)}]
          (range-extractor/extract-temporal-ranges query)))))

(deftest extract-temporal-range-test-multiple
  (let [query (qm/query {:condition
                         (query-helper/or-conds
                           (query/map->TemporalCondition {:start-date (time/date-time 2014 01 01)
                                                          :end-date (time/date-time 2016 01 01)})
                           (query/map->TemporalCondition {:start-date (time/date-time 1993 01 01)
                                                          :end-date (time/date-time 1994 01 01)})
                           (query/map->TemporalCondition {:start-date (time/date-time 2016 10 01)}))})]
    (is (range-extractor/contains-temporal-ranges? query))
    (is (= [{:start-date (time/date-time 2014 01 01) :end-date (time/date-time 2016 01 01)}
            {:start-date (time/date-time 1993 01 01) :end-date (time/date-time 1994 01 01)}
            {:start-date (time/date-time 2016 10 01)}]
           (range-extractor/extract-temporal-ranges query)))))

(deftest no-temporal-range
  (let [query (qm/query
               (query-helper/and-conds
                (qm/text-condition :keyword "foo bar")
                (query-helper/other 1)
                (query-helper/and-conds
                 (qm/string-condition :platform "platform1 platform2")
                 (qm/string-condition :project "project")
                 (qm/string-condition :instrument "instrument")
                 (qm/string-condition :sensor "sensor"))))]
   (is (= false (range-extractor/contains-temporal-ranges? query)))
   (is (empty? (range-extractor/extract-temporal-ranges query)))))
