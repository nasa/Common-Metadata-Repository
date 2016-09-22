(ns cmr.access-control.int-test.acl-crud-test
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
              (fixtures/grant-all-group-fixture ["prov1guid" "prov2guid"]))

(def system-acl
  "A system acl that grants create, read, update, delete to guest on any acl"
  {:group_permissions [{:user_type "guest"
                        :permissions ["create" "read" "update" "delete"]}]
   :system_identity {:target "ANY_ACL"}})

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

(def single-instance-acl
  "A sample single instance ACL."
  {:group_permissions [{:user_type "guest" :permissions ["update" "delete"]}]
   :single_instance_identity {:target "GROUP_MANAGEMENT"
                              :target_id "REPLACEME"}})

(deftest create-acl-test
  (let [token (e/login (u/conn-context) "admin")
        resp (ac/create-acl (u/conn-context) system-acl {:token token})]
    ;; Acceptance criteria: A concept id and revision id of the created ACL should be returned.
    (is (re-find #"^ACL.*" (:concept_id resp)))
    (is (= 1 (:revision_id resp)))))

(deftest create-single-instance-acl-test
  (let [token (e/login (u/conn-context) "user1")
        group1 (u/ingest-group token {:name "group1"} ["user1"])
        group1-concept-id (:concept_id group1)
        resp (ac/create-acl (u/conn-context) (assoc-in single-instance-acl
                                                       [:single_instance_identity :target_id]
                                                       group1-concept-id)
                            {:token token})]
    (is (re-find #"^ACL.*" (:concept_id resp)))
    (is (= 1 (:revision_id resp)))))

(deftest create-acl-errors-test
  (let [token (e/login (u/conn-context) "admin")
        group1 (u/ingest-group token {:name "group1"} ["user1"])
        group1-concept-id (:concept_id group1)]
    (are3 [re acl]
          (is (thrown-with-msg? Exception re (ac/create-acl (u/conn-context) acl {:token token})))

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

          "Acceptance criteria: I receive an error if creating an ACL with a non-existent system
           identity, provider identity, or single instance identity target."
          #"instance value .* not found in enum"
          (update-in system-acl [:system_identity] assoc :target "WHATEVER")

          "Value not found in enum"
          #"instance value .* not found in enum"
          (update-in provider-acl [:provider_identity] assoc :target "WHATEVER")

          "Provider doesn't exist, provider version"
          #"Provider with provider-id \[WHATEVER\] does not exist"
          (assoc-in provider-acl [:provider_identity :provider_id] "WHATEVER")

          "Provider doesn't exist, catalog-item version"
          #"Provider with provider-id \[WHATEVER\] does not exist"
          (assoc-in catalog-item-acl [:catalog_item_identity :provider_id] "WHATEVER")

          "Group id doesn't exist for single-instance-identity"
          #"Group with concept-id \[AG123-CMR\] does not exist"
          (assoc-in single-instance-acl [:single_instance_identity :target_id] "AG123-CMR")

          "Single instance identity target grantable permission check"
          #"\[single-instance-identity\] ACL cannot have \[create, read] permission for target \[GROUP_MANAGEMENT\], only \[update, delete\] are grantable"
          (assoc-in
            (assoc-in single-instance-acl [:group_permissions 0 :permissions] ["create" "read" "update" "delete"])
            [:single_instance_identity :target_id] group1-concept-id)

          "Provider identity target grantable permission check"
          #"\[provider-identity\] ACL cannot have \[create, delete\] permission for target \[INGEST_MANAGEMENT_ACL\], only \[read, update\] are grantable"
          (assoc-in provider-acl [:group_permissions 0 :permissions] ["create" "read" "update" "delete"])

          "System identity target grantable permission check"
          #"\[system-identity\] ACL cannot have \[read\] permission for target \[TAG_GROUP\], only \[create, update, delete\] are grantable"
          (assoc-in (assoc-in system-acl [:group_permissions 0 :permissions] ["create" "read" "update" "delete"])
                    [:system_identity :target] "TAG_GROUP"))

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

(deftest acl-catalog-item-identity-validation-test
  (let [token (e/login (u/conn-context) "admin")]
    (are3 [errors acl] (is (= errors (:errors (u/create-acl token acl {:raw? true}))))

          "An error is returned if creating a catalog item identity that does not grant permission
               to collections or granules. (It must grant to collections or granules or both.)"
          ["when catalog_item_identity is specified, one or both of collection_applicable or granule_applicable must be true"]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"}}

          "An error is returned if creating a collection applicable catalog item identity with a granule identifier"
          ["granule_applicable must be true when granule_identifier is specified"]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"
                                   :collection_applicable true
                                   :granule_identifier {:access_value {:include_undefined_value true}}}}

          "An error is returned if specifying a collection identifier with collection entry titles that do not exist."
          ["collection with entry-title [notreal] does not exist in provider [PROV1]"]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"
                                   :collection_applicable true
                                   :collection_identifier {:entry_titles ["notreal"]}}}

          "At least one of a range (min and/or max) or include_undefined value must be specified."
          ["min_value and/or max_value must be specified when include_undefined_value is false"]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"
                                   :collection_applicable true
                                   :collection_identifier {:access_value {:include_undefined_value false}}}}

          "include_undefined_value and range values can't be used together"
          ["min_value and/or max_value must not be specified if include_undefined_value is true"]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"
                                   :collection_applicable true
                                   :collection_identifier {:access_value {:min_value 4
                                                                          :include_undefined_value true}}}}

          "min and max value must be valid numbers if specified"
          ["/catalog_item_identity/granule_identifier/access_value/min_value instance type (string) does not match any allowed primitive type (allowed: [\"integer\",\"number\"])"]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"
                                   :collection_applicable true
                                   :granule_identifier {:access_value {:min_value "potato"}}}}

          "temporal validation: stop must be greater than or equal to start"
          ["start_date must be before stop_date"]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"
                                   :collection_applicable true
                                   :collection_identifier {:temporal {:start_date "2012-01-01T12:00:00Z"
                                                                      :stop_date "2011-01-01T12:00:00Z"
                                                                      :mask "intersect"}}}}

          ;; Repeated for Granule Identifier
          "At least one of a range (min and/or max) or include_undefined value must be specified."
          ["min_value and/or max_value must be specified when include_undefined_value is false"]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"
                                   :granule_applicable true
                                   :granule_identifier {:access_value {:include_undefined_value false}}}}

          "include_undefined_value and range values can't be used together"
          ["min_value and/or max_value must not be specified if include_undefined_value is true"]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"
                                   :granule_applicable true
                                   :granule_identifier {:access_value {:min_value 4
                                                                       :include_undefined_value true}}}}

          "start and stop dates must be valid dates"
          ["/catalog_item_identity/granule_identifier/temporal/start_date string \"banana\" is invalid against requested date format(s) [yyyy-MM-dd'T'HH:mm:ssZ, yyyy-MM-dd'T'HH:mm:ss.SSSZ]"]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"
                                   :granule_applicable true
                                   :granule_identifier {:temporal {:start_date "banana"
                                                                   :stop_date "2012-01-01T12:00:00Z"
                                                                   :mask "intersect"}}}}

          "start and stop dates must be valid dates"
          ["/catalog_item_identity/granule_identifier/temporal/stop_date string \"robot\" is invalid against requested date format(s) [yyyy-MM-dd'T'HH:mm:ssZ, yyyy-MM-dd'T'HH:mm:ss.SSSZ]"]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"
                                   :granule_applicable true
                                   :granule_identifier {:temporal {:start_date "2012-01-01T12:00:00Z"
                                                                   :stop_date "robot"
                                                                   :mask "intersect"}}}}

          "temporal validation: stop must be greater than or equal to start"
          ["start_date must be before stop_date"]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"
                                   :granule_applicable true
                                   :granule_identifier {:temporal {:start_date "2012-01-01T12:00:00Z"
                                                                   :stop_date "2011-01-01T12:00:00Z"
                                                                   :mask "intersect"}}}}

          "Must specify a valid temporal mask"
          ["/catalog_item_identity/granule_identifier/temporal/mask instance value (\"underwhelm\") not found in enum (possible values: [\"intersect\",\"contains\",\"disjoint\"])"]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"
                                   :granule_applicable true
                                   :granule_identifier {:temporal {:start_date "2012-01-01T12:00:00Z"
                                                                   :stop_date "2011-01-01T12:00:00Z"
                                                                   :mask "underwhelm"}}}})

    (testing "collection entry_title check passes when collection exists"
      (u/save-collection {:entry-title "coll1 v1"
                          :native-id "coll1"
                          :entry-id "coll1"
                          :short-name "coll1"
                          :version "v1"
                          :provider-id "PROV1"})
      (u/wait-until-indexed)
      (is (= {:revision_id 1 :status 200}
             (select-keys
               (u/create-acl token {:group_permissions [{:user_type "guest" :permissions ["read"]}]
                                    :catalog_item_identity {:name "A real live catalog item ACL"
                                                            :provider_id "PROV1"
                                                            :collection_applicable true
                                                            :collection_identifier {:entry_titles ["coll1 v1"]}}})
               [:revision_id :status]))))))

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

(deftest update-single-instance-acl-test
  (let [token (e/login (u/conn-context) "user1")
        group1 (u/ingest-group token
                               {:name "group1"}
                               ["user1"])
        group2 (u/ingest-group token
                               {:name "group2"}
                               ["user1"])
        group1-concept-id (:concept_id group1)
        group2-concept-id (:concept_id group2)
        {concept-id :concept_id} (ac/create-acl (u/conn-context) (assoc-in single-instance-acl [:single_instance_identity :target_id] group1-concept-id) {:token token})
        resp (ac/update-acl (u/conn-context) concept-id (assoc-in single-instance-acl [:single_instance_identity :target_id] group2-concept-id) {:token token})]
    (is (= concept-id (:concept_id resp)))
    (is (= 2 (:revision_id resp)))
    (is (= (assoc-in single-instance-acl [:single_instance_identity :target_id] group2-concept-id) (ac/get-acl (u/conn-context) concept-id {:token token})))))

(deftest update-acl-errors-test
  (let [token (e/login (u/conn-context) "admin")
        {system-concept-id :concept_id} (ac/create-acl (u/conn-context) system-acl {:token token})
        group1 (u/ingest-group token {:name "group1"} ["user1"])
        group1-concept-id (:concept_id group1)
        {provider-concept-id :concept_id} (ac/create-acl (u/conn-context) provider-acl {:token token})
        {catalog-concept-id :concept_id} (ac/create-acl (u/conn-context) catalog-item-acl {:token token})
        {single-instance-concept-id :concept_id} (ac/create-acl (u/conn-context) (assoc-in single-instance-acl [:single_instance_identity :target_id] group1-concept-id) {:token token})]
    (are3 [re acl concept-id]
          (is (thrown-with-msg? Exception re (ac/update-acl (u/conn-context) concept-id acl {:token token})))
          ;; Acceptance criteria: I receive an error if creating an ACL missing required fields.
          ;; Note: this tests a few fields, and is not exhaustive. The JSON schema handles this check.
          "Nil field value"
          #"object has missing required properties"
          (dissoc system-acl :group_permissions)
          system-concept-id

          "Empty field value"
          #"group_permissions.* object has missing required properties"
          (assoc system-acl :group_permissions [{}])
          system-concept-id

          "Missing target"
          #"system_identity object has missing required properties"
          (update-in system-acl [:system_identity] dissoc :target)
          system-concept-id

          "Acceptance criteria: I receive an error if updating an ACL with an invalid combination of fields. (Only one of system, provider, single instance, or catalog item identities)"
          #"instance failed to match exactly one schema"
          (assoc system-acl :provider_identity {:provider_id "PROV1"
                                                :target "INGEST_MANAGEMENT_ACL"})
          system-concept-id

          "Acceptance criteria: I receive an error if updating an ACL with a non-existent system identity, provider identity, or single instance identity target."
          #"instance value .* not found in enum"
          (update-in system-acl [:system_identity] assoc :target "WHATEVER")
          system-concept-id

          "Value not found in enum"
          #"instance value .* not found in enum"
          (update-in provider-acl [:provider_identity] assoc :target "WHATEVER")
          provider-concept-id

          "Provider doesn't exist, provider version"
          #"Provider with provider-id \[WHATEVER\] does not exist"
          (assoc-in provider-acl [:provider_identity :provider_id] "WHATEVER")
          provider-concept-id

          "Provider doesn't exist, catalog-item version"
          #"Provider with provider-id \[WHATEVER\] does not exist"
          (assoc-in catalog-item-acl [:catalog_item_identity :provider_id] "WHATEVER")
          catalog-concept-id

          "Group id doesn't exist for single-instance-identity"
          #"Group with concept-id \[AG123-CMR\] does not exist"
          (assoc-in single-instance-acl [:single_instance_identity :target_id] "AG123-CMR")
          single-instance-concept-id

          "Single instance identity target grantable permission check"
          #"\[single-instance-identity\] ACL cannot have \[create, read\] permission for target \[GROUP_MANAGEMENT\], only \[update, delete\] are grantable"
          (assoc-in
            (assoc-in single-instance-acl [:group_permissions 0 :permissions] ["create" "read" "update" "delete"])
            [:single_instance_identity :target_id] group1-concept-id)
          single-instance-concept-id

          "Provider identity target grantable permission check"
          #"\[provider-identity\] ACL cannot have \[create, delete\] permission for target \[INGEST_MANAGEMENT_ACL\], only \[read, update\] are grantable"
          (assoc-in provider-acl [:group_permissions 0 :permissions] ["create" "read" "update" "delete"])
          provider-concept-id

          "System identity target grantable permission check"
          #"\[system-identity\] ACL cannot have \[read\] permission for target \[TAG_GROUP\], only \[create, update, delete\] are grantable"
          (assoc-in (assoc-in system-acl [:group_permissions 0 :permissions] ["create" "read" "update" "delete"])
                    [:system_identity :target] "TAG_GROUP")
          system-concept-id)

    (testing "Acceptance criteria: I receive an error if updating an ACL with invalid JSON"
      (is
        (re-find #"Invalid JSON:"
                 (:body
                   (client/put
                     (ac/acl-concept-id-url
                       (transmit-config/context->app-connection (u/conn-context) :access-control)
                       system-concept-id)
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
                       system-concept-id)
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
        coll1 (u/save-collection {:entry-title "coll1"
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
                     :concept-id "ACL1200000001-CMR"}
              :content-type :json}
             (ac/delete-acl (u/conn-context) acl-concept-id {:token token :raw? true}))))
    (testing "404 is returned when trying to delete an ACL again"
      (is (= {:status 404
              :body {:errors ["ACL with concept id [ACL1200000001-CMR] was deleted."]}
              :content-type :json}
             (ac/delete-acl (u/conn-context) acl-concept-id {:token token :raw? true}))))
    (testing "concept can no longer be retrieved through access control service"
      (is (= nil
             (ac/get-acl (u/conn-context) acl-concept-id))))
    (testing "tombstone can be retrieved from Metadata DB"
      (is (= {:deleted true
              :revision-id 2
              :metadata ""
              :concept-id acl-concept-id}
             (select-keys
               (mdb/get-latest-concept (u/conn-context) acl-concept-id)
               [:deleted :revision-id :metadata :concept-id]))))
    (testing "permissions granted by the ACL are no longer in effect"
      (is (= {coll1 []}
             (json/parse-string
               (ac/get-permissions (u/conn-context)
                                   {:concept_id coll1 :user_type "guest"}
                                   {:token token})))))))
