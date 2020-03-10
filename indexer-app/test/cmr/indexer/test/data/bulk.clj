(ns cmr.indexer.test.data.bulk
  "Tests for bulk indexing"
  (:require
   [clojure.test :refer :all]
   [cmr.indexer.data.bulk :as bulk]))

(deftest bulk-index
  (testing "interleaved deletes with indexing"
    (let [doc1 {:id "A" :dumy-field "field-value1" :_index "1" :version 1
                :version_type "integer"}
          doc2 {:id "B" :dummy-field "field-value2" :_index "1" :version 2 :version_type "integer"}
          doc3 {:id "C" :_index "2" :version 3 :version_type "integer"}
          doc4 {:id "D" :_index "2" :deleted true :version 2 :version_type "integer"}
          doc5 {:id "E" :_index "3" :version 1 :version_type "integer"}
          all-docs [doc1 doc2 doc3 doc4 doc5]
          expected [{"index"
                     {:version_type "integer",
                      :version 1,
                      :_index "1"}}
                    {:dumy-field "field-value1", :id "A"}
                    {"index"
                     {:version_type "integer",
                      :version 2,
                      :_index "1"}}
                    {:dummy-field "field-value2", :id "B"}
                    {"index"
                     {:version_type "integer",
                      :version 3,
                      :_index "2"}}
                    {:id "C"}
                    {"delete"
                     {:version_type "integer",
                      :version 2,
                      :_index "2"}}
                    {"index"
                     {:version_type "integer",
                      :version 1,
                      :_index "3"}}
                    {:id "E"}]]
      (is (= expected
             (bulk/create-bulk-index-operations all-docs))))))
