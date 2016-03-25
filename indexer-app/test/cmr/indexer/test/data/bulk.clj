(ns cmr.indexer.test.data.bulk
  "Tests for bulk indexing"
  (:require [clojure.test :refer :all]
            [cmr.indexer.data.bulk :as bulk]))

(deftest bulk-index
  (testing "interleaved deletes with indexing"
    (let [doc1 {:id "A" :dumy-field "field-value1" :_index "1" :_type "doc" :_version 1
                :_version_type "integer"}
          doc2 {:id "B" :dummy-field "field-value2" :_index "1" :_type "doc" :_version 2 :_version_type "integer"}
          doc3 {:id "C" :_index "2" :_type "doc" :_version 3 :_version_type "integer"}
          doc4 {:id "D" :_index "2" :deleted true :_type "doc" :_version 2 :_version_type "integer"}
          doc5 {:id "E" :_index "3" :_type "doc" :_version 1 :_version_type "integer"}
          all-docs [doc1 doc2 doc3 doc4 doc5]
          expected [{"index"
                     {:_version_type "integer",
                      :_version 1,
                      :_type "doc",
                      :_index "1"}}
                    {:dumy-field "field-value1", :id "A"}
                    {"index"
                     {:_version_type "integer",
                      :_version 2,
                      :_type "doc",
                      :_index "1"}}
                    {:dummy-field "field-value2", :id "B"}
                    {"index"
                     {:_version_type "integer",
                      :_version 3,
                      :_type "doc",
                      :_index "2"}}
                    {:id "C"}
                    {"delete"
                     {:_version_type "integer",
                      :_version 2,
                      :_type "doc",
                      :_index "2"}}
                    {"index"
                     {:_version_type "integer",
                      :_version 1,
                      :_type "doc",
                      :_index "3"}}
                    {:id "E"}]]
      (is (= expected
             (bulk/create-bulk-index-operations all-docs))))))
