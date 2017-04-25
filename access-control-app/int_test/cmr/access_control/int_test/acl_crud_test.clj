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
              (fixtures/grant-all-group-fixture ["PROV1" "PROV2"])
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

;; The following test covers creating simple but valid catalog item identity. We permit collection_applicable false
;; and collection_identifier when granule_applicable is true.

(deftest create-catalog-item-identity-acl-test
  (testing "with collection_identifier, collection_applicable true, and granule_applicable false"
    (is (= 1 (:revision_id
               (ac/create-acl (u/conn-context)
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
               (ac/create-acl (u/conn-context)
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
  (let [token (e/login (u/conn-context) "user1")
        group1 (u/ingest-group token {:name "group1" :provider_id "PROV1"} ["user1"])
        group1-concept-id (:concept_id group1)]
    ;; Update the system ACL to remove permission to create single instance ACLs
    (ac/update-acl (u/conn-context)
                   (:concept-id fixtures/*fixture-system-acl*)
                   {:system_identity {:target "ANY_ACL"}
                    :group_permissions [{:user_type "registered" :permissions ["read"]}]}
                   {:token transmit-config/mock-echo-system-token})
    (let [{:keys [status body]} (ac/create-acl
                                 (u/conn-context)
                                 {:group_permissions [{:user_type "registered" :permissions ["update" "delete"]}]
                                  :single_instance_identity {:target_id group1-concept-id
                                                             :target "GROUP_MANAGEMENT"}}
                                 {:token token :raw? true})]
      (is (= 401 status))
      (is (= ["Permission to create ACL is denied"] (:errors body))))
    ;; Create a provider-specific ACL granting permission to create ACLs targeting groups
    (ac/create-acl (u/conn-context)
                   {:group_permissions [{:user_type "registered" :permissions ["create"]}]
                    :provider_identity {:target "PROVIDER_OBJECT_ACL"
                                        :provider_id "PROV1"}}
                   {:token transmit-config/mock-echo-system-token})
    (is (= 1 (:revision_id
              (ac/create-acl (u/conn-context)
                             {:group_permissions [{:user_type "registered" :permissions ["update" "delete"]}]
                              :single_instance_identity {:target_id group1-concept-id
                                                         :target "GROUP_MANAGEMENT"}}
                             {:token token}))))))


(deftest create-provider-acl-permission-test
  ;; Tests user permission to create provider acls
  (let [token-user1 (e/login (u/conn-context) "user1")
        guest-token (e/login-guest (u/conn-context))
        token-user2 (e/login (u/conn-context) "user2")
        token-user3 (e/login (u/conn-context) "user3")
        any-acl-group (u/ingest-group token-user1 {:name "any acl group"} ["user1"])
        any-acl-group-id (:concept_id any-acl-group)
        prov-obj-acl-group (u/ingest-group token-user1 {:name "prov obj acl group"} ["user2"])
        prov-obj-acl-group-id (:concept_id prov-obj-acl-group)
        cat-item-prov-acl-group (u/ingest-group token-user1 {:name "cat item prov acl"} ["user3"])
        cat-item-prov-acl-group-id (:concept_id cat-item-prov-acl-group)]

    ;; Update ANY_ACL fixture to remove permissions to create from registered users.
    (ac/update-acl (merge {:token guest-token} (u/conn-context))
                   (:concept-id fixtures/*fixture-system-acl*)
                   {:system_identity {:target "ANY_ACL"}
                    :group_permissions [{:user_type "guest" :permissions ["create" "update"]}]})

    (testing "Without permissions"
      (are3 [token acl]
        (let [{:keys [status body]} (ac/create-acl
                                     (merge {:token token} (u/conn-context)) acl {:raw? true})]
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
      (ac/update-acl (merge {:token guest-token} (u/conn-context))
                     (:concept-id fixtures/*fixture-system-acl*)
                     {:system_identity {:target "ANY_ACL"}
                      :group_permissions [{:user_type "guest" :permissions ["create"]}
                                          {:group_id any-acl-group-id :permissions ["create"]}]})
      ;; Create provider acl target PROVIDER_OBJECT_ACL and grant ACL create permission to user2,
      ;; which is a member of the prov-obj-acl-group.
      (ac/create-acl (merge {:token guest-token} (u/conn-context))
                     {:provider_identity {:provider_id "PROV2" :target "PROVIDER_OBJECT_ACL"}
                      :group_permissions [{:user_type "guest" :permissions ["create"]}
                                          {:group_id prov-obj-acl-group-id :permissions ["create"]}]})

      ;; verify that user1 and user2 can now create their permitted ACLs
      (are3 [token acl]
        (let [resp (ac/create-acl (merge {:token token} (u/conn-context)) acl)]
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
      (let [{:keys [status body]} (ac/create-acl
                                   (merge {:token token-user3} (u/conn-context))
                                   {:provider_identity {:provider_id "PROV2" :target "CATALOG_ITEM_ACL"}
                                    :group_permissions [{:user_type "guest" :permissions ["read"]}]}
                                   {:raw? true})]
        (is (= 401 status))
        (is (= ["Permission to create ACL is denied"] (:errors body)))))))

(deftest create-system-level-acl-permission-test
  (let [token-user1 (e/login (u/conn-context) "user1")
        guest-token (e/login-guest (u/conn-context))
        token-user2 (e/login (u/conn-context) "user2")
        group1 (u/ingest-group token-user1 {:name "group1"} ["user1"])
        group1-concept-id (:concept_id group1)
        ;; Update ANY_ACL fixture to remove permissions from guest and registered,
        ;; and replace it with group1
        _ (ac/update-acl (merge {:token token-user1} (u/conn-context)) (:concept-id fixtures/*fixture-system-acl*)
                         (assoc (assoc-in system-acl
                                          [:system_identity :target] "ANY_ACL")
                                :group_permissions [{:group_id group1-concept-id :permissions ["read" "create"]},
                                                    {:user_type :guest :permissions ["read"]}]))]
    (testing "create system level ACL without permission"
      (are3 [token]
        (let [{:keys [status body]} (ac/create-acl (assoc (u/conn-context) :token token)
                                                   system-acl {:raw? true})]
          (is (= 401 status))
          (is (= ["Permission to create ACL is denied"] (:errors body))))

        "Try to create ACL as guest"
        guest-token

        "Try to create ACL as user2"
        token-user2))

    (testing "create system level ACL with permission, user1 has permission"
      (let [{:keys [concept_id revision_id]} (ac/create-acl
                                              (assoc (u/conn-context) :token token-user1) system-acl)]
        (is (re-find #"^ACL.*" concept_id))
        (is (= 1 revision_id))))))

(deftest acl-targeting-group-with-legacy-guid-test
  (let [admin-token (e/login (u/conn-context) "admin")
        ;; as an admin user, create a group with a legacy_guid
        created-group (:concept_id (ac/create-group (u/conn-context)
                                                    {:name "group"
                                                     :description "a group"
                                                     :legacy_guid "normal-group-guid"
                                                     :members ["user1"]}
                                                    {:token admin-token}))]

    ;; Update the system-level ANY_ACL to avoid granting ACL creation permission to "user1", since it normally
    ;; grants this permission to all registered users.
    (ac/update-acl (u/conn-context)
                   (:concept-id fixtures/*fixture-system-acl*)
                   {:group_permissions [{:group_id created-group
                                         :permissions [:create :read :update :delete]}]
                    :system_identity {:target "ANY_ACL"}}
                   {:token admin-token})

    ;; Update the PROV1 CATALOG_ITEM_ACL ACL to grant permission explicitly to only the group which "user1" belongs to.
    (ac/update-acl (u/conn-context)
                   (:concept-id fixtures/*fixture-provider-acl*)
                   {:group_permissions [{:group_id created-group
                                         :permissions [:create :read :update :delete]}]
                    :provider_identity {:provider_id "PROV1"
                                        :target "CATALOG_ITEM_ACL"}}
                   {:token admin-token})

    ;; As "user1" try to create a catalog item ACL for PROV1.
    (let [user-token (e/login (u/conn-context) "user1" ["normal-group-guid"])]
      (is (= 1 (:revision_id
                (ac/create-acl (u/conn-context)
                               {:group_permissions [{:user_type :registered
                                                     :permissions [:read]}]
                                :catalog_item_identity {:provider_id "PROV1"
                                                        :name "PROV1 collections ACL"
                                                        :collection_applicable true}}
                               {:token user-token})))))))

(deftest create-catalog-item-acl-permission-test
  ;; Tests creation permissions of catalog item acls
  (let [user1-token (e/login (u/conn-context) "user1")
        guest-token (e/login-guest (u/conn-context))
        user2-token (e/login (u/conn-context) "user2")
        group1 (u/ingest-group user1-token {:name "group1"} ["user1"])
        group1-concept-id (:concept_id group1)
        acl-to-create (assoc-in catalog-item-acl [:catalog_item_identity :provider_id] "PROV2")]
    ;; create ACL to grant user1 permission to create catalog item ACL on PROV2
    (ac/create-acl (u/conn-context) {:group_permissions [{:group_id group1-concept-id
                                                          :permissions ["create"]}]
                                     :provider_identity {:provider_id "PROV2"
                                                         :target "CATALOG_ITEM_ACL"}})
    ;; update system ACL to not allow guest or registered users to create any ACLs
    (ac/update-acl (u/conn-context)
                   (:concept-id fixtures/*fixture-system-acl*)
                   {:system_identity {:target "ANY_ACL"}
                    :group_permissions [{:user_type "guest" :permissions ["read"]}]})

    (testing "create catalog item ACL without permission"
      (are3 [token]
        (let [{:keys [status body]} (ac/create-acl (u/conn-context)
                                                   acl-to-create {:token token :raw? true})]
          (is (= 401 status))
          (is (= ["Permission to create ACL is denied"] (:errors body))))

        "Try to create ACL as guest"
        guest-token

        "Try to create ACL as user2"
        user2-token))

    (testing "create catalog item ACL with permission, user1 has permission"
      (let [{:keys [concept_id revision_id]} (ac/create-acl
                                              (u/conn-context) acl-to-create {:token user1-token})]
        (is (re-find #"^ACL.*" concept_id))
        (is (= 1 revision_id))))))

(deftest create-acl-errors-test
  (let [token (e/login (u/conn-context) "admin")
        group1 (u/ingest-group token {:name "group1"} ["user1"])
        group1-concept-id (:concept_id group1)
        provider-id (:provider_id (:provider_identity (ac/create-acl (u/conn-context) provider-acl {:token token})))]
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
  (let [token (e/login-guest (u/conn-context))]
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

          "At least one of a range (min and/or max) or include_undefined value must be specified (collection_identifier)"
          ["either include_undefined_value or the combination of min_value and max_value must be specified"]
          {:group_permissions [{:user_type "guest" :permissions ["read"]}]
           :catalog_item_identity {:name "A Catalog Item ACL"
                                   :provider_id "PROV1"
                                   :collection_applicable true
                                   :collection_identifier {:access_value {:include_undefined_value false}}}}

          "min and max value must be valid numbers if specified"
          ["/catalog_item_identity/collection_identifier/access_value/min_value instance type (string) does not match any allowed primitive type (allowed: [\"integer\",\"number\"])"]
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
      (is (= {:revision_id 1 :status 200}
             (select-keys
               (u/create-acl token {:group_permissions [{:user_type "guest" :permissions ["read"]}]
                                    :catalog_item_identity {:name "A real live catalog item ACL"
                                                            :provider_id "PROV1"
                                                            :collection_applicable true
                                                            :collection_identifier {:entry_titles ["coll1 v1"]}}})
               [:revision_id :status]))))

    (testing "long entry titles"
      (u/save-collection {:entry-title "coll2 xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
                          :native-id "coll2"
                          :entry-id "coll2"
                          :short-name "coll2"
                          :version "v1"
                          :provider-id "PROV1"})
      (let [result (u/create-acl token {:group_permissions [{:user_type "guest" :permissions ["read"]}]
                                        :catalog_item_identity {:name "Catalog item ACL with a long entry title"
                                                                :provider_id "PROV1"
                                                                :collection_applicable true
                                                                :collection_identifier {:entry_titles ["coll2 xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"]}}})]
        (is (= 1 (:revision_id result)))))))

(deftest create-duplicate-acl-test
  (let [token (e/login-guest (u/conn-context))]
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
  (testing "get acl general case"
    (let [token (e/login (u/conn-context) "admin")
          concept-id (:concept_id (ac/create-acl (u/conn-context) system-acl {:token token}))]
      ;; Acceptance criteria: A created ACL can be retrieved after it is created.
      (is (= system-acl (ac/get-acl (u/conn-context) concept-id {:token token})))
      (let [resp (ac/get-acl (u/conn-context) "NOTACONCEPTID" {:token token :raw? true})]
        (is (= 400 (:status resp)))
        (is (= ["Concept-id [NOTACONCEPTID] is not valid."] (:errors (:body resp)))))
      (let [resp (ac/get-acl (u/conn-context) "ACL999999-CMR" {:token token :raw? true})]
        (is (= 404 (:status resp)))
        (is (= ["ACL could not be found with concept id [ACL999999-CMR]"]
               (:errors (:body resp)))))))

  (testing "get acl with group id"
    (let [token (e/login (u/conn-context) "admin")
          group1-legacy-guid "group1-legacy-guid"
          group1 (u/ingest-group token
                                 {:name "group1"
                                  :legacy_guid group1-legacy-guid}
                                 ["user1"])
          group2 (u/ingest-group token
                                 {:name "group2"}
                                 ["user1"])
          group1-concept-id (:concept_id group1)
          group2-concept-id (:concept_id group2)

          ;; ACL associated with a group that has legacy guid
          acl1 (assoc-in (u/system-acl "TAG_GROUP")
                         [:group_permissions 0]
                         {:permissions ["create"] :group_id group1-concept-id})

          ;; ACL associated with a group that does not have legacy guid
          acl2 (assoc-in (u/system-acl "ARCHIVE_RECORD")
                         [:group_permissions 0]
                         {:permissions ["delete"] :group_id group2-concept-id})
          ;; SingleInstanceIdentity ACL with a group that has legacy guid
          acl3 (u/single-instance-acl group1-concept-id)
          ;; SingleInstanceIdentity ACL with a group that does not have legacy guid
          acl4 (u/single-instance-acl group2-concept-id)]

      (are3 [expected-acl acl]
        (let [concept-id (:concept_id (ac/create-acl (u/conn-context) acl {:token token}))]
          (is (= expected-acl (ac/get-acl (u/conn-context) concept-id
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
  (let [user1-token (e/login (u/conn-context) "user1")
        user2-token (e/login (u/conn-context) "user2")
        user3-token (e/login (u/conn-context) "user3")
        guest-token (e/login-guest (u/conn-context))
        group1 (u/ingest-group user1-token
                               {:name "any acl read"}
                               ["user1"])
        group2 (u/ingest-group user1-token
                               {:name "without any acl read"}
                               ["user2"])
        group3 (u/ingest-group user1-token
                               {:name "provider object prov1 read"}
                               ["user3"])
        group1-concept-id (:concept_id group1)
        group2-concept-id (:concept_id group2)
        group3-concept-id (:concept_id group3)

        ;; remove ANY_ACL read to all users except user1
        _ (ac/update-acl (u/conn-context) (:concept-id fixtures/*fixture-system-acl*)
                         (assoc-in (u/system-acl "ANY_ACL")
                                   [:group_permissions 0]
                                   {:permissions ["read" "create"] :group_id group1-concept-id}))

        acl1 (u/ingest-acl user1-token (assoc-in (u/system-acl "INGEST_MANAGEMENT_ACL")
                                                 [:group_permissions 0]
                                                 {:permissions ["read"] :group_id group1-concept-id}))
        acl2 (u/ingest-acl user1-token (assoc-in (u/system-acl "ARCHIVE_RECORD")
                                                 [:group_permissions 0]
                                                 {:permissions ["delete"] :group_id group2-concept-id}))
        acl3 (u/ingest-acl user1-token (u/system-acl "SYSTEM_OPTION_DEFINITION_DEPRECATION"))
        acl4 (u/ingest-acl user1-token (assoc (u/provider-acl "PROVIDER_OBJECT_ACL")
                                              :group_permissions
                                              [{:group_id group3-concept-id :permissions ["read"]}]))
        acl5 (u/ingest-acl user1-token (u/provider-acl "OPTION_DEFINITION"))
        acl6 (u/ingest-acl user1-token (assoc-in (u/provider-acl "OPTION_DEFINITION")
                                                 [:provider_identity :provider_id] "PROV2"))
        ;; Create an ACL with a catalog item identity for PROV1
        acl7 (u/ingest-acl user1-token {:group_permissions [{:user_type "registered" :permissions ["read"]}]
                                        :catalog_item_identity {:provider_id "PROV1"
                                                                :name "PROV1 All Collections ACL"
                                                                :collection_applicable true}})
        acl8 (u/ingest-acl user1-token {:group_permissions [{:user_type "registered" :permissions ["read"]}]
                                        :catalog_item_identity {:provider_id "PROV2"
                                                                :name "PROV2 All Collections ACL"
                                                                :collection_applicable true}})
        permission-granted? (fn [token acl granted?]
                              (let [{:keys [status]} (ac/get-acl (u/conn-context)
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
      (ac/update-acl (u/conn-context)
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
  (testing "update acl no permission"
    ;; Update the system ACL to remove permission to update single instance ACLs
    (ac/update-acl (u/conn-context)
                   (:concept-id fixtures/*fixture-system-acl*)
                   {:system_identity {:target "ANY_ACL"}
                    :group_permissions [{:user_type "guest" :permissions ["read"]}]}
                   {:token transmit-config/mock-echo-system-token})
    (let [token (e/login (u/conn-context) "user1")
          {:keys [status body]} (ac/update-acl
                                 (u/conn-context)
                                 (:concept-id fixtures/*fixture-system-acl*)
                                 {:system_identity {:target "ANY_ACL"}
                                  :group_permissions [{:user_type "guest" :permissions ["update"]}]}
                                 {:token token :raw? true})]
      (is (= 401 status))
      (is (= ["Permission to update ACL is denied"] (:errors body))))))

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
    (is (= (assoc-in single-instance-acl [:single_instance_identity :target_id] group2-concept-id)
           (ac/get-acl (u/conn-context) concept-id {:token token})))))

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
        {concept-id :concept_id} (ac/create-acl (u/conn-context) provider-acl {:token token})]
    (testing "updating an ACL to change its legacy guid"
      (is (thrown-with-msg?
            Exception
            #"ACL legacy guid cannot be updated, was \[ABCD-EFG-HIJK-LMNOP\] and now \[XYZ-EFG-HIJK-LMNOP\]"
            (ac/update-acl (u/conn-context) concept-id
                           (assoc provider-acl :legacy_guid "XYZ-EFG-HIJK-LMNOP") {:token token}))))
    (testing "Updating an ACL with an empty legacy guid is permitted"
      (let [response (ac/update-acl (u/conn-context) concept-id (dissoc provider-acl :legacy_guid))]
        (is (= {:concept_id concept-id :revision_id 2} response))
        (is (= (:legacy_guid provider-acl) (:legacy_guid (ac/get-acl (u/conn-context) concept-id))))))))

(deftest delete-acl-test
  (let [token (e/login-guest (u/conn-context))
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
                     :concept-id acl-concept-id}
              :content-type :json}
             (ac/delete-acl (u/conn-context) acl-concept-id {:token token :raw? true}))))
    (testing "404 is returned when trying to delete an ACL again"
      (is (= {:status 404
              :body {:errors ["ACL with concept id [ACL1200000009-CMR] was deleted."]}
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
                                  {:token token})))))
    (testing "delete an ACL that is already deleted."
      (let [{:keys [status body]} (ac/delete-acl
                                   (u/conn-context) acl-concept-id {:token token :raw? true})]
        (is (= 404 status))
        (is (= [(format "ACL with concept id [%s] was deleted." acl-concept-id)] (:errors body)))))
    (testing "delete ACL without permission."
      (let [acl-concept-id (:concept_id
                            (ac/create-acl (u/conn-context)
                                           {:group_permissions [{:permissions [:read]
                                                                 :user_type :guest}]
                                            :catalog_item_identity {:name "PROV1 guest read"
                                                                    :collection_applicable true
                                                                    :provider_id "PROV1"}}
                                           {:token token}))
            ;; update system ANY_ACL to not allow guest to delete ACLs
            _ (ac/update-acl (u/conn-context)
                             (:concept-id fixtures/*fixture-system-acl*)
                             {:system_identity {:target "ANY_ACL"}
                              :group_permissions [{:user_type "guest" :permissions ["read" "update"]}]}
                             {:token token})
            ;; update *fixture-provider-acl* to not allow guest to delete PROV1 ACLs
            _ (ac/update-acl (u/conn-context)
                             (:concept-id fixtures/*fixture-provider-acl*)
                             {:provider_identity {:provider_id "PROV1"
                                                  :target "CATALOG_ITEM_ACL"}
                              :group_permissions [{:user_type "guest"
                                                   :permissions ["read" "update"]}]}
                             {:token token})
            {:keys [status body]} (ac/delete-acl
                                   (u/conn-context) acl-concept-id {:token token :raw? true})]
        (is (= 401 status))
        (is (= ["Permission to delete ACL is denied"] (:errors body)))))))
