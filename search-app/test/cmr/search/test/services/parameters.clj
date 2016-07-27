(ns cmr.search.test.services.parameters
  (:require [clojure.test :refer :all]
            [cmr.common.util :refer [are2]]
            [cmr.common-app.services.search.params :as p]
            [cmr.common-app.services.search.query-model :as q]
            [cmr.common-app.services.search.group-query-conditions :as gc]
            [cmr.search.services.parameters.legacy-parameters :as lp]
            [cmr.search.services.parameters.provider-short-name :as psn]))

(deftest replace-parameter-aliases-test
  (testing "with options"
    (is (= {:entry-title "foo"
            :options {:entry-title {:ignore-case "true"}}}
           (lp/replace-parameter-aliases
             {:dataset-id "foo"
              :options {:dataset-id {:ignore-case "true"}}}))))
  (testing "with no options"
    (is (= {:entry-title "foo"}
           (lp/replace-parameter-aliases {:dataset-id "foo"}))))
  (testing "with multiples params aliasing to same key"
    (let [params {:dataset-id "foo"
                  :echo-granule-id ["G1000000002-PROV1" "G1000000003-PROV1" "G1000000004-PROV1"
                                    "G1000000005-PROV2" "G1000000006-PROV2"],
                  :updated-since ["2014-05-16T15:09:37.829Z"],
                  :campaign "E*",
                  :options {:dataset-id {:ignore-case "true"} :campaign {:pattern "true"}}
                  :exclude {:concept-id ["G1000000006-PROV2"],
                            :echo-granule-id ["G1000000006-PROV2"]
                            :echo-collection-id "C1000000002-PROV2"}}
          expected {:entry-title "foo",
                    :concept-id ["G1000000002-PROV1" "G1000000003-PROV1" "G1000000004-PROV1"
                                 "G1000000005-PROV2" "G1000000006-PROV2"],
                    :updated-since ["2014-05-16T15:09:37.829Z"],
                    :project "E*",
                    :options {:entry-title {:ignore-case "true"} :project {:pattern "true"}}
                    :exclude
                    {:concept-id ["G1000000006-PROV2" "G1000000006-PROV2" "C1000000002-PROV2"]}}]
      (is (= expected
             (lp/replace-parameter-aliases params))))))


(deftest parameter->condition-test
  (testing "String conditions"
    (testing "with one value"
      (is (= (q/string-condition :entry-title "bar")
             (p/parameter->condition nil :collection :entry-title "bar" nil))))
    (testing "with multiple values"
      (is (= (q/string-conditions :entry-title ["foo" "bar"])
             (p/parameter->condition nil :collection :entry-title ["foo" "bar"] nil))))
    (testing "with multiple values case sensitive"
      (is (= (q/string-conditions :entry-title ["foo" "bar"] true false :or)
             (p/parameter->condition nil :collection :entry-title ["foo" "bar"]
                                     {:entry-title {:ignore-case "false"}}))))
    (testing "with multiple values pattern"
      (is (= (gc/or-conds [(q/string-condition :entry-title "foo" false true)
                           (q/string-condition :entry-title "bar" false true)])
             (p/parameter->condition nil :collection :entry-title ["foo" "bar"]
                                     {:entry-title {:pattern "true"}}))))
    (testing "with multiple values and'd"
      (is (= (gc/and-conds [(q/string-condition :entry-title "foo" false false)
                            (q/string-condition :entry-title "bar" false false)])
             (p/parameter->condition nil :collection :entry-title ["foo" "bar"]
                                     {:entry-title {:and "true"}}))))
    (testing "case-insensitive"
      (is (= (q/string-condition :entry-title "bar" false false)
             (p/parameter->condition nil :collection :entry-title "bar"
                                     {:entry-title {:ignore-case "true"}}))))
    (testing "pattern"
      (is (= (q/string-condition :entry-title "bar*" false false)
             (p/parameter->condition nil :collection :entry-title "bar*" {})))
      (is (= (q/string-condition :entry-title "bar*" false true)
             (p/parameter->condition nil :collection :entry-title "bar*"
                                     {:entry-title {:pattern "true"}}))))))

(deftest parameters->query-test
  (testing "Empty parameters"
    (is (= (q/query {:concept-type :collection})
           (p/parse-parameter-query nil :collection {}))))
  (testing "option map aliases are corrected"
    (is (= (q/query {:concept-type :collection
                     :condition (q/string-condition :entry-title "foo" false false)})
           (p/parse-parameter-query nil :collection {:entry-title ["foo"]
                                                     :options {:entry-title {:ignore-case "true"}}}))))
  (testing "with one condition"
    (is (= (q/query {:concept-type :collection
                     :condition (q/string-condition :entry-title "foo")})
           (p/parse-parameter-query nil :collection {:entry-title ["foo"]}))))
  (testing "with multiple conditions"
    (is (= (q/query {:concept-type :collection
                     :condition (gc/and-conds [(q/string-condition :entry-title "foo")
                                               (q/string-condition :provider "bar")])})
           (p/parse-parameter-query nil :collection {:entry-title ["foo"] :provider "bar"})))))

(deftest parse-sort-key-test
  (testing "no sort key"
    (is (= nil (p/parse-sort-key nil lp/param-aliases))))
  (testing "single field default order"
    (is (= [{:field :entry-title
             :order :asc}]
           (p/parse-sort-key "entry-title" lp/param-aliases))))
  (testing "single field with alias"
    (is (= [{:field :entry-title
             :order :asc}]
           (p/parse-sort-key "dataset-id" lp/param-aliases))))
  (testing "single field default ascending"
    (is (= [{:field :entry-title
             :order :asc}]
           (p/parse-sort-key "+entry-title" lp/param-aliases))))
  (testing "single field descending"
    (is (= [{:field :entry-title
             :order :desc}]
           (p/parse-sort-key "-entry-title" lp/param-aliases))))
  (testing "multiple fields"
    (is (= [{:field :short-name
             :order :asc}
            {:field :entry-title
             :order :desc}]
           (p/parse-sort-key ["short-name" "-entry-title"] lp/param-aliases)))))

(deftest handle-legacy-condtions
  (testing "legacy equator date crossing"
    (are [params params-with-legacy] (= params (lp/process-legacy-multi-params-conditions :granule params-with-legacy))
         {:equator-crossing-date "2000-04-15T12:00:00Z,2000-04-15T12:01:00Z"} {:equator-crossing-start-date "2000-04-15T12:00:00Z"
                                                                               :equator-crossing-end-date "2000-04-15T12:01:00Z"}
         {:equator-crossing-date "2000-04-15T12:00:00Z,"} {:equator-crossing-start-date "2000-04-15T12:00:00Z"}
         {:equator-crossing-date ",2000-04-15T12:01:00Z"} {:equator-crossing-end-date "2000-04-15T12:01:00Z"}))
  (testing "legacy range conditions"
    (are [params params-with-legacy] (= params (lp/process-legacy-multi-params-conditions :granule params-with-legacy))
         {:some-param "ABC,XYZ"} {:some-param {:min-value "ABC" :max-value "XYZ"}}
         {:some-param "ABC,"} {:some-param {:min-value "ABC"}}
         {:some-param ",XYZ"} {:some-param {:max-value "XYZ"}}
         {:some-param "ABC"} {:some-param {:value "ABC"}})))

(deftest short-name->provider-ids
  (are2 [matches case-sensitive? short-name]
        (let [providers [{:provider-id "PROV1" :short-name "Provider 1"}
                         {:provider-id "PROV2" :short-name "PROVIDER 1"}
                         {:provider-id "PROV3" :short-name "Test provider with special chars :) ? * \\ [] {} ☺, etc."}
                         {:provider-id "PROV4" :short-name "Not Important"}]]
          (= matches (#'psn/short-name->provider-ids providers case-sensitive? short-name)))

        "exact match, case sensitive"
        ["PROV1"] true "Provider 1"

        "exact match, case insensitive"
        ["PROV1" "PROV2"] false "Provider 1"

        "exact match, special chars"
        ["PROV3"] true "Test provider with special chars :) ? * \\ [] {} ☺, etc."

        "no match"
        [] true "NoMatch"))
