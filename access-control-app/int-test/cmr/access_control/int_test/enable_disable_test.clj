(ns cmr.access-control.int-test.enable-disable-test
  "CMR Access Control Enable/Disable endpoint test"
  (:require
    [clojure.test :refer :all]
    [cmr.access-control.int-test.fixtures :as fixtures]
    [cmr.access-control.test.util :as u]
    [cmr.mock-echo.client.echo-util :as e]
    [cmr.transmit.access-control :as ac]
    [cmr.transmit.config :as transmit-config]))

(use-fixtures :each
              (fixtures/int-test-fixtures)
              (fixtures/reset-fixture {"prov1guid" "PROV1" "prov2guid" "PROV2"}
                                      ["user1" "user2" "user3" "user4" "user5"])
              (fixtures/grant-all-group-fixture ["PROV1" "PROV2"])
              (fixtures/grant-all-acl-fixture))

(def system-acl
  "A system ingest management acl that grants read and update to guest users"
  {:group_permissions [{:user_type "guest"
                        :permissions ["read" "update"]}]
   :system_identity {:target "INGEST_MANAGEMENT_ACL"}})

(def provider-acl
  {:legacy_guid "ABCD-EFG-HIJK-LMNOP"
   :group_permissions [{:group_id "AG001-CMR"
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

(defn get-token
  "Get a token for performing access control crud operations."
  [username]
  (e/login (u/conn-context) username))


(deftest enable-disable-re-enable-write-acl
  (let [token (get-token "admin")
        first-resp (ac/create-acl (u/conn-context) system-acl {:raw? true :token token})
        concept-id (get-in first-resp [:body :concept_id])
        ;; Create a second ACL to test update/delete after disable
        second-resp (ac/create-acl (u/conn-context) provider-acl {:token token})
        concept-id2 (:concept_id second-resp)]

    (testing "save, udate, and delete acl works before disable"
      ;; check save response
      (is (= 200 (:status first-resp)))
      ;; test update
      (is (= 200 (:status (ac/update-acl (u/conn-context) concept-id catalog-item-acl {:token token :raw? true}))))
      ;; test delete
      (is (= 200 (:status (ac/delete-acl (u/conn-context) concept-id {:token token :raw? true})))))

    ;; disable writes for access control service
    (u/disable-access-control-writes post-options)

    (testing "save, update, and delete acl fails after disable"
      (let [resp (ac/create-acl (u/conn-context) system-acl {:raw? true :token token})
            concept-id (get-in resp [:body :concept_id])]
        ;; check save response
        (is (= 503 (:status resp)))
        ;; test update
        (is (= 503 (:status (ac/update-acl (u/conn-context) concept-id2 provider-acl {:token token :raw? true}))))
        ;; test delete
        (is (= 503 (:status (ac/delete-acl (u/conn-context) concept-id2 {:token token :raw? true}))))))

    ;; re-enable writes for access control service
    (u/enable-access-control-writes post-options)

    (testing "save, upate, and delete acl works after re-enable"
      (let [resp (ac/create-acl (u/conn-context) system-acl {:raw? true :token token})
            concept-id (get-in resp [:body :concept_id])]
        ;; check save response
        (is (= 200 (:status resp)))
        ;; test update
        (is (= 200 (:status (ac/update-acl (u/conn-context) concept-id2 provider-acl {:token token :raw? true}))))
        ;; test delete
        (is (= 200 (:status (ac/delete-acl (u/conn-context) concept-id2 {:token token :raw? true}))))))))

(deftest enable-disable-re-enable-write-group
  (let [token (get-token "admin")
        group (u/make-group)
        {:keys [status concept_id revision_id]} (u/create-group token group)
        ;; create a second group to use to test updates/deletes after disable
        group2 (u/make-group {:name "group2" :members ["user1" "user2"]})
        second-resp (u/create-group token group2)
        concept-id2 (:concept_id second-resp)]
    (testing "save, udate, and delete group works before disable"
      ;; check save response
      (is (= 200 status))
      ;; test update
      (is (= 200 (:status (u/update-group token concept_id {:name "Updated" :description "Updated"}))))
      ;; test delete
      (is (= 200 (:status (u/delete-group token concept_id)))))

    ;; disable writes for access control service
    (u/disable-access-control-writes post-options)

    (testing "save, update, and delete group fails after disable"
      (let [group3 (u/make-group {:name "group3" :members ["user1" "user2" "user3"]})
            {:keys [status]} (u/create-group token group3 {:allow-failure? true})]
        ;; check save response
        (is (= 503 status))
        ;; test update
        (is (= 503 (:status (u/update-group token concept-id2 {:name "Updated3" :description "Updated3"}))))
        ;; test delete
        (is (= 503 (:status (u/delete-group token concept-id2 {:allow-failure? true}))))))

    ;; re-eneable writes for access control service
    (u/enable-access-control-writes post-options)

    (testing "save, update, and delete group succeeds after re-enable"
     (let [group3 (u/make-group {:name "group4" :members ["user1" "user5"]})
            {:keys [status]} (u/create-group token group3)]
        ;; check save response
        (is (= 200 status))
        ;; test update
        (is (= 200 (:status (u/update-group token concept-id2 {:name "Updated3" :description "Updated3"}))))
        ;; test delete
        (is (= 200 (:status (u/delete-group token concept-id2 {:allow-failure? true}))))))))
