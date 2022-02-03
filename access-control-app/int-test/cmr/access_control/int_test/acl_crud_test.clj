(ns cmr.access-control.int-test.acl-crud-test
  (:require
    [cheshire.core :as json]
    [clj-http.client :as client]
    [clojure.string :as string]
    [clojure.test :refer :all]
    [cmr.access-control.int-test.fixtures :as fixtures]
    [cmr.access-control.services.acl-validation :as acl-validation]
    [cmr.access-control.test.util :as test-util]
    [cmr.common.util :as util :refer [are3]]
    [cmr.mock-echo.client.echo-util :as echo-util]
    [cmr.transmit.access-control :as access-control]
    [cmr.transmit.config :as transmit-config]
    [cmr.transmit.metadata-db2 :as metadata-db2]))

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

(def single-instance-acl
  "A sample single instance ACL."
  {:group_permissions [{:user_type "guest" :permissions ["update" "delete"]}]
   :single_instance_identity {:target "GROUP_MANAGEMENT"
                              :target_id "REPLACEME"}})

(deftest create-acl-test
  (let [token (echo-util/login (test-util/conn-context) "admin")
        resp (access-control/create-acl (test-util/conn-context) system-acl {:token token})]
    ;; Acceptance criteria: A concept id and revision id of the created ACL should be returned.
    (is (re-find #"^ACL.*" (:concept_id resp)))
    (is (= 1 (:revision_id resp)))))

(deftest create-single-instance-acl-test
  (let [token (echo-util/login (test-util/conn-context) "user1")
        group1 (test-util/ingest-group token {:name "group1"} ["user1"])
        group1-concept-id (:concept_id group1)
        resp (access-control/create-acl (test-util/conn-context)
                                        (assoc-in single-instance-acl
                                                  [:single_instance_identity :target_id]
                                                  group1-concept-id)
                            {:token token})]
    (is (re-find #"^ACL.*" (:concept_id resp)))
    (is (= 1 (:revision_id resp)))))

;; The following test covers creating simple but valid catalog item identity. We permit collection_applicable false
;; and collection_identifier when granule_applicable is true.

(deftest create-catalog-item-identity-acl-test
  (testing "with collection_identifier, collection_applicable true, and granule_applicable false"
    (is (= 1 (:revision_id
               (access-control/create-acl (test-util/conn-context)
                                          {:group_permissions [{:user_type "guest"
                                                                :permissions ["read"]}]
                                           :catalog_item_identity {:name "Catalog Item Identity 1"
                                                                   :provider_id "PROV1"
                                                                   :collection_identifier {:access_value {:include_undefined_value true
                                                                                                          :min_value 1 :max_value 10000}}
                                                                   :collection_applicable true
                                                                   :granule_applicable false}}
                                          {:token (transmit-config/echo-system-token)})))))
  (testing "with collection_identifier, collection_applicable false, and granule_applicable true"
    (is (= 1 (:revision_id
               (access-control/create-acl (test-util/conn-context)
                                          {:group_permissions [{:user_type "guest"
                                                                :permissions ["read"]}]
                                           :catalog_item_identity {:name "Catalog Item Identity 2"
                                                                   :provider_id "PROV1"
                                                                   :collection_identifier {:access_value {:include_undefined_value true
                                                                                                          :min_value 4 :max_value 999}}
                                                                   :collection_applicable false
                                                                   :granule_applicable true}}
                                          {:token (transmit-config/echo-system-token)}))))))

(deftest create-single-instance-acl-permission-test
  (let [token (echo-util/login (test-util/conn-context) "user1")
        group1 (test-util/ingest-group token {:name "group1" :provider_id "PROV1"} ["user1"])
        group1-concept-id (:concept_id group1)]
    ;; Update the system ACL to remove permission to create single instance ACLs
    (access-control/update-acl (test-util/conn-context)
                               (:concept-id fixtures/*fixture-system-acl*)
                               {:system_identity {:target "ANY_ACL"}
                                :group_permissions [{:user_type "registered" :permissions ["read"]}]}
                               {:token transmit-config/mock-echo-system-token})
    (let [{:keys [status body]} (access-control/create-acl
                                 (test-util/conn-context)
                                 {:group_permissions [{:user_type "registered" :permissions ["update" "delete"]}]
                                  :single_instance_identity {:target_id group1-concept-id
                                                             :target "GROUP_MANAGEMENT"}}
                                 {:token token :raw? true})]
      (is (= 401 status))
      (is (= ["Permission to create ACL is denied"] (:errors body))))
    ;; Create a provider-specific ACL granting permission to create ACLs targeting groups
    (access-control/create-acl (test-util/conn-context)
                               {:group_permissions [{:user_type "registered" :permissions ["create"]}]
                                :provider_identity {:target "PROVIDER_OBJECT_ACL"
                                                    :provider_id "PROV1"}}
                               {:token transmit-config/mock-echo-system-token})
    (is (= 1 (:revision_id
              (access-control/create-acl
               (test-util/conn-context)
               {:group_permissions [{:user_type "registered" :permissions ["update" "delete"]}]
                :single_instance_identity {:target_id group1-concept-id
                                           :target "GROUP_MANAGEMENT"}}
               {:token token}))))))


(deftest create-provider-acl-permission-test
  ;; Tests user permission to create provider acls
  (let [token-user1 (echo-util/login (test-util/conn-context) "user1")
        guest-token (echo-util/login-guest (test-util/conn-context))
        token-user2 (echo-util/login (test-util/conn-context) "user2")
        token-user3 (echo-util/login (test-util/conn-context) "user3")
        any-acl-group (test-util/ingest-group token-user1 {:name "any acl group"} ["user1"])
        any-acl-group-id (:concept_id any-acl-group)
        prov-obj-acl-group (test-util/ingest-group token-user1 {:name "prov obj acl group"} ["user2"])
        prov-obj-acl-group-id (:concept_id prov-obj-acl-group)
        cat-item-prov-acl-group (test-util/ingest-group token-user1 {:name "cat item prov acl"} ["user3"])
        cat-item-prov-acl-group-id (:concept_id cat-item-prov-acl-group)]

    ;; Update ANY_ACL fixture to remove permissions to create from registered users.
    (access-control/update-acl (merge {:token guest-token} (test-util/conn-context))
                               (:concept-id fixtures/*fixture-system-acl*)
                               {:system_identity {:target "ANY_ACL"}
                                :group_permissions [{:user_type "guest" :permissions ["create" "update"]}]})

    (testing "Without permissions"
      (are3 [token acl]
        (let [{:keys [status body]} (access-control/create-acl
                                     (merge {:token token} (test-util/conn-context))
                                     acl
                                     {:raw? true})]
          (is (= 401 status))
          (is (= ["Permission to create ACL is denied"] (:errors body))))

        "ANY_ACL check"
        token-user1
        {:provider_identity {:provider_id "PROV1" :target "AUDIT_REPORT"}
         :group_permissions [{:user_type "guest" :permissions ["read"]}]}

        "PROVIDER_OBJECT_ACL check"
        token-user2
        {:provider_identity {:provider_id "PROV2" :target "AUDIT_REPORT"}
         :group_permissions [{:user_type "guest" :permissions ["read"]}]}

        "CATALOG_ITEM_ACL check"
        token-user3
        {:provider_identity {:provider_id "PROV2" :target "CATALOG_ITEM_ACL"}
         :group_permissions [{:user_type "guest" :permissions ["read"]}]}))

    (testing "grant ACL create permission to specific group"
      ;; Update ANY_ACL to grant user1 permission to create ACL.
      (access-control/update-acl (merge {:token guest-token} (test-util/conn-context))
                                 (:concept-id fixtures/*fixture-system-acl*)
                                 {:system_identity {:target "ANY_ACL"}
                                  :group_permissions [{:user_type "guest" :permissions ["create"]}
                                                      {:group_id any-acl-group-id :permissions ["create"]}]})
      ;; Create provider acl target PROVIDER_OBJECT_ACL and grant ACL create permission to user2,
      ;; which is a member of the prov-obj-acl-group.
      (access-control/create-acl (merge {:token guest-token} (test-util/conn-context))
                                 {:provider_identity {:provider_id "PROV2" :target "PROVIDER_OBJECT_ACL"}
                                  :group_permissions [{:user_type "guest" :permissions ["create"]}
                                                      {:group_id prov-obj-acl-group-id :permissions ["create"]}]})

      ;; verify that user1 and user2 can now create their permitted ACLs
      (are3 [token acl]
        (let [resp (access-control/create-acl (merge {:token token} (test-util/conn-context)) acl)]
          (is (re-find #"^ACL.*" (:concept_id resp)))
          (is (= 1 (:revision_id resp))))
        "ANY_ACL check"
        token-user1
        {:provider_identity {:provider_id "PROV1" :target "AUDIT_REPORT"}
         :group_permissions [{:user_type "guest" :permissions ["read"]}]}

        "PROVIDER_OBJECT_ACL check"
        token-user2
        {:provider_identity {:provider_id "PROV2" :target "AUDIT_REPORT"}
         :group_permissions [{:user_type "guest" :permissions ["read"]}]})

      ;; verify that user3 still don't have permission to create ACL
      (let [{:keys [status body]} (access-control/create-acl
                                   (merge {:token token-user3} (test-util/conn-context))
                                   {:provider_identity {:provider_id "PROV2" :target "CATALOG_ITEM_ACL"}
                                    :group_permissions [{:user_type "guest" :permissions ["read"]}]}
                                   {:raw? true})]
        (is (= 401 status))
        (is (= ["Permission to create ACL is denied"] (:errors body)))))))

(deftest create-system-level-acl-permission-test
  (let [token-user1 (echo-util/login (test-util/conn-context) "user1")
        guest-token (echo-util/login-guest (test-util/conn-context))
        token-user2 (echo-util/login (test-util/conn-context) "user2")
        group1 (test-util/ingest-group token-user1 {:name "group1"} ["user1"])
        group1-concept-id (:concept_id group1)
        ;; Update ANY_ACL fixture to remove permissions from guest and registered,
        ;; and replace it with group1
        _ (access-control/update-acl (merge {:token token-user1} (test-util/conn-context))
                                     (:concept-id fixtures/*fixture-system-acl*)
                                     (assoc (assoc-in system-acl
                                                      [:system_identity :target]
                                                      "ANY_ACL")
                                            :group_permissions [{:group_id group1-concept-id
                                                                 :permissions ["read" "create"]},
                                                                {:user_type :guest
                                                                 :permissions ["read"]}]))]
    (testing "create system level ACL without permission"
      (are3 [token]
        (let [{:keys [status body]} (access-control/create-acl (assoc (test-util/conn-context) :token token)
                                                               system-acl {:raw? true})]
          (is (= 401 status))
          (is (= ["Permission to create ACL is denied"] (:errors body))))

        "Try to create ACL as guest"
        guest-token

        "Try to create ACL as user2"
        token-user2))

    (testing "create system level ACL with permission, user1 has permission"
      (let [{:keys [concept_id revision_id]} (access-control/create-acl
                                              (assoc (test-util/conn-context) :token token-user1) system-acl)]
        (is (re-find #"^ACL.*" concept_id))
        (is (= 1 revision_id))))))

(deftest acl-targeting-group-with-legacy-guid-test
  (let [admin-token (echo-util/login (test-util/conn-context) "admin")
        ;; as an admin user, create a group with a legacy_guid
        created-group (:concept_id (access-control/create-group (test-util/conn-context)
                                                                {:name "group"
                                                                 :description "a group"
                                                                 :legacy_guid "normal-group-guid"
                                                                 :members ["user1"]}
                                                                {:token admin-token}))]

    ;; Update the system-level ANY_ACL to avoid granting ACL creation permission to "user1", since it normally
    ;; grants this permission to all registered users.
    (access-control/update-acl (test-util/conn-context)
                               (:concept-id fixtures/*fixture-system-acl*)
                               {:group_permissions [{:group_id created-group
                                                     :permissions [:create :read :update :delete]}]
                                :system_identity {:target "ANY_ACL"}}
                               {:token admin-token})

    ;; Update the PROV1 CATALOG_ITEM_ACL ACL to grant permission explicitly to only the group which "user1" belongs to.
    (access-control/update-acl (test-util/conn-context)
                               (:concept-id fixtures/*fixture-provider-acl*)
                               {:group_permissions [{:group_id created-group
                                                     :permissions [:create :read :update :delete]}]
                                :provider_identity {:provider_id "PROV1"
                                                    :target "CATALOG_ITEM_ACL"}}
                               {:token admin-token})

    ;; As "user1" try to create a catalog item ACL for PROV1.
    (let [user-token (echo-util/login (test-util/conn-context) "user1" ["normal-group-guid"])]
      (is (= 1 (:revision_id
                (access-control/create-acl (test-util/conn-context)
                                           {:group_permissions [{:user_type :registered
                                                                 :permissions [:read]}]
                                            :catalog_item_identity {:provider_id "PROV1"
                                                                    :name "PROV1 collections ACL"
                                                                    :collection_applicable true}}
                                           {:token user-token})))))))

(deftest create-catalog-item-acl-permission-test
  ;; Tests creation permissions of catalog item acls
  (let [user1-token (echo-util/login (test-util/conn-context) "user1")
        guest-token (echo-util/login-guest (test-util/conn-context))
        user2-token (echo-util/login (test-util/conn-context) "user2")
        group1 (test-util/ingest-group user1-token {:name "group1"} ["user1"])
        group1-concept-id (:concept_id group1)
        acl-to-create (assoc-in catalog-item-acl [:catalog_item_identity :provider_id] "PROV2")]
    ;; create ACL to grant user1 permission to create catalog item ACL on PROV2
    (access-control/create-acl (test-util/conn-context) {:group_permissions [{:group_id group1-concept-id
                                                                              :permissions ["create"]}]
                                                            :provider_identity {:provider_id "PROV2"
                                                                                :target "CATALOG_ITEM_ACL"}})
    ;; update system ACL to not allow guest or registered users to create any ACLs
    (access-control/update-acl (test-util/conn-context)
                               (:concept-id fixtures/*fixture-system-acl*)
                               {:system_identity {:target "ANY_ACL"}
                                :group_permissions [{:user_type "guest" :permissions ["read"]}]})

    (testing "create catalog item ACL without permission"
      (are3 [token]
        (let [{:keys [status body]} (access-control/create-acl
                                     (test-util/conn-context)
                                     acl-to-create
                                     {:token token
                                      :raw? true})]
          (is (= 401 status))
          (is (= ["Permission to create ACL is denied"] (:errors body))))

        "Try to create ACL as guest"
        guest-token

        "Try to create ACL as user2"
        user2-token))

    (testing "create catalog item ACL with permission, user1 has permission"
      (let [{:keys [concept_id revision_id]} (access-control/create-acl
                                              (test-util/conn-context)
                                              acl-to-create
                                              {:token user1-token})]
        (is (re-find #"^ACL.*" concept_id))
        (is (= 1 revision_id))))))

(deftest create-acl-errors-test
  (let [token (echo-util/login (test-util/conn-context) "admin")
        group1 (test-util/ingest-group token {:name "group1"} ["user1"])
        group1-concept-id (:concept_id group1)
        provider-id (:provider_id (:provider_identity (access-control/create-acl
                                                       (test-util/conn-context)
                                                       provider-acl
                                                       {:token token})))]
    (are3 [re acl]
          (is (thrown-with-msg? Exception re (access-control/create-acl
                                              (test-util/conn-context)
                                              acl
                                              {:token token})))

          ;; Acceptance criteria: I receive an error if creating an ACL missing required fields.
          ;; Note: this tests a few fields, and is not exhaustive. The JSON schema handles this check.
          "Nil field value"
          #"#: required key \[.*\] not found"
          (dissoc system-acl :group_permissions)

          "Empty field value"
          #"#/group_permissions/0: required key \[.*\] not found"
          (assoc system-acl :group_permissions [{}])

          "Missing target"
          #"#/system_identity: required key \[.*\] not found"
          (update-in system-acl [:system_identity] dissoc :target)

          "Acceptance criteria: I receive an error if creating an ACL with a non-existent system
           identity, provider identity, or single instance identity target."
          #"#/system_identity/target: .* is not a valid enum value"
          (update-in system-acl [:system_identity] assoc :target "WHATEVER")

          "Value not found in enum"
          #"#/provider_identity/target: .* is not a valid enum value"
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

          "Group id is valid CMR concept-id in group-permissions"
          #"\[INVALID-ID\] is not a valid group concept-id."
          (update system-acl :group_permissions conj {:group_id "INVALID-ID" :permissions ["read"]})

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
                   (client/post (access-control/acl-root-url
                                 (transmit-config/context->app-connection
                                  (test-util/conn-context)
                                  :access-control))
                                {:body "{\"bad-json:"
                                 :headers {"Content-Type" "application/json"
                                           "Authorization" token}
                                 :throw-exceptions false})))))

    (testing "Acceptance criteria: I receive an error if creating an ACL with JSON described in CMR-6026"
      (is
        (re-find #"Json parsing error:"
                 (:body
                   (client/post (access-control/acl-root-url
                                 (transmit-config/context->app-connection
                                  (test-util/conn-context)
                                  :access-control))
                                {:body "{\"group_permissions\": [ {\"user_type\": \"registered\",
                                                                   \"permissions\": [\"read\"]}],
                                         \"catalog_item_identity\": {\"name\": \"Example\",
                                                                     \"provider_id\": \"prov-id\",}}"
                                 :headers {"Content-Type" "application/json"
                                           "Authorization" token}
                                 :throw-exceptions false})))))

    (testing "Acceptance criteria: I receive an error if creating an ACL with unsupported content type"
      (is
        (re-find #"The mime types specified in the content-type header \[application/xml\] are not supported."
                 (:body
                   (client/post
                     (access-control/acl-root-url
                      (transmit-config/context->app-connection
                       (test-util/conn-context)
                       :access-control))
                     {:body (json/generate-string system-acl)
                      :headers {"Content-Type" "application/xml"
                                "Authorization" token}
                      :throw-exceptions false})))))))

(deftest acl-catalog-item-identity-validation-test
  (let [token (echo-util/login-guest (test-util/conn-context))]
    (are3 [errors acl] (is (= errors (:errors (test-util/create-acl token acl {:raw? true}))))

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

          "An error is returned if specifying a collection identifier with collection concept-ids that do not exist."
          ["[INVALID ID] is not a valid collection concept-id."]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"
                                   :collection_applicable true
                                   :collection_identifier {:concept_ids ["INVALID ID"]}}}

          "At least one of a range (min and/or max) or include_undefined value must be specified (collection_identifier)"
          ["either include_undefined_value or the combination of min_value and max_value must be specified"]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"
                                   :collection_applicable true
                                   :collection_identifier {:access_value {:include_undefined_value false}}}}

          "min and max value must be valid numbers if specified"
          ["#/catalog_item_identity/collection_identifier/access_value/min_value: expected type: Number, found: String"]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"
                                   :collection_applicable true
                                   :collection_identifier {:access_value {:min_value "potato"}}}}

          "min_value and max_value presence"
          ["either include_undefined_value or the combination of min_value and max_value must be specified"
           "min_value and max_value must both be present if either is specified"]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"
                                   :collection_applicable true
                                   :collection_identifier {:access_value {:min_value 10}}}}

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
          "At least one of a range (min and/or max) or include_undefined value must be specified (granule_identifier)"
          ["either include_undefined_value or the combination of min_value and max_value must be specified"]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"
                                   :granule_applicable true
                                   :granule_identifier {:access_value {:include_undefined_value false}}}}

          "start and stop dates must be valid dates"
          ["#/catalog_item_identity/granule_identifier/temporal/start_date: [banana] is not a valid date-time. Expected [yyyy-MM-dd'T'HH:mm:ssZ, yyyy-MM-dd'T'HH:mm:ss.[0-9]{1,9}Z, yyyy-MM-dd'T'HH:mm:ss[+-]HH:mm, yyyy-MM-dd'T'HH:mm:ss.[0-9]{1,9}[+-]HH:mm]"]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"
                                   :granule_applicable true
                                   :granule_identifier {:temporal {:start_date "banana"
                                                                   :stop_date "2012-01-01T12:00:00Z"
                                                                   :mask "intersect"}}}}

          "start and stop dates must be valid dates"
          ["#/catalog_item_identity/granule_identifier/temporal/stop_date: [robot] is not a valid date-time. Expected [yyyy-MM-dd'T'HH:mm:ssZ, yyyy-MM-dd'T'HH:mm:ss.[0-9]{1,9}Z, yyyy-MM-dd'T'HH:mm:ss[+-]HH:mm, yyyy-MM-dd'T'HH:mm:ss.[0-9]{1,9}[+-]HH:mm]"]
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
          ["#/catalog_item_identity/granule_identifier/temporal/mask: underwhelm is not a valid enum value"]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"
                                   :granule_applicable true
                                   :granule_identifier {:temporal {:start_date "2012-01-01T12:00:00Z"
                                                                   :stop_date "2011-01-01T12:00:00Z"
                                                                   :mask "underwhelm"}}}})

    (testing "collection concept id and entry title check passes when collection exists"
      (let [concept-id (test-util/save-collection {:entry-title "coll1 v1"
                                                   :native-id "coll1"
                                                   :entry-id "coll1"
                                                   :short-name "coll1"
                                                   :version "v1"
                                                   :provider-id "PROV1"})]
        (is (= {:revision_id 1 :status 200}
               (select-keys
                (test-util/create-acl
                 token
                 {:group_permissions [{:user_type "guest" :permissions ["read"]}]
                  :catalog_item_identity {:name "A real live catalog item ACL"
                                          :provider_id "PROV1"
                                          :collection_applicable true
                                          :collection_identifier {:concept_ids [concept-id]
                                                                  :entry_titles ["coll1 v1"]}}})
                [:revision_id :status])))))

    (testing "collection concept id and entry title check passes when one collection doesn't exist."
      (let [concept-id (test-util/save-collection {:entry-title "coll5 v1"
                                                   :native-id "coll5"
                                                   :entry-id "coll5"
                                                   :short-name "coll5"
                                                   :version "v1"
                                                   :provider-id "PROV1"})
            ;; To test that validly formated collection concept-ids that don't exist in the provider
            ;; are not added into the collection-identifier on creation.
            non-existent-coll-id "C999999-PROV1"
            acl (test-util/create-acl
                 token
                 {:group_permissions [{:user_type "guest" :permissions ["read"]}]
                  :catalog_item_identity {:name "A real live catalog item ACL2"
                                          :provider_id "PROV1"
                                          :collection_applicable true
                                          :collection_identifier {:concept_ids
                                                                  [concept-id non-existent-coll-id]
                                                                  :entry_titles ["coll5 v1"]}}})
            resp (access-control/get-acl (test-util/conn-context)
                                         (get acl :concept_id)
                                         {:token token :raw? true
                                          :include_full_acl true})]
        (is (= 1 (get acl :revision_id)))
        (is (= 200 (get acl :status)))
        (is (= (get-in resp [:body :catalog_item_identity :collection_identifier :concept_ids])
               [concept-id]))
        (is (= (get-in resp [:body :catalog_item_identity :collection_identifier :entry_titles])
               ["coll5 v1"]))))

    (testing "long entry titles"
      (test-util/save-collection {:entry-title "coll2 xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
                                  :native-id "coll2"
                                  :entry-id "coll2"
                                  :short-name "coll2"
                                  :version "v1"
                                  :provider-id "PROV1"})
      (let [result
            (test-util/create-acl
             token
             {:group_permissions [{:user_type "guest" :permissions ["read"]}]
              :catalog_item_identity {:name "Catalog item ACL with a long entry title"
                                      :provider_id "PROV1"
                                      :collection_applicable true
                                      :collection_identifier {:entry_titles ["coll2 xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"]}}})]
        (is (= 1 (:revision_id result)))))))

(deftest create-duplicate-acl-test
  (let [token (echo-util/login-guest (test-util/conn-context))]
    (testing "system ACL"
      (is (= 1 (:revision_id (access-control/create-acl (test-util/conn-context) system-acl {:token token}))))
      (is (thrown-with-msg? Exception #"concepts with the same acl identity were found"
                            (access-control/create-acl (test-util/conn-context) system-acl {:token token}))))
    (testing "provider ACL"
      (is (= 1 (:revision_id (access-control/create-acl (test-util/conn-context) provider-acl {:token token}))))
      (is (thrown-with-msg? Exception #"concepts with the same acl identity were found"
                            (access-control/create-acl (test-util/conn-context) provider-acl {:token token}))))
    (testing "catalog item ACL"
      (is (= 1 (:revision_id (access-control/create-acl (test-util/conn-context) catalog-item-acl {:token token}))))
      (is (thrown-with-msg? Exception #"concepts with the same acl identity were found"
                            (access-control/create-acl (test-util/conn-context) catalog-item-acl {:token token}))))))

(deftest get-acl-test
  (testing "get acl general case"
    (let [token (echo-util/login (test-util/conn-context) "admin")
          concept-id (:concept_id (access-control/create-acl (test-util/conn-context) system-acl {:token token}))]
      ;; Acceptance criteria: A created ACL can be retrieved after it is created.
      (is (= system-acl (access-control/get-acl (test-util/conn-context) concept-id {:token token})))
      (let [resp (access-control/get-acl (test-util/conn-context) "NOTACONCEPTID" {:token token :raw? true})]
        (is (= 400 (:status resp)))
        (is (= ["Concept-id [NOTACONCEPTID] is not valid."] (:errors (:body resp)))))
      (let [resp (access-control/get-acl (test-util/conn-context) "ACL999999-CMR" {:token token :raw? true})]
        (is (= 404 (:status resp)))
        (is (= ["ACL could not be found with concept id [ACL999999-CMR]"]
               (:errors (:body resp)))))))

  (testing "get acl with group id"
    (let [token (echo-util/login (test-util/conn-context) "admin")
          group1-legacy-guid "group1-legacy-guid"
          group1 (test-util/ingest-group token
                                         {:name "group1"
                                          :legacy_guid group1-legacy-guid}
                                         ["user1"])
          group2 (test-util/ingest-group token
                                         {:name "group2"}
                                         ["user1"])
          group1-concept-id (:concept_id group1)
          group2-concept-id (:concept_id group2)

          ;; ACL associated with a group that has legacy guid
          acl1 (assoc-in (test-util/system-acl "TAG_GROUP")
                         [:group_permissions 0]
                         {:permissions ["create"] :group_id group1-concept-id})

          ;; ACL associated with a group that does not have legacy guid
          acl2 (assoc-in (test-util/system-acl "ARCHIVE_RECORD")
                         [:group_permissions 0]
                         {:permissions ["delete"] :group_id group2-concept-id})
          ;; SingleInstanceIdentity ACL with a group that has legacy guid
          acl3 (test-util/single-instance-acl group1-concept-id)
          ;; SingleInstanceIdentity ACL with a group that does not have legacy guid
          acl4 (test-util/single-instance-acl group2-concept-id)]

      (are3 [expected-acl acl]
        (let [concept-id (:concept_id (access-control/create-acl (test-util/conn-context) acl {:token token}))]
          (is (= expected-acl (access-control/get-acl (test-util/conn-context)
                                                      concept-id
                                                      {:token token
                                                       :http-options {:query-params {:include_legacy_group_guid true}}}))))

        "ACL associated with a group that has legacy guid"
        (assoc-in acl1 [:group_permissions 0 :group_id] group1-legacy-guid)
        acl1

        "ACL associated with a group that does not have legacy guid"
        acl2
        acl2

        "SingleInstanceIdentity ACL with a group that has legacy guid"
        (assoc-in acl3 [:single_instance_identity :target_id] group1-legacy-guid)
        acl3

        "SingleInstanceIdentity ACL with a group that does not have legacy guid"
        acl4
        acl4))))

(deftest get-acl-permission-test
  (let [user1-token (echo-util/login (test-util/conn-context) "user1")
        user2-token (echo-util/login (test-util/conn-context) "user2")
        user3-token (echo-util/login (test-util/conn-context) "user3")
        guest-token (echo-util/login-guest (test-util/conn-context))
        group1 (test-util/ingest-group user1-token
                                       {:name "any acl read"}
                                       ["user1"])
        group2 (test-util/ingest-group user1-token
                                       {:name "without any acl read"}
                                       ["user2"])
        group3 (test-util/ingest-group user1-token
                                       {:name "provider object prov1 read"}
                                       ["user3"])
        group1-concept-id (:concept_id group1)
        group2-concept-id (:concept_id group2)
        group3-concept-id (:concept_id group3)

        ;; remove ANY_ACL read to all users except user1
        _ (access-control/update-acl (test-util/conn-context)
                                     (:concept-id fixtures/*fixture-system-acl*)
                                     (assoc-in (test-util/system-acl "ANY_ACL")
                                               [:group_permissions 0]
                                               {:permissions ["read" "create"] :group_id group1-concept-id}))

        acl1 (test-util/ingest-acl user1-token
                                   (assoc-in (test-util/system-acl "INGEST_MANAGEMENT_ACL")
                                             [:group_permissions 0]
                                             {:permissions ["read"] :group_id group1-concept-id}))
        acl2 (test-util/ingest-acl user1-token
                                   (assoc-in (test-util/system-acl "ARCHIVE_RECORD")
                                             [:group_permissions 0]
                                             {:permissions ["delete"] :group_id group2-concept-id}))
        acl3 (test-util/ingest-acl user1-token
                                   (test-util/system-acl "SYSTEM_OPTION_DEFINITION_DEPRECATION"))
        acl4 (test-util/ingest-acl user1-token
                                   (assoc (test-util/provider-acl "PROVIDER_OBJECT_ACL")
                                          :group_permissions
                                          [{:group_id group3-concept-id :permissions ["read"]}]))
        acl5 (test-util/ingest-acl user1-token
                                   (test-util/provider-acl "OPTION_DEFINITION"))
        acl6 (test-util/ingest-acl user1-token
                                   (assoc-in (test-util/provider-acl "OPTION_DEFINITION")
                                             [:provider_identity :provider_id]
                                             "PROV2"))
        ;; Create an ACL with a catalog item identity for PROV1
        acl7 (test-util/ingest-acl user1-token
                                   {:group_permissions [{:user_type "registered" :permissions ["read"]}]
                                    :catalog_item_identity {:provider_id "PROV1"
                                                            :name "PROV1 All Collections ACL"
                                                            :collection_applicable true}})
        acl8 (test-util/ingest-acl user1-token
                                   {:group_permissions [{:user_type "registered" :permissions ["read"]}]
                                    :catalog_item_identity {:provider_id "PROV2"
                                                            :name "PROV2 All Collections ACL"
                                                            :collection_applicable true}})
        permission-granted? (fn [token acl granted?]
                              (let [{:keys [status]} (access-control/get-acl (test-util/conn-context)
                                                                             (:concept-id acl)
                                                                             {:token token :raw? true})]
                                (if granted?
                                  (is (= 200 status))
                                  (is (= 401 status)))))]
    (testing "with fixture provider object acls"
      (are [token acl granted?]
        (permission-granted? token acl granted?)
        ;; guest only has permission to retrieve acl7
        guest-token acl1 false
        guest-token acl2 false
        guest-token acl3 false
        guest-token acl4 false
        guest-token acl5 false
        guest-token acl6 false
        guest-token acl7 true
        guest-token acl8 false
        ;; user1 has permission to retrieve all ACLs
        user1-token acl1 true
        user1-token acl2 true
        user1-token acl3 true
        user1-token acl4 true
        user1-token acl5 true
        user1-token acl6 true
        user1-token acl7 true
        user1-token acl8 true
        ;; user2 only has permission to retrieve acl7
        user2-token acl1 false
        user2-token acl2 false
        user2-token acl3 false
        user2-token acl4 false
        user2-token acl5 false
        user2-token acl6 false
        user2-token acl7 true
        user2-token acl8 false
        ;; user3 has permission to retrieve acl4, acl5, acl7
        user3-token acl1 false
        user3-token acl2 false
        user3-token acl3 false
        user3-token acl4 true
        user3-token acl5 true
        user3-token acl6 false
        user3-token acl7 true
        user3-token acl8 false))

    (testing "without fixture provider object acls"
      ;; grant only guest user permission to PROV1 CATALOG_ITEM_ACL
      (access-control/update-acl (test-util/conn-context)
                                 (:concept-id fixtures/*fixture-provider-acl*)
                                 {:provider_identity {:provider_id "PROV1"
                                                      :target "CATALOG_ITEM_ACL"}
                                  :group_permissions [{:user_type "guest"
                                                       :permissions ["read" "update"]}]}
                                 {:token user1-token})
      (are [token acl granted?]
        (permission-granted? token acl granted?)
        ;; guest only has permission to retrieve acl7
        guest-token acl1 false
        guest-token acl2 false
        guest-token acl3 false
        guest-token acl4 false
        guest-token acl5 false
        guest-token acl6 false
        guest-token acl7 true
        guest-token acl8 false
        ;; user1 has permission to retrieve all ACLs
        user1-token acl1 true
        user1-token acl2 true
        user1-token acl3 true
        user1-token acl4 true
        user1-token acl5 true
        user1-token acl6 true
        user1-token acl7 true
        user1-token acl8 true
        ;; user2 has no permission to retrieve any ACLs
        user2-token acl1 false
        user2-token acl2 false
        user2-token acl3 false
        user2-token acl4 false
        user2-token acl5 false
        user2-token acl6 false
        user2-token acl7 false
        user2-token acl8 false
        ;; user3 has permission to retrieve acl4, acl5
        user3-token acl1 false
        user3-token acl2 false
        user3-token acl3 false
        user3-token acl4 true
        user3-token acl5 true
        user3-token acl6 false
        user3-token acl7 false
        user3-token acl8 false))))

(deftest update-acl-test
  (testing "update acl successful case"
    (let [token (echo-util/login (test-util/conn-context) "admin")
          ;; Create the ACL with one set of attributes
          {concept-id :concept_id} (access-control/create-acl (test-util/conn-context) system-acl {:token token})
          ;; Now update it to be completely different
          resp (access-control/update-acl (test-util/conn-context) concept-id catalog-item-acl {:token token})]
      ;; Acceptance criteria: A concept id and revision id of the updated ACL should be returned.
      (is (= concept-id (:concept_id resp)))
      (is (= 2 (:revision_id resp)))
      ;; Acceptance criteria: An updated ACL can be retrieved after it is updated.
      ;; Acceptance criteria: An updated ACL can be found via the search API with any changes.
      (is (= catalog-item-acl (access-control/get-acl (test-util/conn-context) concept-id {:token token})))))
  (testing "update acl no permission"
    ;; Update the system ACL to remove permission to update single instance ACLs
    (access-control/update-acl (test-util/conn-context)
                               (:concept-id fixtures/*fixture-system-acl*)
                               {:system_identity {:target "ANY_ACL"}
                                :group_permissions [{:user_type "guest" :permissions ["read"]}]}
                               {:token transmit-config/mock-echo-system-token})
    (let [token (echo-util/login (test-util/conn-context) "user1")
          {:keys [status body]} (access-control/update-acl
                                 (test-util/conn-context)
                                 (:concept-id fixtures/*fixture-system-acl*)
                                 {:system_identity {:target "ANY_ACL"}
                                  :group_permissions [{:user_type "guest" :permissions ["update"]}]}
                                 {:token token :raw? true})]
      (is (= 401 status))
      (is (= ["Permission to update ACL is denied"] (:errors body))))))

(deftest update-single-instance-acl-test
  (let [token (echo-util/login (test-util/conn-context) "user1")
        group1 (test-util/ingest-group token
                                       {:name "group1"}
                                       ["user1"])
        group2 (test-util/ingest-group token
                                       {:name "group2"}
                                       ["user1"])
        group1-concept-id (:concept_id group1)
        group2-concept-id (:concept_id group2)
        {concept-id :concept_id} (access-control/create-acl
                                  (test-util/conn-context)
                                  (assoc-in single-instance-acl
                                            [:single_instance_identity :target_id]
                                            group1-concept-id)
                                  {:token token})
        resp (access-control/update-acl (test-util/conn-context)
                                        concept-id
                                        (assoc-in single-instance-acl
                                                  [:single_instance_identity :target_id]
                                                  group2-concept-id)
                                        {:token token})]
    (is (= concept-id (:concept_id resp)))
    (is (= 2 (:revision_id resp)))
    (is (= (assoc-in single-instance-acl [:single_instance_identity :target_id] group2-concept-id)
           (access-control/get-acl (test-util/conn-context) concept-id {:token token})))))

(deftest update-acl-errors-test
  (let [token (echo-util/login (test-util/conn-context) "admin")
        {system-concept-id :concept_id} (access-control/create-acl
                                         (test-util/conn-context)
                                         system-acl
                                         {:token token})
        group1 (test-util/ingest-group token {:name "group1"} ["user1"])
        group1-concept-id (:concept_id group1)
        {provider-concept-id :concept_id} (access-control/create-acl
                                           (test-util/conn-context)
                                           provider-acl
                                           {:token token})
        {catalog-concept-id :concept_id} (access-control/create-acl
                                          (test-util/conn-context)
                                          catalog-item-acl
                                          {:token token})
        {single-instance-concept-id :concept_id} (access-control/create-acl
                                                  (test-util/conn-context)
                                                  (assoc-in single-instance-acl
                                                            [:single_instance_identity :target_id]
                                                            group1-concept-id)
                                                  {:token token})]
    (are3 [re acl concept-id]
          (is (thrown-with-msg? Exception re (access-control/update-acl (test-util/conn-context) concept-id acl {:token token})))
          ;; Acceptance criteria: I receive an error if creating an ACL missing required fields.
          ;; Note: this tests a few fields, and is not exhaustive. The JSON schema handles this check.
          "Nil field value"
          #"#: required key \[.*\] not found"
          (dissoc system-acl :group_permissions)
          system-concept-id

          "Empty field value"
          #"#/group_permissions/0: required key \[.*\] not found"
          (assoc system-acl :group_permissions [{}])
          system-concept-id

          "Missing target"
          #"#/system_identity: required key \[target\] not found"
          (update-in system-acl [:system_identity] dissoc :target)
          system-concept-id

          "Acceptance criteria: I receive an error if updating an ACL with an invalid combination of fields. (Only one of system, provider, single instance, or catalog item identities)"
          #"#: required key \[catalog_item_identity\] not found"
          (assoc system-acl :provider_identity {:provider_id "PROV1"
                                                :target "INGEST_MANAGEMENT_ACL"})
          system-concept-id

          "Acceptance criteria: I receive an error if updating an ACL with a non-existent system identity, provider identity, or single instance identity target."
          #"#/system_identity/target: .* is not a valid enum value"
          (update-in system-acl [:system_identity] assoc :target "WHATEVER")
          system-concept-id

          "Value not found in enum"
          #"#/provider_identity/target: .* is not a valid enum value"
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
                     (access-control/acl-concept-id-url
                       (transmit-config/context->app-connection (test-util/conn-context) :access-control)
                       system-concept-id)
                     {:body "{\"bad-json:"
                      :headers {"Content-Type" "application/json"
                                "Authorization" token}
                      :throw-exceptions false})))))

    (testing "Acceptance criteria: I receive an error if updating an ACL with unsupported content type"
      (is
        (re-find #"The mime types specified in the content-type header \[application/xml\] are not supported."
                 (:body
                   (client/put
                     (access-control/acl-concept-id-url
                       (transmit-config/context->app-connection (test-util/conn-context) :access-control)
                       system-concept-id)
                     {:body (json/generate-string system-acl)
                      :headers {"Content-Type" "application/xml"
                                "Authorization" token}
                      :throw-exceptions false})))))))

(deftest update-acl-invalid-data-test
  (let [token (echo-util/login (test-util/conn-context) "admin")
        {concept-id :concept_id} (access-control/create-acl
                                  (test-util/conn-context)
                                  provider-acl
                                  {:token token})]
    (testing "updating an ACL to change its legacy guid"
      (is (thrown-with-msg?
            Exception
            #"ACL legacy guid cannot be updated, was \[ABCD-EFG-HIJK-LMNOP\] and now \[XYZ-EFG-HIJK-LMNOP\]"
            (access-control/update-acl
             (test-util/conn-context)
             concept-id
             (assoc provider-acl :legacy_guid "XYZ-EFG-HIJK-LMNOP")
             {:token token}))))
    (testing "Updating an ACL with an empty legacy guid is permitted"
      (let [response (access-control/update-acl (test-util/conn-context)
                                                concept-id
                                                (dissoc provider-acl :legacy_guid))]
        (is (= {:concept_id concept-id :revision_id 2} response))
        (is (= (:legacy_guid provider-acl) (:legacy_guid (access-control/get-acl
                                                          (test-util/conn-context)
                                                          concept-id))))))))

(deftest delete-acl-with-revision-id-test
  (let [token (echo-util/login-guest (test-util/conn-context))
        acl-concept-id (:concept_id
                        (access-control/create-acl
                         (test-util/conn-context)
                         {:group_permissions [{:permissions [:read]
                                               :user_type :guest}]
                          :catalog_item_identity {:name "PROV1 guest read"
                                                  :collection_applicable true
                                                  :provider_id "PROV1"}}
                         {:token token}))]
    (testing "422 is returned  when revision-id is invalid"
      (is (= {:status 422
              :body {:errors ["Invalid revision-id [invalid]. Cmr-Revision-id in the header must be a positive integer."]}
              :content-type :json}
             (access-control/delete-acl (test-util/conn-context) acl-concept-id {:token token :raw? true :cmr-revision-id "invalid"}))))

    (testing "409 is returned  when revision is <= the current revision of the acl"
      (is (= {:status 409
              :body {:errors ["Expected revision-id of [2] got [1] for [ACL1200000009-CMR]"]}
              :content-type :json}
             (access-control/delete-acl (test-util/conn-context) acl-concept-id {:token token :raw? true :cmr-revision-id 1}))))

    (testing "200 status, concept id and revision id of tombstone is returned on successful deletion."
      (is (= {:status 200
              :body {:revision-id 2
                     :concept-id acl-concept-id}
              :content-type :json}
             (access-control/delete-acl (test-util/conn-context) acl-concept-id {:token token :raw? true :cmr-revision-id 2}))))))

(deftest delete-acl-test
  (let [token (echo-util/login-guest (test-util/conn-context))
        acl-concept-id (:concept_id
                        (access-control/create-acl
                         (test-util/conn-context)
                         {:group_permissions [{:permissions [:read]
                                               :user_type :guest}]
                          :catalog_item_identity {:name "PROV1 guest read"
                                                  :collection_applicable true
                                                  :provider_id "PROV1"}}
                         {:token token}))
        coll1 (test-util/save-collection {:entry-title "coll1"
                                          :native-id "coll1"
                                          :entry-id "coll1"
                                          :short-name "coll1"
                                          :provider-id "PROV1"})]
    (testing "created ACL grants permissions (precursor to testing effectiveness of deletion)"
      (is (= {coll1 ["read"]}
             (json/parse-string
              (access-control/get-permissions (test-util/conn-context)
                                              {:concept_id coll1 :user_type "guest"}
                                              {:token token})))))
    (testing "404 status is returned if ACL does not exist"
      (is (= {:status 404
              :body {:errors ["ACL could not be found with concept id [ACL1234-NOPE]"]}
              :content-type :json}
             (access-control/delete-acl (test-util/conn-context) "ACL1234-NOPE" {:token token :raw? true}))))
    (testing "200 status, concept id and revision id of tombstone is returned on successful deletion."
      (is (= {:status 200
              :body {:revision-id 2
                     :concept-id acl-concept-id}
              :content-type :json}
             (access-control/delete-acl (test-util/conn-context) acl-concept-id {:token token :raw? true}))))
    (testing "404 is returned when trying to delete an ACL again"
      (is (= {:status 404
              :body {:errors ["ACL with concept id [ACL1200000009-CMR] was deleted."]}
              :content-type :json}
             (access-control/delete-acl (test-util/conn-context) acl-concept-id {:token token :raw? true}))))
    (testing "concept can no longer be retrieved through access control service"
      (is (= nil
             (access-control/get-acl (test-util/conn-context) acl-concept-id))))
    (testing "tombstone can be retrieved from Metadata DB"
      (is (= {:deleted true
              :revision-id 2
              :metadata ""
              :concept-id acl-concept-id}
             (select-keys
              (metadata-db2/get-latest-concept (test-util/conn-context) acl-concept-id)
              [:deleted :revision-id :metadata :concept-id]))))
    (testing "permissions granted by the ACL are no longer in effect"
      (is (= {coll1 []}
             (json/parse-string
              (access-control/get-permissions (test-util/conn-context)
                                              {:concept_id coll1 :user_type "guest"}
                                              {:token token})))))
    (testing "delete an ACL that is already deleted."
      (let [{:keys [status body]} (access-control/delete-acl
                                   (test-util/conn-context) acl-concept-id {:token token :raw? true})]
        (is (= 404 status))
        (is (= [(format "ACL with concept id [%s] was deleted." acl-concept-id)] (:errors body)))))
    (testing "delete ACL without permission."
      (let [acl-concept-id (:concept_id
                            (access-control/create-acl
                             (test-util/conn-context)
                             {:group_permissions [{:permissions [:read]
                                                   :user_type :guest}]
                              :catalog_item_identity {:name "PROV1 guest read"
                                                      :collection_applicable true
                                                      :provider_id "PROV1"}}
                             {:token token}))
            ;; update system ANY_ACL to not allow guest to delete ACLs
            _ (access-control/update-acl
               (test-util/conn-context)
               (:concept-id fixtures/*fixture-system-acl*)
               {:system_identity {:target "ANY_ACL"}
                :group_permissions [{:user_type "guest" :permissions ["read" "update"]}]}
               {:token token})
            ;; update *fixture-provider-acl* to not allow guest to delete PROV1 ACLs
            _ (access-control/update-acl
               (test-util/conn-context)
               (:concept-id fixtures/*fixture-provider-acl*)
               {:provider_identity {:provider_id "PROV1"
                                    :target "CATALOG_ITEM_ACL"}
                :group_permissions [{:user_type "guest"
                                     :permissions ["read" "update"]}]}
               {:token token})
            {:keys [status body]} (access-control/delete-acl
                                   (test-util/conn-context)
                                   acl-concept-id
                                   {:token token
                                    :raw? true})]
        (is (= 401 status))
        (is (= ["Permission to delete ACL is denied"] (:errors body)))))))

(deftest entry-titles-concept-ids-sync
  (let [token (echo-util/login-guest (test-util/conn-context))
        make-catalog-item (fn [name coll-id]
                            (-> catalog-item-acl
                                (assoc-in [:catalog_item_identity :name] name)
                                (assoc-in [:catalog_item_identity :collection_identifier] coll-id)))
        actual->set (fn [coll-id]
                      (-> coll-id
                          (update :entry_titles set)
                          (update :concept_ids set)))
        coll1 (test-util/save-collection {:entry-title "coll1 entry title"
                                          :short-name "coll1"
                                          :native-id "coll1"
                                          :provider-id "PROV1"})

        coll2 (test-util/save-collection {:entry-title "coll2 entry title"
                                          :short-name "coll2"
                                          :native-id "coll2"
                                          :provider-id "PROV1"})

        coll3 (test-util/save-collection {:entry-title "coll3 entry title"
                                          :short-name "coll3"
                                          :native-id "coll3"
                                          :provider-id "PROV1"})

        coll4 (test-util/save-collection {:entry-title "coll4 entry title"
                                          :short-name "coll4"
                                          :native-id "coll4"
                                          :provider-id "PROV1"})

        acl1 (access-control/create-acl
              (test-util/conn-context)
              (make-catalog-item "acl1" {:entry_titles ["coll1 entry title"
                                                        "coll2 entry title"
                                                        "coll3 entry title"]})
              {:token token})

        acl2 (access-control/create-acl
              (test-util/conn-context)
              (make-catalog-item "acl2" {:concept_ids [coll1 coll2 coll3]})
              {:token token})

        acl3 (access-control/create-acl
              (test-util/conn-context)
              (make-catalog-item "acl3" {:concept_ids [coll1 coll2 coll3]
                                         :entry_titles ["coll1 entry title"
                                                        "coll2 entry title"
                                                        "coll3 entry title"]})
              {:token token})

        acl4 (access-control/create-acl
              (test-util/conn-context)
              (make-catalog-item "acl4" {:concept_ids [coll1 coll2]
                                         :entry_titles ["coll2 entry title"
                                                        "coll3 entry title"]})
              {:token token})
        expected-collection-identifier {:concept_ids #{coll1 coll2 coll3}
                                        :entry_titles #{"coll1 entry title"
                                                        "coll2 entry title"
                                                        "coll3 entry title"}}
        expected-collection-identifier2 {:concept_ids #{coll4 coll3 coll2 coll1}
                                         :entry_titles #{"coll1 entry title"
                                                         "coll2 entry title"
                                                         "coll3 entry title"
                                                         "coll4 entry title"}}]
    (testing "create acls"
      (are3 [concept-id]
        (is (= expected-collection-identifier
               (actual->set
                (get-in (access-control/get-acl (test-util/conn-context) concept-id {:token token})
                        [:catalog_item_identity :collection_identifier]))))

        "Only entry-titles"
        (:concept_id acl1)

        "Only concept-ids"
        (:concept_id acl2)

        "Equal entry-titles and concept-ids"
        (:concept_id acl3)

        "Disjoint entry-titles and concept-ids"
        (:concept_id acl4)))

    (testing "update acls add collection via concept-ids"
      (access-control/update-acl
       (test-util/conn-context)
       (:concept_id acl4)
       (make-catalog-item "acl4" {:concept_ids [coll1 coll2 coll3 coll4]
                                  :entry_titles ["coll1 entry title"
                                                 "coll2 entry title"
                                                 "coll3 entry title"]})

       {:token token})
      (is (= expected-collection-identifier2
             (actual->set
              (get-in (access-control/get-acl (test-util/conn-context) (:concept_id acl4) {:token token})
                      [:catalog_item_identity :collection_identifier])))))

    (testing "update acls remove collection"
      (access-control/update-acl
       (test-util/conn-context)
       (:concept_id acl4)
       (make-catalog-item "acl4" {:concept_ids [coll1 coll2 coll3]
                                  :entry_titles ["coll1 entry title"
                                                 "coll2 entry title"
                                                 "coll3 entry title"]})

       {:token token})
      (is (= expected-collection-identifier
             (actual->set
              (get-in (access-control/get-acl (test-util/conn-context) (:concept_id acl4) {:token token})
                      [:catalog_item_identity :collection_identifier])))))

    (testing "update acls via entry-titles"
      (access-control/update-acl
       (test-util/conn-context)
       (:concept_id acl4)
       (make-catalog-item "acl4" {:concept_ids [coll1 coll2 coll3]
                                  :entry_titles ["coll1 entry title"
                                                 "coll2 entry title"
                                                 "coll3 entry title"
                                                 "coll4 entry title"]})

       {:token token})
      (is (= expected-collection-identifier2
             (actual->set
              (get-in (access-control/get-acl (test-util/conn-context) (:concept_id acl4) {:token token})
                      [:catalog_item_identity :collection_identifier])))))))

(deftest CMR-6233-dashboard-mdq-curator-acl-test
  (let [token (echo-util/login (test-util/conn-context) "admin")
        group1 (test-util/ingest-group token {:name "group1"} ["user1"])
        mdq-curator-acl {:group_permissions [{:user_type "guest"
                                              :permissions ["read"]}
                                             {:user_type "registered"
                                              :permissions ["read" "update"]}
                                             {:group_id (:concept_id group1)
                                              :permissions ["read" "create" "delete" "update"]}]
                          :system_identity {:target "DASHBOARD_MDQ_CURATOR"}}
        acl (access-control/create-acl (test-util/conn-context)
                                       mdq-curator-acl
                                       {:token token})]
    (is (= mdq-curator-acl
           (access-control/get-acl (test-util/conn-context)
                                   (:concept_id acl)
                                   {:token token})))

    (testing "DASHBOARD MDQ CURATOR  Acl permissions"
      (are3 [perms params]
        (is (= {"DASHBOARD_MDQ_CURATOR" perms}
               (json/parse-string
                 (access-control/get-permissions
                  (test-util/conn-context)
                  params
                  {:token token}))))

        "user in group permissions DASHBOARD_MDQ_CURATOR"
        ["read" "create" "update" "delete"]
        {:user_id "user1"
         :system_object "DASHBOARD_MDQ_CURATOR"}

        "guest permissions DASHBOARD_MDQ_CURATOR"
        ["read"]
        {:user_type "guest"
         :system_object "DASHBOARD_MDQ_CURATOR"}

        "user not in group permissions DASHBOARD_MDQ_CURATOR"
        ["read" "update"]
        {:user_id "user2"
         :system_object "DASHBOARD_MDQ_CURATOR"}))))

(deftest CMR-6147-email-subscription-acl-test
  (let [token (echo-util/login (test-util/conn-context) "admin")
        group1 (test-util/ingest-group token {:name "group1"} ["user1"])
        subscription-acl {:group_permissions [{:user_type "guest"
                                               :permissions ["read"]}
                                              {:user_type "registered"
                                               :permissions ["update"]}
                                              {:group_id (:concept_id group1)
                                               :permissions ["read" "update"]}]
                          :provider_identity {:provider_id "PROV1"
                                              :target "SUBSCRIPTION_MANAGEMENT"}}
        acl (access-control/create-acl (test-util/conn-context)
                                       subscription-acl
                                       {:token token})]
    (is (= subscription-acl
           (access-control/get-acl (test-util/conn-context)
                                   (:concept_id acl)
                                   {:token token})))

    (testing "EMAIL SUBSCRIPTION Acl permissions"
      (are3 [perms params]
        (is (= {"SUBSCRIPTION_MANAGEMENT" perms}
               (json/parse-string
                 (access-control/get-permissions
                  (test-util/conn-context)
                  params
                  {:token token}))))

        "user in group permissions SUBSCRIPTION_MANAGEMENT"
        ["read" "update"]
        {:user_id "user1"
         :provider "PROV1"
         :target "SUBSCRIPTION_MANAGEMENT"}

        "guest permissions SUBSCRIPTION_MANAGEMENT"
        ["read"]
        {:user_type "guest"
         :provider "PROV1"
         :target "SUBSCRIPTION_MANAGEMENT"}

        "user not in group permissions SUBSCRIPTION_MANAGEMENT"
        ["update"]
        {:user_id "user2"
         :provider "PROV1"
         :target "SUBSCRIPTION_MANAGEMENT"}))))

(deftest CMR-5797-draft-mmt-acl-test
  (let [token (echo-util/login (test-util/conn-context) "admin")
        user1-token (echo-util/login (test-util/conn-context) "user1")
        user2-token (echo-util/login (test-util/conn-context) "user2")
        guest-token (echo-util/login-guest (test-util/conn-context))
        group1 (test-util/ingest-group user1-token {:name "group1"} ["user1"])
        group2 (test-util/ingest-group user1-token {:name "group2"} ["user2"])
        group1-concept-id (:concept_id group1)
        group2-concept-id (:concept_id group2)
        draft-acl {:group_permissions [{:user_type "guest"
                                        :permissions ["read"]}
                                       {:user_type "registered"
                                        :permissions ["read" "update"]}
                                       {:group_id group1-concept-id
                                        :permissions ["read" "create" "delete" "update"]}]
                   :provider_identity {:provider_id "PROV1"
                                       :target "NON_NASA_DRAFT_USER"}}
        draft-acl2 {:group_permissions [{:user_type "guest"
                                         :permissions ["read"]}
                                        {:user_type "registered"
                                         :permissions ["read" "update"]}
                                        {:group_id group1-concept-id
                                         :permissions ["read" "create" "delete" "update"]}]
                    :provider_identity {:provider_id "PROV1"
                                        :target "NON_NASA_DRAFT_APPROVER"}}
        acl (access-control/create-acl (test-util/conn-context)
                                       draft-acl
                                       {:token token})
        acl2 (access-control/create-acl (test-util/conn-context)
                                       draft-acl2
                                       {:token token})]
    (is (= draft-acl
           (access-control/get-acl (test-util/conn-context)
                                   (:concept_id acl)
                                   {:token token})))
    (is (= draft-acl2
           (access-control/get-acl (test-util/conn-context)
                                   (:concept_id acl2)
                                   {:token token})))

    (testing "MMT DRAFT NON NASA Acl permissions"
      (are3 [perms params]
        (is (= {"NON_NASA_DRAFT_USER" perms}
               (json/parse-string
                 (access-control/get-permissions
                  (test-util/conn-context)
                  params
                  {:token token}))))

        "user1 permissions NON_NASA_DRAFT_USER"
        ["read" "create" "update" "delete"]
        {:user_id "user1"
         :provider "PROV1"
         :target "NON_NASA_DRAFT_USER"}

        "guest permissions NON_NASA_DRAFT_USER"
        ["read"]
        {:user_type "guest"
         :provider "PROV1"
         :target "NON_NASA_DRAFT_USER"}

        "user2 permissions NON_NASA_DRAFT_USER"
        ["read" "update"]
        {:user_id "user2"
         :provider "PROV1"
         :target "NON_NASA_DRAFT_USER"})

      (are3 [perms params]
        (is (= {"NON_NASA_DRAFT_APPROVER" perms}
               (json/parse-string
                 (access-control/get-permissions
                  (test-util/conn-context)
                  params
                  {:token token}))))

        "user1 permissions NON_NASA_DRAFT_APPROVER"
        ["read" "create" "update" "delete"]
        {:user_id "user1"
         :provider "PROV1"
         :target "NON_NASA_DRAFT_APPROVER"}

        "guest permissions NON_NASA_DRAFT_APPROVER"
        ["read"]
        {:user_type "guest"
         :provider "PROV1"
         :target "NON_NASA_DRAFT_APPROVER"}

        "user2 permissions NON_NASA_DRAFT_APPROVER"
        ["read" "update"]
        {:user_id "user2"
         :provider "PROV1"
         :target "NON_NASA_DRAFT_APPROVER"}))))


(deftest CMR-5128-mmt-dashboard-acl-test
  (let [token (echo-util/login (test-util/conn-context) "admin")
        user1-token (echo-util/login (test-util/conn-context) "user1")
        guest-token (echo-util/login-guest (test-util/conn-context))
        group1 (test-util/ingest-group user1-token {:name "group1"} ["user1"])
        group1-concept-id (:concept_id group1)
        dash-daac {:group_permissions [{:user_type "guest"
                                        :permissions ["read"]}
                                       {:group_id group1-concept-id
                                        :permissions ["read" "create" "delete" "update"]}]
                   :provider_identity {:provider_id "PROV1"
                                       :target "DASHBOARD_DAAC_CURATOR"}}
        acl1 (access-control/create-acl (test-util/conn-context)
                                        dash-daac
                                        {:token token})
        dash-admin {:group_permissions [{:user_type "guest"
                                         :permissions ["read"]}
                                        {:group_id group1-concept-id
                                         :permissions ["read" "create" "delete" "update"]}]
                    :system_identity {:target "DASHBOARD_ADMIN"}}
        acl2 (access-control/create-acl (test-util/conn-context)
                                        dash-admin
                                        {:token token})
        dash-arch {:group_permissions [{:user_type "guest"
                                        :permissions ["read"]}
                                       {:group_id group1-concept-id
                                        :permissions ["read" "create" "delete" "update"]}]
                   :system_identity {:target "DASHBOARD_ARC_CURATOR"}}
        acl3 (access-control/create-acl (test-util/conn-context)
                                        dash-arch
                                        {:token token})]

    (is (= dash-daac
           (access-control/get-acl (test-util/conn-context) (:concept_id acl1) {:token token})))
    (is (= dash-admin
           (access-control/get-acl (test-util/conn-context) (:concept_id acl2) {:token token})))
    (is (= dash-arch
           (access-control/get-acl (test-util/conn-context) (:concept_id acl3) {:token token})))

    (is (= {"DASHBOARD_DAAC_CURATOR" ["read"]}
           (json/parse-string
             (access-control/get-permissions
              (test-util/conn-context)
              {:user_type "guest"
               :provider "PROV1"
               :target "DASHBOARD_DAAC_CURATOR"}
              {:token token}))))
    (is (= {"DASHBOARD_ADMIN" ["read"]}
           (json/parse-string
             (access-control/get-permissions
              (test-util/conn-context)
              {:user_type "guest"
               :system_object "DASHBOARD_ADMIN"}
              {:token token}))))
    (is (= {"DASHBOARD_ARC_CURATOR" ["read"]}
           (json/parse-string
             (access-control/get-permissions
              (test-util/conn-context)
              {:user_type "guest"
               :system_object "DASHBOARD_ARC_CURATOR"}
              {:token token}))))
    (is (= {"DASHBOARD_DAAC_CURATOR" ["read" "create" "update" "delete"]}
           (json/parse-string
             (access-control/get-permissions
              (test-util/conn-context)
              {:user_id "user1"
               :provider "PROV1"
               :target "DASHBOARD_DAAC_CURATOR"}
              {:token token}))))
    (is (= {"DASHBOARD_ADMIN" ["read" "create" "update" "delete"]}
           (json/parse-string
             (access-control/get-permissions
              (test-util/conn-context)
              {:user_id "user1"
               :system_object "DASHBOARD_ADMIN"}
              {:token token}))))
    (is (= {"DASHBOARD_ARC_CURATOR" ["read" "create" "update" "delete"]}
           (json/parse-string
             (access-control/get-permissions
              (test-util/conn-context)
              {:user_id "user1"
               :system_object "DASHBOARD_ARC_CURATOR"}
              {:token token}))))))
