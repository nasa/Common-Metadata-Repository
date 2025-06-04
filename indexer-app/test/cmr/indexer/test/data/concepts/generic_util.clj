(ns cmr.indexer.test.data.concepts.generic-util
  "Functions for testing cmr.indexer.data.concepts.generic namespace"
  (:require
   [clojure.test :refer [deftest is]]))

(deftest test-only-elastic-preferences
  (let [only-elastic-preferences @#'cmr.indexer.data.concepts.generic-util/only-elastic-preferences
        input-config {:Indexes [{:Description "Identifier as Id"
                                 :Field ".Identifier"
                                 :Name "Id"
                                 :Mapping "string"}
                                {:Description "Identifier as Identifier"
                                 :Type "elastic"
                                 :Field ".Identifier"
                                 :Name "Identifier"
                                 :Mapping "string"}
                                {:Description "Id for Graph-DB"
                                 :Type "graph"
                                 :Name "id"}]
                      :Generic {:Name "Visualization" :Version "1.1.0"}}
        result (only-elastic-preferences (:Indexes input-config))
        ;; Create a test function to search for a description to shorten code latter.
        description-count (fn [description result]
                            (count (filter #(= (:Description %) description) result)))]
    (is (= 2 (count result)) "Number returned should be 2")
    (is (= 1 (description-count "Identifier as Id" result)) "Look for first Index")
    (is (= 1 (description-count "Identifier as Identifier" result)) "Look for second Index")
    (is (= 0 (description-count "Id for Graph-DB" result)) "Look for Last Index")))
