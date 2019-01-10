(ns cmr.nlp.tests.unit.core
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.nlp.core :as nlp]
    [cmr.nlp.tests.data :as test-data])
  (:import
    (java.util Calendar)))

(deftest find-locations
  (is (= ["Lake Superior"]
         (nlp/find-locations test-data/spatio-temporal-tokens-1)))
  (is (= ["Alaska"]
         (nlp/find-locations test-data/spatio-temporal-tokens-2))))

(deftest find-dates
  (is (= ["last week"]
         (nlp/find-dates test-data/spatio-temporal-tokens-1)))
  (is (= ["20 years ago"]
         (nlp/find-dates test-data/spatio-temporal-tokens-2))))

(deftest extract-dates
  (let [now (first (nlp/extract-dates "now"))]
    (testing "20 years ago ..."
      (is (= 20
             (- (.getYear now)
                (.getYear (first (nlp/extract-dates test-data/spatio-temporal-sentence-2)))))))
    (testing "Relative year differences ..."
      (let [[d1 d2] (nlp/extract-dates test-data/relative-temporals-sentence-1)]
        (is (= 1
               (- (.getYear d2)
                  (.getYear d1))))))
    (testing "Relative future days differences ..."
      (let [d (first (nlp/extract-dates test-data/relative-future-temporals-sentence-1))]
        (is (> 7
               (- (.getDay d)
                  (.getDay now))))
        (is (.after d now))))))
