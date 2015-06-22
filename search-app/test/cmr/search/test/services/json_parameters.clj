(ns cmr.search.test.services.json-parameters
  (:require [clojure.test :refer :all]
            [cmr.search.services.parameters.conversion :as p]
            [cmr.search.models.query :as q]
            [cmr.search.models.group-query-conditions :as gc]
            [cmr.search.services.parameters.legacy-parameters :as lp]
            [cmr.search.services.json-parameters.conversion :as jp]))

(deftest json-parameters->query-test
  (testing "Empty parameters"
    (are [empty-string] (= (q/query {:concept-type :collection})
                           (jp/json-parameters->query :collection {} empty-string))
         "{}" "" nil))
  #_(testing "option map aliases are corrected"
    (is (= (q/query {:concept-type :collection
                     :condition (q/string-condition :entry-title "foo" false false)})
           (jp/json-parameters->query :collection {:entry-title ["foo"]
                                             :options {:entry-title {:ignore-case "true"}}}))))
  #_(testing "with one condition"
    (is (= (q/query {:concept-type :collection
                     :condition (q/string-condition :entry-title "foo")})
           (jp/json-parameters->query :collection {:entry-title ["foo"]}))))
  #_(testing "with multiple conditions"
    (is (= (q/query {:concept-type :collection
                     :condition (gc/and-conds [(q/string-condition :provider "bar")
                                              (q/string-condition :entry-title "foo")])})
           (jp/json-parameters->query :collection {:entry-title ["foo"] :provider "bar"})))))

; (deftest parameter->condition-test
;   (testing "String conditions"
;     (testing "with one value"
;       (is (= (q/string-condition :entry-title "bar")
;              (p/parameter->condition :collection :entry-title "bar" nil))))
;     (testing "with multiple values"
;       (is (= (q/string-conditions :entry-title ["foo" "bar"])
;              (p/parameter->condition :collection :entry-title ["foo" "bar"] nil))))
;     (testing "with multiple values case sensitive"
;       (is (= (q/string-conditions :entry-title ["foo" "bar"] true false :or)
;              (p/parameter->condition :collection :entry-title ["foo" "bar"]
;                                      {:entry-title {:ignore-case "false"}}))))
;     (testing "with multiple values pattern"
;       (is (= (gc/or-conds [(q/string-condition :entry-title "foo" false true)
;                           (q/string-condition :entry-title "bar" false true)])
;              (p/parameter->condition :collection :entry-title ["foo" "bar"]
;                                      {:entry-title {:pattern "true"}}))))
;     (testing "with multiple values and'd"
;       (is (= (gc/and-conds [(q/string-condition :entry-title "foo" false false)
;                           (q/string-condition :entry-title "bar" false false)])
;              (p/parameter->condition :collection :entry-title ["foo" "bar"]
;                                      {:entry-title {:and "true"}}))))
;     (testing "case-insensitive"
;       (is (= (q/string-condition :entry-title "bar" false false)
;              (p/parameter->condition :collection :entry-title "bar"
;                                      {:entry-title {:ignore-case "true"}}))))
;     (testing "pattern"
;       (is (= (q/string-condition :entry-title "bar*" false false)
;              (p/parameter->condition :collection :entry-title "bar*" {})))
;       (is (= (q/string-condition :entry-title "bar*" false true)
;              (p/parameter->condition :collection :entry-title "bar*"
;                                      {:entry-title {:pattern "true"}}))))))
