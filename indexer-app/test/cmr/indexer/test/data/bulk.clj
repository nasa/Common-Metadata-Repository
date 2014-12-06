(ns cmr.indexer.test.data.bulk
  "Tests for bulk indexing"
  (:require [clojure.test :refer :all]
            [cmr.indexer.data.bulk :as bulk]))

(deftest bulk-index
  (testing "interleaved deletes with indexing"
    (let [doc1 {:id "A" :_index "A1"}
          doc2 {:id "B" :_index "B1"}
          doc3 {:id "C" :_index "C1"}
          doc4 {:id "D" :_index "D1" :deleted true}
          doc5 {:id "E" :_index "E1"}
          all [doc1 doc2 doc3 doc4 doc5]
          expected [{"index" {:_index "A1"}}
                    {:id "A"}
                    {"index" {:_index "B1"}}
                    {:id "B"}
                    {"index" {:_index "C1"}}
                    {:id "C"}
                    {"delete" {:_index "D1"}}
                    {"index" {:_index "E1"}}
                    {:id "E"}]]
      (is (= expected
             (bulk/bulk-index all))))))