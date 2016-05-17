(ns cmr.access-control.int-test.acl-crud-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.access-control.int-test.fixtures :as fixtures]
            [clj-http.client :as client]
            [cmr.access-control.test.util :as u]
            [cmr.transmit.access-control :as ac]
            [cheshire.core :as json]))

(use-fixtures :each
              (fixtures/int-test-fixtures)
              (fixtures/reset-fixture {"prov1guid" "PROV1" "prov2guid" "PROV2"})
              (fixtures/grant-all-group-fixture ["prov1guid" "prov2guid"]))

(def system-acl
  {:group_permissions {:user_type "guest"
                       :permissions ["create" "delete"]}
   :system_identity {:target "TAG_GROUP"}})

(def provider-acl
  {:group_permissions {:group_id "admins"
                       :permissions ["read" "create"]}
   :provider_identity {:provider_id "PROV1"
                       :target "INGEST_MANAGEMENT_ACL"}})

(def catalog-item-acl
  {:group_permissions {:user_type "guest"
                       :permissions ["create" "delete"]}
   :catalog_item_identity {:name "A Catalog Item ACL"
                           :provider_id "PROV1"
                           :collection_identifier {:entry_titles ["foo" "bar"]}}})

(deftest create-acl-test
  (let [token (e/login (u/conn-context) "admin")
        resp (ac/create-acl (u/conn-context) system-acl {:token token})]
    (is (re-find #"^ACL.*" (:concept_id resp)))
    (is (= 1 (:revision_id resp)))))

(deftest create-acl-errors-test
  (let [token (e/login (u/conn-context) "admin")]
    (are [re acl]
      (thrown-with-msg? Exception re (ac/create-acl (u/conn-context) acl {:token token}))

      #"object has missing required properties.*group_permissions"
      (dissoc system-acl :group_permissions)

      #"group_permissions object has missing required properties"
      (assoc system-acl :group_permissions {})

      #"system_identity object has missing required properties"
      (update-in system-acl [:system_identity] dissoc :target))))

(deftest create-duplicate-acl-test
  (let [token (e/login (u/conn-context) "admin")]
    (testing "system ACL"
      (is (= 1 (:revision_id (ac/create-acl (u/conn-context) system-acl {:token token}))))
      (is (thrown-with-msg? Exception #"ACL already exists"
        (ac/create-acl (u/conn-context) system-acl {:token token}))))
    (testing "provider ACL"
      (is (= 1 (:revision_id (ac/create-acl (u/conn-context) provider-acl {:token token}))))
      (is (thrown-with-msg? Exception #"ACL already exists"
        (ac/create-acl (u/conn-context) provider-acl {:token token}))))
    (testing "catalog item ACL"
      (is (= 1 (:revision_id (ac/create-acl (u/conn-context) catalog-item-acl {:token token}))))
      (is (thrown-with-msg? Exception #"ACL already exists"
        (ac/create-acl (u/conn-context) catalog-item-acl {:token token}))))))

(deftest get-acl-test
  (let [token (e/login (u/conn-context) "admin")
        concept-id (:concept_id (ac/create-acl (u/conn-context) system-acl {:token token}))]
    (is (= system-acl (ac/get-acl (u/conn-context) concept-id {:token token})))))
