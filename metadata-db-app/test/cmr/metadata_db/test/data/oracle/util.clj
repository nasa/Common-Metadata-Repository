(ns cmr.metadata-db.test.data.oracle.util
  (:require
    [clojure.test :refer :all]
    [cmr.metadata-db.data.util :as util]))

(deftest validate-table-name-test
  (testing "correct table name given"
    (are [table-name] (= (util/validate-table-name table-name))
                      "table_name", "123table_name", "table_name_"))
  (testing "incorrect table name given"
    (are [table-name] (thrown? Exception (util/validate-table-name table-name))
                      "table-name", "; -- comment", "; DELETE")))
