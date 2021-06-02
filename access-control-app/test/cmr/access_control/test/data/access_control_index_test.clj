(ns cmr.access-control.test.data.access-control-index-test
  (:require
   [clojure.test :refer :all]
   [cmr.access-control.data.access-control-index :as a]))


(deftest acl-display-name
  (testing "system identity display name"
    (is (= "System - FOO"
           (a/acl->display-name {:system-identity {:target "FOO"}}))))
  (testing "single instance identity display name"
    (is (= "Group - AG1234-CMR"
           (a/acl->display-name {:single-instance-identity {:target-id "AG1234-CMR"}}))))
  (testing "provider identity display name"
    (is (= "Provider - PROV1 - FOO"
           (a/acl->display-name {:provider-identity {:target "FOO"
                                                     :provider-id "PROV1"}}))))
  (testing "catalog identity display name"
    (is (= "All Collections"
           (a/acl->display-name {:catalog-item-identity {:name "All Collections"}})))))

(deftest acl-identity-type
  (testing "system identity identity type"
    (is (= "System"
           (a/acl->identity-type {:system-identity {:target "FOO"}}))))
  (testing "single instance identity identity type"
    (is (= "Group"
           (a/acl->identity-type {:single-instance-identity {:target-id "AG1234-CMR"}}))))
  (testing "provider identity identity type"
    (is (= "Provider"
           (a/acl->identity-type {:provider-identity {:target "FOO"
                                                      :provider-id "PROV1"}}))))
  (testing "catalog identity identity type"
    (is (= "Catalog Item"
           (a/acl->identity-type {:catalog-item-identity {:name "All Collections"}})))))
