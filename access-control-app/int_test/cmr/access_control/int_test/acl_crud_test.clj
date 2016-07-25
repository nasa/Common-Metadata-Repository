(ns cmr.access-control.int-test.acl-crud-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.access-control.int-test.fixtures :as fixtures]
            [clj-http.client :as client]
            [cmr.access-control.test.util :as u]
            [cmr.transmit.access-control :as ac]
            [cmr.transmit.metadata-db2 :as mdb]
            [cheshire.core :as json]
            [cmr.common.util :as util :refer [are2]]
            [cmr.transmit.config :as transmit-config]
            [cmr.access-control.int-test.permission-check-test :as perm-test]))

(use-fixtures :each
  (fixtures/int-test-fixtures)
  (fixtures/reset-fixture {"prov1guid" "PROV1" "prov2guid" "PROV2"})
  (fixtures/grant-all-group-fixture ["prov1guid" "prov2guid"]))

(def system-acl
  {:group_permissions [{:user_type "guest"
                        :permissions ["create" "delete"]}]
   :system_identity {:target "TAG_GROUP"}})

(def provider-acl
  {:legacy_guid "ABCD-EFG-HIJK-LMNOP"
   :group_permissions [{:group_id "admins"
                        :permissions ["read" "create"]}]
   :provider_identity {:provider_id "PROV1"
                       :target "INGEST_MANAGEMENT_ACL"}})

(def catalog-item-acl
  {:group_permissions [{:user_type "guest"
                        :permissions ["create" "delete"]}]
   :catalog_item_identity {:name "A Catalog Item ACL"
                           :provider_id "PROV1"
                           :collection_identifier {:entry_titles ["foo" "bar"]}}})

(deftest create-acl-test
  (let [token (e/login (u/conn-context) "admin")
        resp (ac/create-acl (u/conn-context) system-acl {:token token})]
    ;; Acceptance criteria: A concept id and revision id of the created ACL should be returned.
    (is (re-find #"^ACL.*" (:concept_id resp)))
    (is (= 1 (:revision_id resp)))))

(deftest create-acl-errors-test
  (let [token (e/login (u/conn-context) "admin")]
    (are2 [re acl]
          (thrown-with-msg? Exception re (ac/create-acl (u/conn-context) acl {:token token}))

          ;; Acceptance criteria: I receive an error if creating an ACL missing required fields.
          ;; Note: this tests a few fields, and is not exhaustive. The JSON schema handles this check.
          "Nil field value"
          #"object has missing required properties"
          (dissoc system-acl :group_permissions)

          "Empty field value"
          #"group_permissions.* object has missing required properties"
          (assoc system-acl :group_permissions [{}])

          "Missing target"
          #"system_identity object has missing required properties"
          (update-in system-acl [:system_identity] dissoc :target)

          "Acceptance criteria: I receive an error if creating an ACL with a non-existent system identity, provider identity, or single instance identity target."
          #"instance value .* not found in enum"
          (update-in system-acl [:system_identity] assoc :target "WHATEVER")

          "Value not found in enum"
          #"instance value .* not found in enum"
          (update-in provider-acl [:provider_identity] assoc :target "WHATEVER"))


    (testing "Acceptance criteria: I receive an error if creating an ACL with invalid JSON"
      (is
        (re-find #"Invalid JSON:"
                 (:body
                   (client/post (ac/acl-root-url (transmit-config/context->app-connection (u/conn-context) :access-control))
                                {:body "{\"bad-json:"
                                 :headers {"Content-Type" "application/json"
                                           "ECHO-Token" token}
                                 :throw-exceptions false})))))

    (testing "Acceptance criteria: I receive an error if creating an ACL with unsupported content type"
      (is
        (re-find #"The mime types specified in the content-type header \[application/xml\] are not supported."
                 (:body
                   (client/post
                     (ac/acl-root-url (transmit-config/context->app-connection (u/conn-context) :access-control))
                     {:body (json/generate-string system-acl)
                      :headers {"Content-Type" "application/xml"
                                "ECHO-Token" token}
                      :throw-exceptions false})))))))

(deftest create-duplicate-acl-test
  (let [token (e/login (u/conn-context) "admin")]
    (testing "system ACL"
      (is (= 1 (:revision_id (ac/create-acl (u/conn-context) system-acl {:token token}))))
      (is (thrown-with-msg? Exception #"concepts with the same acl identity were found"
                            (ac/create-acl (u/conn-context) system-acl {:token token}))))
    (testing "provider ACL"
      (is (= 1 (:revision_id (ac/create-acl (u/conn-context) provider-acl {:token token}))))
      (is (thrown-with-msg? Exception #"concepts with the same acl identity were found"
                            (ac/create-acl (u/conn-context) provider-acl {:token token}))))
    (testing "catalog item ACL"
      (is (= 1 (:revision_id (ac/create-acl (u/conn-context) catalog-item-acl {:token token}))))
      (is (thrown-with-msg? Exception #"concepts with the same acl identity were found"
                            (ac/create-acl (u/conn-context) catalog-item-acl {:token token}))))))

(deftest get-acl-test
  (let [token (e/login (u/conn-context) "admin")
        concept-id (:concept_id (ac/create-acl (u/conn-context) system-acl {:token token}))]
    ;; Acceptance criteria: A created ACL can be retrieved after it is created.
    (is (= system-acl (ac/get-acl (u/conn-context) concept-id {:token token})))
    (let [resp (ac/get-acl (u/conn-context) "NOTACONCEPTID" {:token token :raw? true})]
      (is (= 400 (:status resp)))
      (is (= ["Concept-id [NOTACONCEPTID] is not valid."] (:errors (:body resp)))))
    (let [resp (ac/get-acl (u/conn-context) "ACL999999-CMR" {:token token :raw? true})]
      (is (= 404 (:status resp)))
      (is (= ["ACL could not be found with concept id [ACL999999-CMR]"] (:errors (:body resp)))))))

(deftest update-acl-test
  (let [token (e/login (u/conn-context) "admin")
        ;; Create the ACL with one set of attributes
        {concept-id :concept_id} (ac/create-acl (u/conn-context) system-acl {:token token})
        ;; Now update it to be completely different
        resp (ac/update-acl (u/conn-context) concept-id catalog-item-acl {:token token})]
    ;; Acceptance criteria: A concept id and revision id of the updated ACL should be returned.
    (is (= concept-id (:concept_id resp)))
    (is (= 2 (:revision_id resp)))
    ;; Acceptance criteria: An updated ACL can be retrieved after it is updated.
    ;; Acceptance criteria: An updated ACL can be found via the search API with any changes.
    (is (= catalog-item-acl (ac/get-acl (u/conn-context) concept-id {:token token})))))

(deftest update-acl-errors-test
  (let [token (e/login (u/conn-context) "admin")
        {concept-id :concept_id} (ac/create-acl (u/conn-context) system-acl {:token token})]
    (are2 [re acl]
          (thrown-with-msg? Exception re (ac/update-acl (u/conn-context) concept-id acl {:token token}))

          ;; Acceptance criteria: I receive an error if creating an ACL missing required fields.
          ;; Note: this tests a few fields, and is not exhaustive. The JSON schema handles this check.
          "Nil field value"
          #"object has missing required properties"
          (dissoc system-acl :group_permissions)

          "Empty field value"
          #"group_permissions.* object has missing required properties"
          (assoc system-acl :group_permissions [{}])

          "Missing target"
          #"system_identity object has missing required properties"
          (update-in system-acl [:system_identity] dissoc :target)

          "Acceptance criteria: I receive an error if updating an ACL with an invalid combination of fields. (Only one of system, provider, single instance, or catalog item identities)"
          #"instance failed to match exactly one schema"
          (assoc system-acl :provider_identity {:provider_id "PROV1"
                                                :target "INGEST_MANAGEMENT_ACL"})

          "Acceptance criteria: I receive an error if updating an ACL with a non-existent system identity, provider identity, or single instance identity target."
          #"instance value .* not found in enum"
          (update-in system-acl [:system_identity] assoc :target "WHATEVER")

          "Value not found in enum"
          #"instance value .* not found in enum"
          (update-in provider-acl [:provider_identity] assoc :target "WHATEVER"))


    (testing "Acceptance criteria: I receive an error if updating an ACL with invalid JSON"
      (is
        (re-find #"Invalid JSON:"
                 (:body
                   (client/put
                     (ac/acl-concept-id-url
                       (transmit-config/context->app-connection (u/conn-context) :access-control)
                       concept-id)
                     {:body "{\"bad-json:"
                      :headers {"Content-Type" "application/json"
                                "ECHO-Token" token}
                      :throw-exceptions false})))))

    (testing "Acceptance criteria: I receive an error if updating an ACL with unsupported content type"
      (is
        (re-find #"The mime types specified in the content-type header \[application/xml\] are not supported."
                 (:body
                   (client/put
                     (ac/acl-concept-id-url
                       (transmit-config/context->app-connection (u/conn-context) :access-control)
                       concept-id)
                     {:body (json/generate-string system-acl)
                      :headers {"Content-Type" "application/xml"
                                "ECHO-Token" token}
                      :throw-exceptions false})))))))

(deftest update-acl-invalid-data-test
  (let [token (e/login (u/conn-context) "admin")
        {provider-concept-id :concept_id} (ac/create-acl (u/conn-context) provider-acl {:token token})]
    (testing "updating an ACL to change its legacy guid"
      (is (thrown-with-msg?
            Exception
            #"ACL legacy guid cannot be updated, was \[ABCD-EFG-HIJK-LMNOP\] and now \[XYZ-EFG-HIJK-LMNOP\]"
            (ac/update-acl (u/conn-context) provider-concept-id
                           (assoc provider-acl :legacy_guid "XYZ-EFG-HIJK-LMNOP") {:token token}))))))

(deftest delete-acl-test
  (let [token (e/login (u/conn-context) "admin")
        acl-concept-id (:concept_id
                         (ac/create-acl (u/conn-context)
                                        {:group_permissions [{:permissions [:read]
                                                              :user_type :guest}]
                                         :catalog_item_identity {:name "PROV1 guest read"
                                                                 :collection_applicable true
                                                                 :provider_id "PROV1"}}
                                        {:token token}))
        coll1 (perm-test/ingest-collection token {:entry-title "coll1"
                                                  :native-id "coll1"
                                                  :entry-id "coll1"
                                                  :short-name "coll1"
                                                  :provider-id "PROV1"})]
    (testing "created ACL grants permissions (precursor to testing effectiveness of deletion)"
      (is (= {coll1 ["read"]}
             (json/parse-string
               (ac/get-permissions (u/conn-context)
                                   {:concept_id coll1 :user_type "guest"}
                                   {:token token})))))
    (testing "404 status is returned if ACL does not exist"
      (is (= {:status 404
              :body {:errors ["ACL could not be found with concept id [ACL1234-NOPE]"]}
              :content-type :json}
             (ac/delete-acl (u/conn-context) "ACL1234-NOPE" {:token token :raw? true}))))
    (testing "200 status, concept id and revision id of tombstone is returned on successful deletion."
      (is (= {:status 200
              :body {:revision-id 2
                     :concept-id "ACL1200000000-CMR"}
              :content-type :json}
             (ac/delete-acl (u/conn-context) acl-concept-id {:token token :raw? true}))))
    (testing "tombstone can be retrieved from Metadata DB"
      (is (:deleted (mdb/get-latest-concept (u/conn-context) acl-concept-id))))
    (testing "permissions granted by the ACL are no longer in effect"
      (is (= {"ACL1200000000-CMR" []}
             (json/parse-string
               (ac/get-permissions (u/conn-context)
                                   {:concept_id acl-concept-id :user_type "guest"}
                                   {:token token})))))))
