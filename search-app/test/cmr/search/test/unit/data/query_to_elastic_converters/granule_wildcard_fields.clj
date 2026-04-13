(ns cmr.search.test.unit.data.query-to-elastic-converters.granule-wildcard-fields
  "Tests wildcard field selection for granule string searches."
  (:require
   [clojure.test :refer :all]
   [cmr.common.services.search.query-model :as qm]
   [cmr.elastic-utils.config :as elastic-config]
   [cmr.elastic-utils.search.es-params-converter :as params]
   [cmr.elastic-utils.search.es-query-to-elastic :as q2e]
   [cmr.search.data.query-to-elastic]
   [cmr.search.services.parameters.conversion]))

(deftest granule-string-condition-field-selection-test
  (with-redefs [elastic-config/enable-wildcard-field-searches (constantly true)]
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
              :granule))))))

(deftest readable-granule-name-wildcard-field-selection-test
  (with-redefs [elastic-config/enable-wildcard-field-searches (constantly true)]
    (let [condition (params/parameter->condition
                     nil
                     :granule
                     :readable-granule-name
                     "Gran*"
                     {:readable-granule-name {:pattern "true"}})]
      (is (= {:bool {:should [{:wildcard {"granule-ur-lowercase-wildcard" "gran*"}}
                              {:wildcard {"producer-granule-id-lowercase-wildcard" "gran*"}}]
                      :minimum_should_match 1}}
             (q2e/condition->elastic condition :granule))))))

(deftest wildcard-field-feature-toggle-test
  (testing "when feature toggle is enabled, wildcard searches use wildcard fields"
    (with-redefs [elastic-config/enable-wildcard-field-searches (constantly true)]
      (is (= {:wildcard {"granule-ur-lowercase-wildcard" "gran*"}}
             (q2e/condition->elastic
              (qm/string-condition :granule-ur "Gran*" false true)
              :granule)))
      (is (= {:wildcard {"producer-granule-id-wildcard" "Prod*"}}
             (q2e/condition->elastic
              (qm/string-condition :producer-granule-id "Prod*" true true)
              :granule)))))

  (testing "when feature toggle is disabled, wildcard searches use standard fields"
    (with-redefs [elastic-config/enable-wildcard-field-searches (constantly false)]
      (is (= {:wildcard {"granule-ur-lowercase" "gran*"}}
             (q2e/condition->elastic
              (qm/string-condition :granule-ur "Gran*" false true)
              :granule)))
      (is (= {:wildcard {"granule-ur" "Gran*"}}
             (q2e/condition->elastic
              (qm/string-condition :granule-ur "Gran*" true true)
              :granule)))))

  (testing "exact (non-pattern) searches are not affected by the toggle"
    (with-redefs [elastic-config/enable-wildcard-field-searches (constantly true)]
      (is (= {:term {"granule-ur-lowercase" "granule1"}}
             (q2e/condition->elastic
              (qm/string-condition :granule-ur "Granule1" false false)
              :granule))))
    (with-redefs [elastic-config/enable-wildcard-field-searches (constantly false)]
      (is (= {:term {"granule-ur-lowercase" "granule1"}}
             (q2e/condition->elastic
              (qm/string-condition :granule-ur "Granule1" false false)
              :granule))))))
