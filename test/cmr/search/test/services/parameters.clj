(ns cmr.search.test.services.parameters
  (:require [clojure.test :refer :all]
            [cmr.search.services.parameters :as p]
            [cmr.search.models.query :as q]))

(deftest replace-parameter-aliases-test
  (testing "with options"
    (is (= {:entry_title "foo"
            :options {:entry_title {:ignore_case "true"}}}
           (p/replace-parameter-aliases
             {:dataset_id "foo"
              :options {:dataset_id {:ignore_case "true"}}}))))
  (testing "with no options"
    (is (= {:entry_title "foo" :options nil}
           (p/replace-parameter-aliases {:dataset_id "foo"})))))


(def valid-params
  "Example valid parameters"
  {:entry_title "foo"
   :options {:entry_title {:ignore_case "true"}}})

(deftest individual-parameter-validation-test
  (testing "unrecognized parameters"
    (is (= [] (p/unrecognized-params-validation valid-params)))
    (is (= ["Parameter [foo] was not recognized."
            "Parameter [bar] was not recognized."]
           (p/unrecognized-params-validation
             {:entry_title "fdad"
              :foo 1
              :bar 2}))))
  (testing "invalid options param names"
    (is (= [] (p/unrecognized-params-in-options-validation valid-params)))
    (is (= ["Parameter [foo] with option was not recognized."]
           (p/unrecognized-params-in-options-validation
             {:entry_title "fdad"
              :options {:foo {:ignore_case "true"}}}))))
  (testing "invalid options param args"
    (is (= ["Option [foo] for param [entry_title] was not recognized."]
           (p/unrecognized-params-settings-in-options-validation
             {:entry_title "fdad"
              :options {:entry_title {:foo "true"}}})))))

(deftest validate-parameters-test
  (testing "parameters are returned when valid"
    (is (= valid-params (p/validate-parameters valid-params))))
  (testing "errors thrown when parameters are invalid."
    (try
      (p/validate-parameters {:entry_title "fdad"
                              :foo 1
                              :bar 2})
      (is false "An error should have been thrown.")
      (catch clojure.lang.ExceptionInfo e
        (is (= {:type :invalid-data
                :errors ["Parameter [foo] was not recognized."
                         "Parameter [bar] was not recognized."]}
               (ex-data e)))))))

(deftest parameter->condition-test
  (testing "String conditions"
    (testing "with one value"
      (is (= (q/string-condition :entry_title "bar")
             (p/parameter->condition :entry_title "bar" nil))))
    (testing "with multiple values"
      (is (= (q/or-conds [(q/string-condition :entry_title "foo")
                          (q/string-condition :entry_title "bar")])
             (p/parameter->condition :entry_title ["foo" "bar"] nil))))
    (testing "case-insensitive"
      (is (= (q/string-condition :entry_title "bar" false false)
             (p/parameter->condition :entry_title "bar" {:entry_title {:ignore_case "true"}}))))
    (testing "pattern"
      (is (= (q/string-condition :entry_title "bar*" true false)
             (p/parameter->condition :entry_title "bar*" {})))
      (is (= (q/string-condition :entry_title "bar*" true true)
             (p/parameter->condition :entry_title "bar*" {:entry_title {:pattern "true"}}))))))


(deftest parameters->query-test
  (testing "Empty parameters"
    (is (= (q/query :collection)
           (p/parameters->query :collection {}))))
  (testing "option map aliases are corrected"
    (is (= (q/query :collection (q/string-condition :entry_title "foo" false false))
           (p/parameters->query :collection {:entry_title ["foo"]
                                             :options {:entry_title {:ignore_case "true"}}}))))
  (testing "with one condition"
    (is (= (q/query :collection (q/string-condition :entry_title "foo"))
           (p/parameters->query :collection {:entry_title ["foo"]}))))
  (testing "with multiple conditions"
    (is (= (q/query :collection (q/and-conds [(q/string-condition :entry_title "foo")
                                              (q/string-condition :provider "bar")]))
           (p/parameters->query :collection {:entry_title ["foo"] :provider "bar"})))))


