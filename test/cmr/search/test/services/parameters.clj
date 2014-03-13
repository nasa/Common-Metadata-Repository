(ns cmr.search.test.services.parameters
  (:require [clojure.test :refer :all]
            [cmr.search.services.parameters :as p]))

(def valid-params
  "Example valid parameters"
  {:dataset_id "dd"})

(deftest individual-parameter-validation-test
  (testing "unrecognized parameters"
    (is (= [] (p/unrecognized-params-validation valid-params)))
    (is (= ["Parameter [foo] was not recognized."
            "Parameter [bar] was not recognized."]
           (p/unrecognized-params-validation
             {:dataset_id "fdad"
              :foo 1
              :bar 2})))))

(deftest validate-parameters-test
  (testing "parameters are returned when valid"
    (is (= valid-params (p/validate-parameters valid-params))))
  (testing "errors thrown when parameters are invalid."
    (try
      (p/validate-parameters {:dataset_id "fdad"
                              :foo 1
                              :bar 2})
      (is false "An error should have been thrown.")
      (catch clojure.lang.ExceptionInfo e
        (is (= {:type :invalid-data
                :errors ["Parameter [foo] was not recognized."
                         "Parameter [bar] was not recognized."]}
               (ex-data e)))))))

