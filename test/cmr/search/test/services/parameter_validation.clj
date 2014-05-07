(ns cmr.search.test.services.parameter-validation
  (:require [clojure.test :refer :all]
            [cmr.search.services.parameter-validation :as pv]
            [cmr.search.services.messages.attribute-messages :as attrib-msg]
            [cmr.search.services.messages.orbit-number-messages :as on-msg]
            [cmr.common.services.messages :as com-msg]))

(def valid-params
  "Example valid parameters"
  {:entry-title "foo"
   :page-size 10
   :options {:entry-title {:ignore-case "true"}}})

(deftest individual-parameter-validation-test
  (testing "unrecognized parameters"
    (is (= [] (pv/unrecognized-params-validation :collection valid-params)))
    (is (= #{"Parameter [foo] was not recognized."
             "Parameter [bar] was not recognized."}
           (set (pv/unrecognized-params-validation :collection
                                                   {:entry-title "fdad"
                                                    :foo 1
                                                    :bar 2})))))
  (testing "invalid options param names"
    (is (= [] (pv/unrecognized-params-in-options-validation :collection valid-params)))
    (is (= ["Parameter [foo] with option was not recognized."]
           (pv/unrecognized-params-in-options-validation :collection
                                                         {:entry-title "fdad"
                                                          :options {:foo {:ignore-case "true"}}}))))
  (testing "invalid options param args"
    (is (= ["Option [foo] for param [entry_title] was not recognized."]
           (pv/unrecognized-params-settings-in-options-validation :collection
                                                                  {:entry-title "fdad"
                                                                   :options {:entry-title {:foo "true"}}}))))
  (testing "Page size less than one"
    (is (= ["page_size must be a number between 1 and 2000"]
           (pv/page-size-validation :collection (assoc valid-params :page-size 0)))))

  (testing "Page size less than one"
    (is (= ["page_size must be a number between 1 and 2000"]
           (pv/page-size-validation :collection (assoc valid-params :page-size 0)))))
  (testing "Search with large page size"
    (is (= []
           (pv/page-size-validation :collection (assoc valid-params :page-size 100)))))
  (testing "Negative page size"
    (is (= ["page_size must be a number between 1 and 2000"]
           (pv/page-size-validation :collection (assoc valid-params :page-size -1)))))
  (testing "Page size too large."
    (is (= ["page_size must be a number between 1 and 2000"]
           (pv/page-size-validation :collection (assoc valid-params :page-size 2001)))))
  (testing "Non-numeric page size"
    (is (= ["page_size must be a number between 1 and 2000"]
           (pv/page-size-validation :collection (assoc valid-params :page-size "ABC")))))
  (testing "Valid page_num"
    (is (= []
           (pv/page-num-validation :collection (assoc valid-params :page-num 5)))))
  (testing "Page num less than one"
    (is (= ["page_num must be a number greater than or equal to 1"]
           (pv/page-num-validation :collection (assoc valid-params :page-num 0)))))
  (testing "Non-numeric page num"
    (is (= ["page_num must be a number greater than or equal to 1"]
           (pv/page-num-validation :collection (assoc valid-params :page-num "ABC")))))
  (testing "Valid exact orbit_number"
    (is (= []
           (pv/orbit-number-validation :granule (assoc valid-params :orbit-number "10")))))
  (testing "Valid orbit_number range"
    (is (= []
           (pv/orbit-number-validation :granule (assoc valid-params :orbit-number "1,2")))))
  (testing "Non-numeric single orbit-number"
    (is (= [(on-msg/invalid-orbit-number-msg) (com-msg/invalid-numeric-range-msg "A")]
           (pv/orbit-number-validation :granlue (assoc valid-params :orbit-number "A")))))
  (testing "Non-numeric start-orbit-number"
    (is (= [(on-msg/invalid-orbit-number-msg) (com-msg/invalid-numeric-range-msg "A,10")]
           (pv/orbit-number-validation :granule (assoc valid-params :orbit-number "A,10")))))
  (testing "Non-numeric stop-orbit-number"
    (is (= [(on-msg/invalid-orbit-number-msg) (com-msg/invalid-numeric-range-msg "10,A")]
           (pv/orbit-number-validation :granule (assoc valid-params :orbit-number "10,A"))))))

(deftest temporal-format-validation :collection-start-date-test
  (testing "valid-start-date"
    (is (empty? (pv/temporal-format-validation :collection {:temporal ["2014-04-05T00:00:00Z"]})))
    (is (empty? (pv/temporal-format-validation :collection {:temporal ["2014-04-05T00:00:00"]})))
    (is (empty? (pv/temporal-format-validation :collection {:temporal ["2014-04-05T00:00:00.123Z"]})))
    (is (empty? (pv/temporal-format-validation :collection {:temporal ["2014-04-05T00:00:00.123"]}))))
  (testing "invalid-start-date"
    (are [start-date]
         (let [error (pv/temporal-format-validation :collection {:temporal [start-date]})]
           (is (= 1 (count error)))
           (re-find (re-pattern "temporal datetime is invalid:") (first error)))
         "2014-13-05T00:00:00Z"
         "2014-04-00T00:00:00Z"
         "2014-04-05T24:00:00Z"
         "2014-04-05T00:60:00Z"
         "2014-04-05T00:00:60Z")))

(deftest temporal-format-validation :collection-end-date-test
  (testing "valid-end-date"
    (is (empty? (pv/temporal-format-validation :collection {:temporal [",2014-04-05T00:00:00Z"]})))
    (is (empty? (pv/temporal-format-validation :collection {:temporal [",2014-04-05T00:00:00"]})))
    (is (empty? (pv/temporal-format-validation :collection {:temporal [",2014-04-05T00:00:00.123Z"]})))
    (is (empty? (pv/temporal-format-validation :collection {:temporal [",2014-04-05T00:00:00.123"]}))))
  (testing "invalid-end-date"
    (are [end-date]
         (let [error (pv/temporal-format-validation :collection {:temporal [end-date]})]
           (is (= 1 (count error)))
           (re-find (re-pattern "temporal datetime is invalid:") (first error)))
         ",2014-13-05T00:00:00Z"
         ",2014-04-00T00:00:00Z"
         ",2014-04-05T24:00:00Z"
         ",2014-04-05T00:60:00Z"
         ",2014-04-05T00:00:60Z")))

(deftest validate-temporal-start-day-test
  (testing "valid-start-day"
    (are [start-day] (empty? (pv/temporal-format-validation
                               :collection
                               {:temporal [(str "2014-04-05T18:45:51Z,," start-day)]}))
         "1"
         "366"
         "10"))
  (testing "invalid-start-day"
    (are [start-day err-msg] (= [err-msg]
                                (pv/temporal-format-validation
                                  :collection
                                  {:temporal [(str "2014-04-05T18:45:51Z,," start-day)]}))
         "x" "temporal_start_day [x] must be an integer between 1 and 366"
         "0" "temporal_start_day [0] must be an integer between 1 and 366"
         "367" "temporal_start_day [367] must be an integer between 1 and 366")))

(deftest validate-temporal-end-day-test
  (testing "valid-end-day"
    (are [end-day] (empty? (pv/temporal-format-validation
                             :collection
                             {:temporal [(str "2014-04-05T18:45:51Z,," end-day)]}))
         "1"
         "366"
         "10"))
  (testing "invalid-end-day"
    (are [end-day err-msg] (= [err-msg]
                              (pv/temporal-format-validation
                                :collection
                                {:temporal [(str "2013-04-05T18:45:51Z,2014-04-05T18:45:51Z,," end-day)]}))
         "x" "temporal_end_day [x] must be an integer between 1 and 366"
         "0" "temporal_end_day [0] must be an integer between 1 and 366"
         "367" "temporal_end_day [367] must be an integer between 1 and 366")))

(deftest validate-attributes-is-a-sequence
  (is (= [(attrib-msg/attributes-must-be-sequence-msg)]
         (pv/attribute-validation :granule {:attribute "foo"}))))

(deftest validate-parameters-test
  (testing "parameters are returned when valid"
    (is (= valid-params (pv/validate-parameters :collection valid-params)))
    (is (= valid-params (pv/validate-parameters :granule valid-params))))
  (testing "parameters are validated according to concept-type"
    (is (= {:granule-ur "Dummy"} (pv/validate-parameters :granule {:granule-ur "Dummy"})))
    (is (thrown? clojure.lang.ExceptionInfo (pv/validate-parameters :collection {:granule-ur "Dummy"}))))
  (testing "errors thrown when parameters are invalid."
    (try
      (pv/validate-parameters :collection {:entry-title "fdad"
                                           :foo 1
                                           :bar 2})
      (is false "An error should have been thrown.")
      (catch clojure.lang.ExceptionInfo e
        (is (= {:type :invalid-data
                :errors #{"Parameter [foo] was not recognized."
                         "Parameter [bar] was not recognized."}}
               (update-in (ex-data e) [:errors] set)))))))


