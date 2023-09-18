(ns cmr.metadata-db.test.data.oracle.util
  (:require
    [clojure.test :refer :all]
    [cmr.metadata-db.data.util :as data_util]
    [cmr.common.util :as common_util :refer [are3]]))

(deftest validate-table-name-test
  (testing "correct table name given"
    (are3 [table-name result]
          (is (= nil (data_util/validate-table-name table-name)))

          "valid table name with only underscore"
          "table_name"
          true

          "valid table name with numbers and underscores"
          "123table_name"
          true

          "valid table name with multiple underscores"
          "table_name_"
          true))
  (testing "incorrect table name given"
    (are3 [table-name result]
          (is (thrown? Exception (data_util/validate-table-name table-name)))

          "invalid table name"
          "table-name"
          true

          "invalid table name 2"
          "; -- comment"
          true

          "invalid table name 3"
          "; DELETE"
          true)))
