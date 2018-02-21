(ns cmr.indexer.test.data.concepts.collection.science-keyword
  "Tests for science keywords"
  (:require
   [clojure.test :refer :all]
   [cmr.indexer.data.concepts.collection.science-keyword :as sk]))

(deftest flatten-science-keywords-test
  (let [science-keyword1 {:Category "category"
                          :Topic "topic"
                          :Term "term"}
        science-keyword2 {:Category "category"
                          :Topic "topic"
                          :Term "another-term"}
        science-keyword3 {:Category "category"
                          :Topic "another-topic"}
        collection {:ScienceKeywords [science-keyword1
                                       science-keyword2
                                       science-keyword3]}
        actual-keywords (sk/flatten-science-keywords collection)
        expected-keywords #{"category" "topic" "term" "another-term" "another-topic"}]
    (is (= (count expected-keywords) (count actual-keywords)))
    (is (= expected-keywords (set actual-keywords)))))
