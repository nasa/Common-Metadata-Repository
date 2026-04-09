(ns cmr.search.test.unit.data.query-to-elastic-converters.granule-wildcard-fields
  "Tests wildcard field selection for granule string searches."
  (:require
   [clojure.test :refer :all]
   [cmr.common.services.search.query-model :as qm]
   [cmr.elastic-utils.search.es-params-converter :as params]
   [cmr.elastic-utils.search.es-query-to-elastic :as q2e]
   [cmr.search.data.query-to-elastic]
   [cmr.search.services.parameters.conversion]))

(deftest granule-string-condition-field-selection-test
  (testing "granule-ur exact search uses lowercase keyword field"
    (is (= {:term {"granule-ur-lowercase" "granule1"}}
           (q2e/condition->elastic
            (qm/string-condition :granule-ur "Granule1" false false)
            :granule))))
  (testing "granule-ur wildcard search uses wildcard field"
    (is (= {:wildcard {"granule-ur-lowercase-wildcard" "gran*"}}
           (q2e/condition->elastic
            (qm/string-condition :granule-ur "Gran*" false true)
            :granule))))
  (testing "producer-granule-id exact search uses lowercase keyword field"
    (is (= {:term {"producer-gran-id-lowercase" "producer1"}}
           (q2e/condition->elastic
            (qm/string-condition :producer-granule-id "Producer1" false false)
            :granule))))
  (testing "producer-granule-id wildcard search uses wildcard field"
    (is (= {:wildcard {"producer-granule-id-lowercase-wildcard" "prod*"}}
           (q2e/condition->elastic
            (qm/string-condition :producer-granule-id "Prod*" false true)
            :granule))))
  (testing "case-sensitive wildcard search uses the case-sensitive wildcard field"
    (is (= {:wildcard {"producer-granule-id-wildcard" "Prod*"}}
           (q2e/condition->elastic
            (qm/string-condition :producer-granule-id "Prod*" true true)
            :granule)))))

(deftest readable-granule-name-wildcard-field-selection-test
  (let [condition (params/parameter->condition
                   nil
                   :granule
                   :readable-granule-name
                   "Gran*"
                   {:readable-granule-name {:pattern "true"}})]
    (is (= {:bool {:should [{:wildcard {"granule-ur-lowercase-wildcard" "gran*"}}
                            {:wildcard {"producer-granule-id-lowercase-wildcard" "gran*"}}]
                    :minimum_should_match 1}}
           (q2e/condition->elastic condition :granule)))))
