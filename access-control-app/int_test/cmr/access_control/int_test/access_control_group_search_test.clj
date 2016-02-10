(ns cmr.access-control.int-test.access-control-group-search-test
  "Tests searching for access control groups"
    (:require [clojure.test :refer :all]
              [cmr.mock-echo.client.echo-util :as e]
              [cmr.access-control.int-test.access-control-test-util :as u]))

(use-fixtures :once (u/int-test-fixtures))
(use-fixtures :each (u/reset-fixture {"prov1guid" "PROV1", "prov2guid" "PROV2"} ["user1" "user2" "user3" "user4" "user5"]))

(defn ingest-group
  [token attributes members]
  (u/create-group-with-members token (u/make-group attributes) members))


(deftest group-search-test
  (let [token (e/login (u/conn-context) "user1")
        cmr-group1 (ingest-group token {:name "group1"} ["user1"])
        cmr-group2 (ingest-group token {:name "group2"} ["user1" "user2"])
        cmr-group3 (ingest-group token {:name "group3"} nil)
        prov1-group1 (ingest-group token {:name "group1" :provider-id "PROV1"} ["user1"])
        prov1-group2 (ingest-group token {:name "group2" :provider-id "PROV1"} ["user1" "user3"])
        prov2-group1 (ingest-group token {:name "group1" :provider-id "PROV2"} ["user2"])
        prov2-group2 (ingest-group token {:name "group2" :provider-id "PROV2"} ["user2" "user3"])]))







