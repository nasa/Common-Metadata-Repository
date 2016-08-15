(ns cmr.common.test.parameter-parser
  "Contains tests for parameter-parser"
  (:require [clojure.test :refer :all]
            [cmr.common.parameter-parser :as p]
            [cmr.common.services.messages :as msg])
  (:import clojure.lang.ExceptionInfo))

(deftest parse-numeric-range
  (testing "valid ranges"
    (are [range-str range-map] (= range-map (p/numeric-range-parameter->map range-str))
         "1" {:value 1.0}
         "-1" {:value -1.0}
         "1,2" {:min-value 1.0 :max-value 2.0}
         "2," {:min-value 2.0}
         ",3" {:max-value 3.0}
         "0.5,2" {:min-value 0.5 :max-value 2.0}))
  (testing "invalid ranges"
    (are [range-str]
         (try
           (p/numeric-range-parameter->map range-str)
           false
           (catch ExceptionInfo e
             (let [{:keys [type errors]} (ex-data e)]
               (and (= type :invalid-data)
                    (= 1 (count errors))
                    (= (msg/invalid-numeric-range-msg range-str) (first errors))))))
         "A"
         "A,10"
         "1,B"
         "C,"
         ",D")))

(deftest validate-numeric-range
  (testing "valid ranges"
    (are [range-str errors] (= errors (p/numeric-range-string-validation range-str))
         "1" []
         "-1" []
         "1,2" []
         "2," []
         ",3" []
         "0.5,2" []))
  (testing "invalid ranges"
    (are [range-str errors] (= errors (p/numeric-range-string-validation range-str))
         "A" [(msg/invalid-msg java.lang.Double "A")]
         "A,10" [(msg/invalid-msg java.lang.Double "A")]
         "1,B" [(msg/invalid-msg java.lang.Double "B")]
         "C," [(msg/invalid-msg java.lang.Double "C")]
         ",D" [(msg/invalid-msg java.lang.Double "D")]
         "," [(msg/invalid-numeric-range-msg ",")])))
