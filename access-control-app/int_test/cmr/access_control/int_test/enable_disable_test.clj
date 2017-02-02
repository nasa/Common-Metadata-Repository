(ns cmr.access-control.int-test.enable-disable-test
  "CMR Ingest Enable/Disable endpoint test"
  (:require
    [cheshire.core :as json]
    [clj-http.client :as client]
    [clojure.string :as str]
    [clojure.test :refer :all]
    [cmr.access-control.int-test.fixtures :as fixtures]
    [cmr.access-control.test.util :as u]
    [cmr.common.util :as util :refer [are3]]
    [cmr.mock-echo.client.echo-util :as e]
    [cmr.transmit.access-control :as ac]
    [cmr.transmit.config :as transmit-config]
    [cmr.transmit.metadata-db2 :as mdb]))


(use-fixtures :each
              (fixtures/int-test-fixtures)
              (fixtures/reset-fixture {"prov1guid" "PROV1" "prov2guid" "PROV2"}
                                      ["user1" "user2" "user3" "user4" "user5"])
              (fixtures/grant-all-group-fixture ["prov1guid" "prov2guid"])
              (fixtures/grant-all-acl-fixture))

(def system-acl
  "A system ingest management acl that grants read and update to guest users"
  {:group_permissions [{:user_type "guest"
                        :permissions ["read" "update"]}]
   :system_identity {:target "INGEST_MANAGEMENT_ACL"}})

(def provider-acl
  {:legacy_guid "ABCD-EFG-HIJK-LMNOP"
   :group_permissions [{:group_id "admins"
                        :permissions ["read" "update"]}]
   :provider_identity {:provider_id "PROV1"
                       :target "INGEST_MANAGEMENT_ACL"}})

(def catalog-item-acl
  {:group_permissions [{:user_type "guest"
                        :permissions ["create" "delete"]}]
   :catalog_item_identity {:name "A Catalog Item ACL"
                           :provider_id "PROV1"
                           :collection_applicable true}})

(def post-options
  "Options map to pass on POST requests to enable/disable writes in access control."
  {:headers {transmit-config/token-header (transmit-config/echo-system-token)}})

(deftest enable-disable-enable-write-acl
  (let [token (e/login (u/conn-context) "admin")
        resp (ac/create-acl (u/conn-context) system-acl {:raw? true :token token})
        concept-id (get-in resp [:body :concept_id])
        ;; Create a second ACL to test update/delete after disable
        resp2 (ac/create-acl (u/conn-context) provider-acl {:token token})
        concept-id2 (:concept_id resp2)]

    (testing "save and delete acl works before disable"
      ;; check save response
      (is (= 200 (:status resp)))
      ;; test update
      (is (= 200 (:status (ac/update-acl (u/conn-context) concept-id catalog-item-acl {:token token :raw? true}))))
      ;; test delete
      (is (= 200 (:status (ac/delete-acl (u/conn-context) concept-id {:token token :raw? true})))))

    ;; disable writes for access control service
    (u/disable-access-control-writes post-options)

    (testing "save and delete acl fails after disable"
      (let [token (e/login (u/conn-context) "admin")
            resp (ac/create-acl (u/conn-context) system-acl {:raw? true :token token})
            concept-id (get-in resp [:body :concept_id])]
        ;; check save response
        (is (= 503 (:status resp)))
        ;; test update
        (is (= 503 (:status (ac/update-acl (u/conn-context) concept-id2 provider-acl {:token token :raw? true}))))
        ;; test delete
        (is (= 503 (:status (ac/delete-acl (u/conn-context) concept-id2 {:token token :raw? true}))))))))

