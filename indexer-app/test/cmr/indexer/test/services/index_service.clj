(ns cmr.indexer.test.services.index-service
  "Tests for index service"
  (:require
   [clojure.test :refer :all]
   [cmr.indexer.services.index-service :as index-svc]))

(deftest index-concept-invalid-input-test
  (testing "invalid input"
    (are [concept-id revision-id]
         (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Concept-id .* and revision-id .* cannot be null"
           (index-svc/index-concept-by-concept-id-revision-id {} concept-id revision-id true))

         "C123-PROV1" nil
         nil 1
         nil nil)))

(deftest anti-value-test
  (are [doc result]
       (is (= result (index-svc/anti-value-suggestion? doc)))

       {:value "not"} false
       {:value "Nothofagus"} false
       {:value "Notothenioids"} false

       {:value "none"} true
       {:value "not applicable"} true
       {:value "not provided"} true)

  (testing "null values don't fail indexing"
    (is (= [false
            true
            true
            true
            true]
           (map #(index-svc/anti-value-suggestion? %)
                [{:value "ice-sat"}
                 {:value nil}
                 {:value "none"}
                 {:value "not applicable"}
                 {:value "NOT PROVIDED"}])))))
