(ns cmr.metadata-db.test.data.oracle.collection-table
  (:require
    [clojure.test :refer :all]
    [cmr.metadata-db.data.oracle.collection-table :as ct]))

(deftest collection-constraint-sql-false-test)

(deftest collection-constraint-sql-true-test)

(deftest create-collection-indexes-false-test)

(deftest create-collection-indexes-true-test)
