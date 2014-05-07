(ns cmr.search.test.services.parameters
  (:require [clojure.test :refer :all]
            [cmr.search.services.parameters :as p]
            [cmr.search.models.query :as q]))

(deftest replace-parameter-aliases-test
  (testing "with options"
    (is (= {:entry-title "foo"
            :options {:entry-title {:ignore-case "true"}}}
           (p/replace-parameter-aliases
             {:dataset-id "foo"
              :options {:dataset-id {:ignore-case "true"}}}))))
  (testing "with no options"
    (is (= {:entry-title "foo" :options nil}
           (p/replace-parameter-aliases {:dataset-id "foo"})))))

(deftest parameter->condition-test
  (testing "String conditions"
    (testing "with one value"
      (is (= (q/string-condition :entry-title "bar")
             (p/parameter->condition :collection :entry-title "bar" nil))))
    (testing "with multiple values"
      (is (= (q/or-conds [(q/string-condition :entry-title "foo")
                          (q/string-condition :entry-title "bar")])
             (p/parameter->condition :collection :entry-title ["foo" "bar"] nil))))
    (testing "case-insensitive"
      (is (= (q/string-condition :entry-title "bar" false false)
             (p/parameter->condition :collection :entry-title "bar" {:entry-title {:ignore-case "true"}}))))
    (testing "pattern"
      (is (= (q/string-condition :entry-title "bar*" true false)
             (p/parameter->condition :collection :entry-title "bar*" {})))
      (is (= (q/string-condition :entry-title "bar*" true true)
             (p/parameter->condition :collection :entry-title "bar*" {:entry-title {:pattern "true"}}))))))

(deftest parameters->query-test
  (testing "Empty parameters"
    (is (= (q/query {:concept-type :collection})
           (p/parameters->query :collection {}))))
  (testing "option map aliases are corrected"
    (is (= (q/query {:concept-type :collection
                     :condition (q/string-condition :entry-title "foo" false false)})
           (p/parameters->query :collection {:entry-title ["foo"]
                                             :options {:entry-title {:ignore-case "true"}}}))))
  (testing "with one condition"
    (is (= (q/query {:concept-type :collection
                     :condition (q/string-condition :entry-title "foo")})
           (p/parameters->query :collection {:entry-title ["foo"]}))))
  (testing "with multiple conditions"
    (is (= (q/query {:concept-type :collection
                     :condition (q/and-conds [(q/string-condition :provider "bar")
                                              (q/string-condition :entry-title "foo")])})
           (p/parameters->query :collection {:entry-title ["foo"] :provider "bar"})))))


