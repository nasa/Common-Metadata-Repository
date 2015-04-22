(ns cmr.indexer.test.data.concepts.science-keyword
  "Tests for science keywords"
  (:require [clojure.test :refer :all]
            [cmr.indexer.data.concepts.science-keyword :as sk]))

(deftest flatten-science-keywords-test
  (let [science-keyword1 {:category "category"
                          :topic "topic"
                          :term "term"}
        science-keyword2 {:category "category"
                          :topic "topic"
                          :term "another-term"}
        science-keyword3 {:cateogry "category"
                          :topic "another-topic"}
        collection {:science-keywords [science-keyword1
                                       science-keyword2
                                       science-keyword3]}
        flattened-science-keywords (sk/flatten-science-keywords collection)]
    (is (= (count flattened-science-keywords) 5))
    (is (=  (set flattened-science-keywords)
           #{"category" "topic" "term" "another-term" "another-topic"}))))