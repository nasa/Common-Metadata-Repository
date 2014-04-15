(ns cmr.search.test.services.parameters
  (:require [clojure.test :refer :all]
            [cmr.search.services.parameter_validation :as pv]))

(def valid-params
  "Example valid parameters"
  {:entry_title "foo"
   :options {:entry_title {:ignore_case "true"}}})

(deftest individual-parameter-validation-test
  (testing "unrecognized parameters"
    (is (= [] (pv/unrecognized-params-validation :collection valid-params)))
    (is (= ["Parameter [foo] was not recognized."
            "Parameter [bar] was not recognized."]
           (pv/unrecognized-params-validation :collection
             {:entry_title "fdad"
              :foo 1
              :bar 2}))))
  (testing "invalid options param names"
    (is (= [] (pv/unrecognized-params-in-options-validation :collection valid-params)))
    (is (= ["Parameter [foo] with option was not recognized."]
           (pv/unrecognized-params-in-options-validation :collection
             {:entry_title "fdad"
              :options {:foo {:ignore_case "true"}}}))))
  (testing "invalid options param args"
    (is (= ["Option [foo] for param [entry_title] was not recognized."]
           (pv/unrecognized-params-settings-in-options-validation :collection
             {:entry_title "fdad"
              :options {:entry_title {:foo "true"}}})))))

(deftest validate-parameters-test
  (testing "parameters are returned when valid"
    (is (= valid-params (pv/validate-parameters :collection valid-params)))
    (is (= valid-params (pv/validate-parameters :granule valid-params))))
  (testing "parameters are validated according to concept-type"
    (is (= {:granule_ur "Dummy"} (pv/validate-parameters :granule {:granule_ur "Dummy"})))
    (is (thrown? clojure.lang.ExceptionInfo (pv/validate-parameters :collection {:granule_ur "Dummy"}))))
  (testing "errors thrown when parameters are invalid."
    (try
      (pv/validate-parameters :collection {:entry_title "fdad"
                              :foo 1
                              :bar 2})
      (is false "An error should have been thrown.")
      (catch clojure.lang.ExceptionInfo e
        (is (= {:type :invalid-data
                :errors ["Parameter [foo] was not recognized."
                         "Parameter [bar] was not recognized."]}
               (ex-data e)))))))
