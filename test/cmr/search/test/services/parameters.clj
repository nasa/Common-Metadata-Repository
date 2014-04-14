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

(deftest parameter->condition-test
  (testing "String conditions"
    (testing "with one value"
      (is (= (q/string-condition :entry_title "bar")
             (p/parameter->condition :collection :entry_title "bar" nil))))
    (testing "with multiple values"
      (is (= (q/or-conds [(q/string-condition :entry_title "foo")
                          (q/string-condition :entry_title "bar")])
             (p/parameter->condition :collection :entry_title ["foo" "bar"] nil))))
    (testing "case-insensitive"
      (is (= (q/string-condition :entry_title "bar" false false)
             (p/parameter->condition :collection :entry_title "bar" {:entry_title {:ignore_case "true"}}))))
    (testing "pattern"
      (is (= (q/string-condition :entry_title "bar*" true false)
             (p/parameter->condition :collection :entry_title "bar*" {})))
      (is (= (q/string-condition :entry_title "bar*" true true)
             (p/parameter->condition :collection :entry_title "bar*" {:entry_title {:pattern "true"}}))))))

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


