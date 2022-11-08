(ns cmr.access-control.int-test.permission-check-test
  "Tests the access control permission check routes."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clj-time.core :as t]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.access-control.int-test.fixtures :as fixtures]
   [cmr.access-control.test.util :as u]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.transmit.access-control :as ac]
   [cmr.transmit.config :as transmit-config]))

(use-fixtures :once (fixtures/int-test-fixtures))

(use-fixtures :each
              (fixtures/reset-fixture {"prov1guid" "PROV1" "prov2guid" "PROV2"}
                                      ["user1" "user2"])
              (fn [f]
                (e/grant-registered-ingest (u/conn-context) "PROV1")
                (f))
              (fixtures/grant-all-group-fixture ["PROV1" "PROV2"])
              (fixtures/grant-all-acl-fixture))

(deftest permission-get-and-post-request-test
  (let [save-basic-collection (fn [short-name]
                                  (u/save-collection {:entry-title (str short-name " entry title")
                                                      :short-name short-name
                                                      :native-id short-name
                                                      :provider-id "PROV1"}))
        coll1 (save-basic-collection "coll1")
        coll2 (save-basic-collection "coll2")
        coll3 (save-basic-collection "coll3")
        coll4 (save-basic-collection "coll4")
        post-data-body (str "user_type=guest"
                         "&concept_id=" coll1
                         "&concept_id=" coll2
                         "&concept_id=" coll3
                         "&concept_id=" coll4)]
    (testing "permissions endpoint allows post request"
      (let [permissions-url (ac/acl-permission-url
                                         (transmit-config/context->app-connection
                                          (u/conn-context)
                                          :access-control))
            post-response (client/post permissions-url
                            {:basic-auth ["user" "pass"]
                             :body post-data-body
                             :content-type "application/x-www-form-urlencoded"})
            get-response (client/get permissions-url
                            {:query-params {"user_type" "guest"
                                            "concept_id" [coll1 coll2 coll3 coll4]}})
            post-body (get post-response :body)
            get-body (get get-response :body)]

        (is (= get-body post-body))))))

(deftest multi-provider-permissions-test
  (testing "User permissions for same target and user accross two providers"
    (let [group1 (u/create-group
                  (transmit-config/echo-system-token)
                  (u/make-group
                   {:name "test-group-1"
                    :members ["user1"]
                    :provider_id "PROV1"}))
          group2 (u/create-group
                  (transmit-config/echo-system-token)
                  (u/make-group
                   {:name "test-group-2"
                    :members ["user1"]
                    :provider_id "PROV2"}))]
      (ac/create-acl
       (u/conn-context)
       {:group_permissions [{:permissions [:create]
                             :group_id (:concept_id group1)}]
        :provider_identity {:provider_id "PROV1"
                            :target "NON_NASA_DRAFT_APPROVER"}}
       {:token (transmit-config/echo-system-token)})
      (ac/create-acl
       (u/conn-context)
       {:group_permissions [{:permissions [:delete]
                             :group_id (:concept_id group2)}]
        :provider_identity {:provider_id "PROV2"
                            :target "NON_NASA_DRAFT_APPROVER"}}
       {:token (transmit-config/echo-system-token)})
      (ac/create-acl
       (u/conn-context)
       {:group_permissions [{:permissions [:update]
                             :group_id (:concept_id group1)}]
        :provider_identity {:provider_id "PROV1"
                            :target "SUBSCRIPTION_MANAGEMENT"}}
       {:token (transmit-config/echo-system-token)})
      (ac/create-acl
       (u/conn-context)
       {:group_permissions [{:permissions [:update]
                             :group_id (:concept_id group2)}]
        :provider_identity {:provider_id "PROV2"
                            :target "SUBSCRIPTION_MANAGEMENT"}}
       {:token (transmit-config/echo-system-token)})

      (is (= {"NON_NASA_DRAFT_APPROVER" ["create"]}
             (json/parse-string
              (ac/get-permissions
               (u/conn-context)
               {:target "NON_NASA_DRAFT_APPROVER"
                :provider "PROV1"
                :user_id "user1"}))))
      (is (= {"NON_NASA_DRAFT_APPROVER" ["delete"]}
             (json/parse-string
              (ac/get-permissions
               (u/conn-context)
               {:target "NON_NASA_DRAFT_APPROVER"
                :provider "PROV2"
                :user_id "user1"}))))
      (is (= {"SUBSCRIPTION_MANAGEMENT" ["update"]}
             (json/parse-string
              (ac/get-permissions
               (u/conn-context)
               {:target "SUBSCRIPTION_MANAGEMENT"
                :provider "PROV1"
                :user_id "user1"}))))
      (is (= {"SUBSCRIPTION_MANAGEMENT" ["update"]}
             (json/parse-string
              (ac/get-permissions
               (u/conn-context)
               {:target "SUBSCRIPTION_MANAGEMENT"
                :provider "PROV2"
                :user_id "user1"})))))))

(deftest invalid-params-test
  (let [target-required-err "One of [concept_id], [system_object], [target_group_id], or [provider] and [target] are required."
        user-required-err "One of parameters [user_type] or [user_id] are required."
        system-target-err (str "Parameter [system_object] must be one of: [\"SYSTEM_AUDIT_REPORT\" "
                               "\"METRIC_DATA_POINT_SAMPLE\" \"SYSTEM_INITIALIZER\" \"ARCHIVE_RECORD\" "
                               "\"ERROR_MESSAGE\" \"TOKEN\" \"TOKEN_REVOCATION\" \"EXTENDED_SERVICE_ACTIVATION\" "
                               "\"ORDER_AND_ORDER_ITEMS\" \"PROVIDER\" \"TAG_GROUP\" \"TAXONOMY\" "
                               "\"TAXONOMY_ENTRY\" \"USER_CONTEXT\" \"USER\" \"GROUP\" \"ANY_ACL\" "
                               "\"EVENT_NOTIFICATION\" \"EXTENDED_SERVICE\" \"SYSTEM_OPTION_DEFINITION\" "
                               "\"SYSTEM_OPTION_DEFINITION_DEPRECATION\" \"INGEST_MANAGEMENT_ACL\" \"SYSTEM_CALENDAR_EVENT\" "
                               "\"DASHBOARD_ADMIN\" \"DASHBOARD_ARC_CURATOR\" \"DASHBOARD_MDQ_CURATOR\"]")
        prov-target-err (str "Parameter [target] must be one of: [\"AUDIT_REPORT\" "
                             "\"OPTION_ASSIGNMENT\" \"OPTION_DEFINITION\" \"OPTION_DEFINITION_DEPRECATION\" "
                             "\"DATASET_INFORMATION\" \"PROVIDER_HOLDINGS\" \"EXTENDED_SERVICE\" \"PROVIDER_ORDER\" "
                             "\"PROVIDER_ORDER_RESUBMISSION\" \"PROVIDER_ORDER_ACCEPTANCE\" \"PROVIDER_ORDER_REJECTION\" "
                             "\"PROVIDER_ORDER_CLOSURE\" \"PROVIDER_ORDER_TRACKING_ID\" \"PROVIDER_INFORMATION\" "
                             "\"PROVIDER_CONTEXT\" \"AUTHENTICATOR_DEFINITION\" \"PROVIDER_POLICIES\" \"USER\" "
                             "\"GROUP\" \"DASHBOARD_DAAC_CURATOR\" \"PROVIDER_OBJECT_ACL\" \"CATALOG_ITEM_ACL\" \"INGEST_MANAGEMENT_ACL\" "
                             "\"DATA_QUALITY_SUMMARY_DEFINITION\" \"DATA_QUALITY_SUMMARY_ASSIGNMENT\" \"PROVIDER_CALENDAR_EVENT\" \"NON_NASA_DRAFT_USER\" \"NON_NASA_DRAFT_APPROVER\" \"SUBSCRIPTION_MANAGEMENT\"]")]
    (are [params errors]
      (= {:status 400 :body {:errors errors} :content-type :json}
         (ac/get-permissions (u/conn-context) params {:raw? true}))
      {} [target-required-err user-required-err]
      {:target "PROVIDER_HOLDINGS"} [target-required-err user-required-err]
      {:user_id "" :concept_id []} [target-required-err user-required-err]
      {:user_type "" :concept_id []} [target-required-err user-required-err]
      {:user_id "foobar"} [target-required-err]
      ;; Provider target and provider not both present
      {:user_id "foobar" :target "PROVIDER_HOLDINGS"} [target-required-err]
      {:user_id "foobar" :provider "PROV1"} [target-required-err prov-target-err]
      {:concept_id "C12345-ABC2" :system_object "GROUP" :user_id "bat"} [target-required-err]
      {:concept_id "C1200000-PROV1" :user_type "GROUP" :user_id "foo"} [user-required-err]
      {:not_a_valid_param "foo"} ["Parameter [not_a_valid_param] was not recognized."]
      {:user_id "foo" :concept_id ["XXXXX"]} ["Concept-id [XXXXX] is not valid."]
      {:user_id "foo" :target_group_id ["C1200000-PROV1"]} ["Target group id [C1200000-PROV1] is not valid."]
      {:user_id "foo" :system_object "GROUPE"} [system-target-err]
      {:user_id "foo" :system_object "group"} [system-target-err]
      {:user_id "foo" :provider "PROV1" :target "PROVIDER_HOLDINGZ"} [prov-target-err]
      ;; More than one kind of target is specified
      {:user_id "foo" :provider "PROV1" :target ["PROVIDER_HOLDINGS" "AUDIT_REPORT"]} ["Only one target can be specified."
                                                                                       prov-target-err])))

(defn get-permissions
  "Helper to get permissions with the current context and the specified username string or user type keyword and concept ids."
  [user & concept-ids]
  (json/parse-string
    (ac/get-permissions
      (u/conn-context)
      (merge {:concept_id concept-ids}
             (if (keyword? user)
               {:user_type (name user)}
               {:user_id user})))))

(deftest collection-simple-catalog-item-identity-permission-check-test
  ;; tests ACLs which grant access to collections based on provider id and/or entry title
  (let [token (e/login (u/conn-context) "user1" ["group-create-group"])
        ;; helper for easily creating a group, returns concept id
        create-group #(:concept_id (u/create-group token (u/make-group %)))
        ;; then create a group that contains our user, so we can find collections that grant access to this user
        user1-group (create-group {:name "groupwithuser1" :members ["user1"]})
        ;; create some collections
        save-prov1-collection #(u/save-collection {:provider-id "PROV1"
                                                   :entry-title (str % " entry title")
                                                   :native-id %
                                                   :short-name %})
        coll1 (save-prov1-collection "coll1")
        coll2 (save-prov1-collection "coll2")
        coll3 (save-prov1-collection "coll3")
        coll4 (save-prov1-collection "coll4")

        gran1 (u/save-granule coll1)
        ;; local helpers to make the body of the test cleaner
        create-acl #(:concept_id (ac/create-acl (u/conn-context) % {:token token}))
        update-acl #(ac/update-acl (u/conn-context) %1 %2 {:token token})
        update-acl-invalid-revision-id #(ac/update-acl (u/conn-context) %1 %2 {:token token
                                                                               :cmr-revision-id "invalid"})
        update-acl-conflict-revision-id #(ac/update-acl (u/conn-context) %1 %2 {:token token
                                                                                :cmr-revision-id 2})
        update-acl-working-revision-id #(ac/update-acl (u/conn-context) %1 %2 {:token token
                                                                               :cmr-revision-id 3})
        get-collection-permissions #(get-permissions %1 coll1)
        get-granule-permissions #(get-permissions %1 gran1)
        ;;remove some ACLs created in fixtures which we dont want polluting tests
        fixture-acls (:items (u/search-for-acls (transmit-config/echo-system-token) {:target "INGEST_MANAGEMENT_ACL"}))
        _ (doseq [fixture-acl fixture-acls]
            (e/ungrant (u/conn-context) (:concept_id fixture-acl)))]

    (testing "no permissions granted"
      (are [user permissions]
        (= {coll1 permissions}
           (get-collection-permissions user))
        :guest []
        :registered []
        "user1" []))

    (testing "collection level permissions"
      (let [acl-concept-id (create-acl {:group_permissions [{:permissions [:read :order]
                                                             :user_type :guest}]
                                        :catalog_item_identity {:name "coll1 read and order"
                                                                :collection_applicable true
                                                                :provider_id "PROV1"}})]

        (testing "for guest users"
          (are [user permissions]
            (= {coll1 permissions}
               (get-collection-permissions user))
            :guest ["read" "order"]
            :registered []
            "user1" []))

        (testing "for registered users"
          (update-acl acl-concept-id
                      {:group_permissions [{:permissions [:read :order]
                                            :user_type :registered}]
                       :catalog_item_identity {:name "coll1 read and order"
                                               :collection_applicable true
                                               :provider_id "PROV1"}})
          (are [user permissions]
            (= {coll1 permissions}
               (get-collection-permissions user))
            :guest []
            :registered ["read" "order"]
            "user1" ["read" "order"]))

        (testing "update acl with revision-id"
          ;; invalid revision-id
          (try
            (update-acl-invalid-revision-id
              acl-concept-id
              {:group_permissions [{:permissions [:read :order]
                                    :user_type :registered}]
               :catalog_item_identity {:name "coll1 read and order"
                                       :collection_applicable true
                                       :provider_id "PROV1"}})
            (catch Exception e
              (is (= true
                     (string/includes?  e "{:type :invalid-data, :errors [\"Invalid revision-id [invalid]. Cmr-Revision-id in the header must be a positive integer.\"]}")))))

          ;; conflict revision-id: The previous test already updates the acl to revision 2,
          (try
            (update-acl-conflict-revision-id
              acl-concept-id
              {:group_permissions [{:permissions [:read :order]
                                    :user_type :registered}]
               :catalog_item_identity {:name "coll1 read and order"
                                       :collection_applicable true
                                       :provider_id "PROV1"}})
            (catch Exception e
              (is (= true
                     (string/includes?  e "Expected revision-id of [3] got [2]")))))

          ;; working revision-id: 3, as expected.
          (update-acl-working-revision-id
            acl-concept-id
            {:group_permissions [{:permissions [:read]
                                  :user_type :registered}]
             :catalog_item_identity {:name "coll1 read only"
                                     :collection_applicable true
                                     :provider_id "PROV1"}})

          (are3 [user permissions]
            (is (= {coll1 permissions}
                   (get-collection-permissions user)))
            "for guest users"
            :guest []
            "for registered"
            :registered ["read"]
            "for user1"
            "user1" ["read"]))

        (testing "acls granting access to specific groups"

          (update-acl acl-concept-id
                      {:group_permissions [{:permissions [:read :order]
                                            :group_id user1-group}]
                       :catalog_item_identity {:name "coll1 read and order"
                                               :collection_applicable true
                                               :provider_id "PROV1"}})

          (are [user permissions]
            (= {coll1 permissions}
               (get-collection-permissions user))
            :guest []
            :registered []
            "user1" ["read" "order"]
            "user2" [])

          (testing "with a complex ACL distributing permissions across multiple groups"
            (let [user2-group1 (create-group {:name "group1withuser2" :members ["user2"]})
                  user2-group2 (create-group {:name "group2withuser1and2" :members ["user1" "user2"]})]
              (create-acl {:group_permissions [{:permissions [:read] :group_id user1-group}
                                               {:permissions [:read] :group_id user2-group1}
                                               {:permissions [:order] :group_id user2-group2}]
                           :catalog_item_identity {:name "PROV1 complex ACL"
                                                   :collection_applicable true
                                                   :provider_id "PROV1"}})
              (are [user permissions]
                (= {coll1 permissions}
                   (get-collection-permissions user))
                :guest []
                :registered []
                "user1" ["read" "order"]
                "user2" ["read" "order"]))))

        (testing "acls granting access to collection by collection identifier"
          (testing "by entry title"
            (create-acl
              {:group_permissions [{:permissions [:read]
                                    :user_type :guest}]
               :catalog_item_identity {:name "coll2 guest read entry titles"
                                       :collection_applicable true
                                       :collection_identifier {:entry_titles ["coll2 entry title"]}
                                       :provider_id "PROV1"}})
            (testing "for collection in ACL's entry titles"
              (are [user permissions]
                (= {coll2 permissions}
                   (get-permissions user coll2))
                :guest ["read"]
                :registered []))
            (testing "for collection not in ACL's entry titles"
              (are [user permissions]
                (= {coll3 permissions}
                   (get-permissions user coll3))
                :guest []
                :registered [])))

          (testing "by concept id"
            (create-acl
              {:group_permissions [{:permissions [:read :order]
                                    :user_type :guest}]
               :catalog_item_identity {:name "coll3 guest read concept ids"
                                       :collection_applicable true
                                       :collection_identifier {:concept_ids [coll3]}
                                       :provider_id "PROV1"}})
            (testing "for collection in ACL's concept ids"
              (are3 [user permissions]
                (is (= {coll3 permissions}
                       (get-permissions user coll3)))

                "for guest users"
                :guest ["read" "order"]

                "for registered users"
                :registered []))

            (testing "for collection not in ACL's concept-ids"
              (are3 [user permissions]
                (is (= {coll4 permissions}
                       (get-permissions user coll4)))

                "for guest users"
                :guest []

                "for registered users"
                :registered []))))))

    (testing "granule level permissions"
      (testing "no permissions granted"
        (is (= {gran1 []}
               (get-granule-permissions :guest)))))))

(deftest collection-catalog-item-identifier-access-value-test
  ;; tests ACLs which grant access to collections based on their access value
  (let [token (e/login-guest (u/conn-context))
        save-access-value-collection (fn [short-name access-value format]
                                         (u/save-collection {:entry-title (str short-name " entry title")
                                                             :short-name short-name
                                                             :native-id short-name
                                                             :provider-id "PROV1"
                                                             :access-value access-value
                                                             :format format}))
        ;; collection with a low access value as umm-json
        coll1 (save-access-value-collection "coll1" 1 :umm-json)
        ;; collection with a low access value as dif10
        coll2 (save-access-value-collection "coll2" 1 :dif10)
        ;; collection with an intermediate access value
        coll3 (save-access-value-collection "coll3" 4 :iso-smap)
        ;; collection with an intermediate access iso19115
        coll4 (save-access-value-collection "coll4" 4 :iso19115)
        ;; one with a higher access value
        coll5 (save-access-value-collection "coll5" 9 :echo10)
        ;; and one with no access value
        coll6 (save-access-value-collection "coll6" nil :dif)
        create-acl #(:concept_id (ac/create-acl (u/conn-context) % {:token token}))
        update-acl #(ac/update-acl (u/conn-context) %1 %2 {:token token})
        get-coll-permissions #(get-permissions :guest coll1 coll2 coll3 coll4 coll5 coll6)]

    (testing "no permissions granted"
      (is (= {coll1 []
              coll2 []
              coll3 []
              coll4 []
              coll5 []
              coll6 []}
             (get-coll-permissions))))

    (let [acl-id (create-acl
                   {:group_permissions [{:permissions [:read]
                                         :user_type :guest}]
                    :catalog_item_identity {:name "coll2 guest read"
                                            :collection_applicable true
                                            :collection_identifier {:access_value {:min_value 1 :max_value 10}}
                                            :provider_id "PROV1"}})]

      (testing "ACL matching all access values"
        (is (= {coll1 ["read"]
                coll2 ["read"]
                coll3 ["read"]
                coll4 ["read"]
                coll5 ["read"]
                coll6 []}
               (get-coll-permissions))))

      (testing "ACL matching only high access values"
        (update-acl acl-id {:group_permissions [{:permissions [:read]
                                                 :user_type :guest}]
                            :catalog_item_identity {:name "coll2 guest read"
                                                    :collection_applicable true
                                                    :collection_identifier {:access_value {:min_value 4 :max_value 10}}
                                                    :provider_id "PROV1"}})

        (is (= {coll1 []
                coll2 []
                coll3 ["read"]
                coll4 ["read"]
                coll5 ["read"]
                coll6 []}
               (get-coll-permissions))))

      (testing "ACL matching only one access value"
        (update-acl acl-id {:group_permissions [{:permissions [:read]
                                                 :user_type :guest}]
                            :catalog_item_identity {:name "coll2 guest read"
                                                    :collection_applicable true
                                                    :collection_identifier {:access_value {:min_value 4 :max_value 5}}
                                                    :provider_id "PROV1"}})

        (is (= {coll1 []
                coll2 []
                coll3 ["read"]
                coll4 ["read"]
                coll5 []
                coll6 []}
               (get-coll-permissions))))

      (testing "ACL matching only collections with undefined access values"
        (update-acl acl-id {:group_permissions [{:permissions [:read]
                                                 :user_type :guest}]
                            :catalog_item_identity {:name "coll2 guest read"
                                                    :collection_applicable true
                                                    :collection_identifier {:access_value {:include_undefined_value true}}
                                                    :provider_id "PROV1"}})

        (is (= {coll1 []
                coll2 []
                coll3 []
                coll4 []
                coll5 []
                coll6 ["read"]}
               (get-coll-permissions)))))))

(deftest collection-catalog-item-identifier-temporal-test
  ;; tests ACLs that grant access based on a collection's temporal range
  (let [token (e/login-guest (u/conn-context))
        save-temporal-collection (fn [short-name start-year end-year]
                                     (u/save-collection {:entry-title (str short-name " entry title")
                                                         :short-name short-name
                                                         :native-id short-name
                                                         :provider-id "PROV1"
                                                         :temporal-range {:BeginningDateTime (t/date-time start-year)
                                                                          :EndingDateTime (t/date-time end-year)}}))
        coll1 (save-temporal-collection "coll1" 2001 2002)
        coll2 (save-temporal-collection "coll2" 2004 2005)
        coll3 (save-temporal-collection "coll3" 2007 2009)
        ;; coll4 will have no temporal extent, and should not be granted any permissions by our ACLs in this test
        coll4 (u/save-collection {:entry-id "coll4"
                                  :native-id "coll4"
                                  :short-name "coll4"
                                  :entry-title "non-temporal coll4"
                                  :provider-id "PROV1"
                                  :no-temporal true})
        create-acl #(:concept_id (ac/create-acl (u/conn-context) % {:token token}))
        update-acl #(ac/update-acl (u/conn-context) %1 %2 {:token token})
        get-coll-permissions #(get-permissions :guest coll1 coll2 coll3 coll4)]

    (is (= {coll1 []
            coll2 []
            coll3 []
            coll4 []}
           (get-coll-permissions)))

    (let [acl-id (create-acl
                   {:group_permissions [{:permissions [:read]
                                         :user_type :guest}]
                    :catalog_item_identity {:name "coll2 guest read"
                                            :collection_applicable true
                                            :collection_identifier {:temporal {:start_date "2000-01-01T00:00:00Z"
                                                                               :stop_date "2010-01-01T00:00:00Z"
                                                                               :mask "intersect"}}
                                            :provider_id "PROV1"}})]

      (testing "\"intersect\" mask"
        (is (= {coll1 ["read"]
                coll2 ["read"]
                coll3 ["read"]
                coll4 []}
               (get-coll-permissions))))

      (testing "\"disjoint\" mask"
        (update-acl acl-id {:group_permissions [{:permissions [:read]
                                                 :user_type :guest}]
                            :catalog_item_identity {:name "coll2 guest read"
                                                    :collection_applicable true
                                                    :collection_identifier {:temporal {:start_date "2003-01-01T00:00:00Z"
                                                                                       :stop_date "2006-01-01T00:00:00Z"
                                                                                       :mask "disjoint"}}
                                                    :provider_id "PROV1"}})
        (is (= {coll1 ["read"]
                coll2 []
                coll3 ["read"]
                coll4 []}
               (get-coll-permissions))))

      (testing "\"contains\" mask"
        (update-acl acl-id {:group_permissions [{:permissions [:read]
                                                 :user_type :guest}]
                            :catalog_item_identity {:name "coll2 guest read"
                                                    :collection_applicable true
                                                    :collection_identifier {:temporal {:start_date "2003-01-01T00:00:00Z"
                                                                                       :stop_date "2006-01-01T00:00:00Z"
                                                                                       :mask "contains"}}
                                                    :provider_id "PROV1"}})
        (is (= {coll1 []
                coll2 ["read"]
                coll3 []
                coll4 []}
               (get-coll-permissions)))))))

;; For CMR-3210:

(deftest granule-permissions-test
  (let [token (e/login (u/conn-context) "user1" ["group-create-group"])
        group (u/make-group {:name "groupwithuser1" :members ["user1"]})
        created-group-concept-id (:concept_id (u/create-group token group))
        save-prov1-collection #(u/save-collection {:provider-id "PROV1"
                                                      :entry-title (str % " entry title")
                                                      :native-id %
                                                      :short-name %})
        coll1 (save-prov1-collection "coll1")
        gran1 (u/save-granule coll1)
        create-acl #(:concept_id (ac/create-acl (u/conn-context) % {:token token}))
        update-acl #(ac/update-acl (u/conn-context) %1 %2 {:token token})
        ;;remove some ACLs created in fixtures which we dont want polluting tests
        fixture-acls (:items (u/search-for-acls (transmit-config/echo-system-token) {:target "INGEST_MANAGEMENT_ACL"}))
        _ (doseq [fixture-acl fixture-acls]
            (e/ungrant (u/conn-context) (:concept_id fixture-acl)))]

    (testing "no permissions granted"
      (are [user permissions]
        (= {gran1 permissions}
           (get-permissions user gran1))
        :guest []
        :registered []
        "user1" []))

    (testing "permissions granted to guests"
      (let [acl (create-acl
                  {:group_permissions [{:permissions [:read]
                                        :user_type :guest}]
                   :catalog_item_identity {:name "prov1 granule read"
                                           :granule_applicable true
                                           :provider_id "PROV1"}})]
        (are [user permissions]
          (= {gran1 permissions}
             (get-permissions user gran1))
          :guest ["read"]
          :registered []
          "user1" [])

        (testing "permissions granted to registered users"
          (update-acl acl {:group_permissions [{:permissions [:read :order]
                                                :user_type :registered}]
                           :catalog_item_identity {:name "prov1 granule read"
                                                   :granule_applicable true
                                                   :provider_id "PROV1"}})

          (are [user permissions]
            (= {gran1 permissions}
               (get-permissions user gran1))
            :guest []
            :registered ["read" "order"]
            "user1" ["read" "order"]))

        (testing "permissions granted to a specific group"
          (update-acl acl {:group_permissions [{:permissions [:read]
                                                :group_id created-group-concept-id}]
                           :catalog_item_identity {:name "prov1 granule read"
                                                   :granule_applicable true
                                                   :provider_id "PROV1"}})

          (are [user permissions]
            (= {gran1 permissions}
               (get-permissions user gran1))
            :guest []
            :registered []
            "user1" ["read"]))))))

(deftest granule-permissions-with-collection-identifier-test
  (let [token (e/login (u/conn-context) "user1" ["group-create-group"])
        group (u/make-group {:name "groupwithuser1" :members ["user1"]})
        created-group-concept-id (:concept_id (u/create-group token group))
        save-prov1-collection #(u/save-collection {:provider-id "PROV1"
                                                     :entry-title (str % " entry title")
                                                     :native-id %
                                                     :short-name %})
        coll1 (save-prov1-collection "coll1")
        coll2 (save-prov1-collection "coll2")
        gran1 (u/save-granule coll1)
        gran2 (u/save-granule coll2)
        create-acl #(:concept_id (ac/create-acl (u/conn-context) % {:token token}))
        update-acl #(ac/update-acl (u/conn-context) %1 %2 {:token token})
        ;;remove some ACLs created in fixtures which we dont want polluting tests
        fixture-acls (:items (u/search-for-acls (transmit-config/echo-system-token) {:target "INGEST_MANAGEMENT_ACL"}))
        _ (doseq [fixture-acl fixture-acls]
            (e/ungrant (u/conn-context) (:concept_id fixture-acl)))]

    (testing "no permissions granted"
      (are [user permissions]
        (= {gran1 permissions}
           (get-permissions user gran1))
        :guest []
        :registered []
        "user1" []))

    (testing "permissions granted to guests"
      (let [acl (create-acl
                  {:group_permissions [{:permissions [:read]
                                        :user_type :guest}]
                   :catalog_item_identity {:name "prov1 granule read"
                                           :granule_applicable true
                                           :collection_applicable true
                                           :collection_identifier {:entry_titles ["coll1 entry title"]}
                                           :provider_id "PROV1"}})]
        (are [user permissions1 permissions2]
          (= {gran1 permissions1
              ;; also ensure that the other granule under coll2 doesn't get any permissions from this ACL
              gran2 permissions2}
             (get-permissions user gran1 gran2))
          :guest ["read"] []
          :registered [] []
          "user1" [] [])

        (testing "permissions granted to registered users"
          (update-acl acl {:group_permissions [{:permissions [:read :order]
                                                :user_type :registered}]
                           :catalog_item_identity {:name "prov1 granule read"
                                                   :granule_applicable true
                                                   :collection_applicable true
                                                   :collection_identifier {:entry_titles ["coll1 entry title"]}
                                                   :provider_id "PROV1"}})

          (are [user permissions1 permissions2]
            (= {gran1 permissions1
                gran2 permissions2}
               (get-permissions user gran1 gran2))
            :guest [] []
            :registered ["read" "order"] []
            "user1" ["read" "order"] []))

        (testing "permissions granted to registered users via concept ids instead of entry-titles"
          (update-acl acl {:group_permissions [{:permissions [:read :order]
                                                :user_type :registered}]
                           :catalog_item_identity {:name "prov1 granule read"
                                                   :granule_applicable true
                                                   :collection_applicable true
                                                   :collection_identifier {:concept_ids [coll1]}
                                                   :provider_id "PROV1"}})

          (are3 [user permissions1 permissions2]
            (= {gran1 permissions1
                gran2 permissions2}
               (get-permissions user gran1 gran2))

            "for guest users"
            :guest [] []

            "for registered users"
            :registered ["read" "order"] []

            "for user1"
            "user1" ["read" "order"] []))

        (testing "no permissions are granted with granule_applicable = false"
          (update-acl acl {:group_permissions [{:permissions [:read :order]
                                                :user_type :registered}]
                           :catalog_item_identity {:name "prov1 granule read"
                                                   :granule_applicable false
                                                   :collection_applicable true
                                                   :collection_identifier {:entry_titles ["coll1 entry title"]}
                                                   :provider_id "PROV1"}})

          (are [user permissions1 permissions2]
            (= {gran1 permissions1
                gran2 permissions2}
               (get-permissions user gran1 gran2))
            :guest [] []
            :registered [] []
            "user1" [] []))

        (testing "permissions granted to a specific group"
          (update-acl acl {:group_permissions [{:permissions [:read]
                                                :group_id created-group-concept-id}]
                           :catalog_item_identity {:name "prov1 granule read"
                                                   :granule_applicable true
                                                   :collection_applicable true
                                                   :collection_identifier {:entry_titles ["coll1 entry title"]}
                                                   :provider_id "PROV1"}})

          (are [user permissions1 permissions2]
            (= {gran1 permissions1
                gran2 permissions2}
               (get-permissions user gran1 gran2))
            :guest [] []
            :registered [] []
            "user1" ["read"] []))))))

(deftest granule-permissions-with-access-value-test
  (let [token (e/login (u/conn-context) "user1" ["group-create-group"])
        group (u/make-group {:name "groupwithuser1" :members ["user1"]})
        created-group-concept-id (:concept_id (u/create-group token group))
        save-prov1-collection #(u/save-collection {:provider-id "PROV1"
                                                   :entry-title (str % " entry title")
                                                   :native-id %
                                                   :short-name %})
        coll1 (save-prov1-collection "coll1")
        ;; no access value
        gran1 (u/save-granule coll1)
        ;; mid access value
        gran2 (u/save-granule coll1 {:access-value 5})
        ;; high access value
        gran3 (u/save-granule coll1 {:access-value 10})
        ;; no access value umm-g
        gran4 (u/save-granule coll1 {} :umm-json)
        ;; mid access value umm-g
        gran5 (u/save-granule coll1 {:access-value 5} :umm-json)
        ;; high access value umm-g
        gran6 (u/save-granule coll1 {:access-value 9} :umm-json)
        create-acl #(:concept_id (ac/create-acl (u/conn-context) % {:token token}))
        ;; guest read coll1 granules with undefined access value
        acl1 (create-acl {:group_permissions [{:permissions [:read]
                                               :user_type :guest}]
                          :catalog_item_identity {:name "prov1 granules w/ undefined access value"
                                                  :granule_applicable true
                                                  :granule_identifier {:access_value {:include_undefined_value true}}
                                                  :provider_id "PROV1"}})
        ;; registered read granules with access value up to 7
        acl2 (create-acl {:group_permissions [{:permissions [:read]
                                               :user_type :registered}]
                          :catalog_item_identity {:name "prov1 granules w/ max access value"
                                                  :granule_applicable true
                                                  :granule_identifier {:access_value {:min_value 0 :max_value 7}}
                                                  :provider_id "PROV1"}})
        ;; specific group read granules with access value 7 or higher
        acl3 (create-acl {:group_permissions [{:permissions [:read]
                                               :user_type :registered}]
                          :catalog_item_identity {:name "prov1 granules w/ min access value"
                                                  :granule_applicable true
                                                  :granule_identifier {:access_value {:min_value 7 :max_value 1000}}
                                                  :provider_id "PROV1"}})
        ;;remove some ACLs created in fixtures which we dont want polluting tests
        fixture-acls (:items (u/search-for-acls (transmit-config/echo-system-token) {:target "INGEST_MANAGEMENT_ACL"}))
        _ (doseq [fixture-acl fixture-acls]
            (e/ungrant (u/conn-context) (:concept_id fixture-acl)))]
    (are [user result]
      (= result (get-permissions user gran1 gran2 gran3 gran4 gran5 gran6))
      :guest {gran1 ["read"]
              gran2 []
              gran3 []
              gran4 ["read"]
              gran5 []
              gran6 []}
      :registered {gran1 []
                   gran2 ["read"]
                   gran3 ["read"]
                   gran4 []
                   gran5 ["read"]
                   gran6 ["read"]})))

(deftest granule-permissions-with-temporal-value-test
  (let [token (e/login (u/conn-context) "user1" ["group-create-group"])
        group (u/make-group {:name "groupwithuser1" :members ["user1"]})
        created-group-concept-id (:concept_id (u/create-group token group))
        save-prov1-collection #(u/save-collection {:provider-id "PROV1"
                                                   :entry-title (str % " entry title")
                                                   :native-id %
                                                   :short-name %})
        coll1 (save-prov1-collection "coll1")
        ;; no temporal
        gran1 (u/save-granule coll1)
        ;; temporal range
        gran2 (u/save-granule coll1 {:temporal {:range-date-time {:beginning-date-time "2002-01-01T00:00:00Z"
                                                                  :ending-date-time "2005-01-01T00:00:00Z"}}})
        ;; single date-time
        gran3 (u/save-granule coll1 {:temporal {:single-date-time "1999-01-01T00:00:00Z"}})

        ;; no temporal umm-g
        gran4 (u/save-granule coll1 {} :umm-json)

        ;; temporal range umm-g
        gran5 (u/save-granule coll1
                              {:temporal {:range-date-time {:beginning-date-time "2002-01-01T00:00:00Z"
                                                            :ending-date-time "2005-01-01T00:00:00Z"}}}
                              :umm-json)

        ;; single date-time umm-g
        gran6 (u/save-granule coll1 {:temporal {:single-date-time "1999-01-01T00:00:00Z"}} :umm-json)

        create-acl #(:concept_id (ac/create-acl (u/conn-context) % {:token token}))

        acl1 (create-acl {:group_permissions [{:permissions [:read]
                                               :user_type :guest}]
                          :catalog_item_identity {:name "prov1 granules between 2000 and 2011"
                                                  :granule_applicable true
                                                  :granule_identifier {:temporal {:start_date "2000-01-01T00:00:00Z"
                                                                                  :stop_date "2011-01-01T00:00:00Z"
                                                                                  :mask "contains"}}
                                                  :provider_id "PROV1"}})

        acl2 (create-acl {:group_permissions [{:permissions [:read]
                                               :user_type :registered}]
                          :catalog_item_identity {:name "prov1 granules before 2000"
                                                  :granule_applicable true
                                                  :granule_identifier {:temporal {:start_date "1900-01-01T00:00:00Z"
                                                                                  :stop_date "2000-01-01T00:00:00Z"
                                                                                  :mask "contains"}}
                                                  :provider_id "PROV1"}})
        ;;remove some ACLs created in fixtures which we dont want polluting tests
        fixture-acls (:items (u/search-for-acls (transmit-config/echo-system-token) {:target "INGEST_MANAGEMENT_ACL"}))
        _ (doseq [fixture-acl fixture-acls]
            (e/ungrant (u/conn-context) (:concept_id fixture-acl)))]
    (are [user result]
      (= result (get-permissions user gran1 gran2 gran3 gran4 gran5 gran6))
      :guest {gran1 []
              gran2 ["read"]
              gran3 []
              gran4 []
              gran5 ["read"]
              gran6 []}
      :registered {gran1 []
                   gran2 []
                   gran3 ["read"]
                   gran4 []
                   gran5 []
                   gran6 ["read"]})))

(deftest collection-provider-level-permission-check-test
  ;; Tests ACLs which grant the provider-level update/delete permissions to collections
  (let [token (e/login (u/conn-context) "user1" ["group-create-group"])
        ;; then create a group that contains our user, so we can find collections that grant access to this user
        group (u/make-group {:name "groupwithuser1" :members ["user1"]})
        created-group-concept-id (:concept_id (u/create-group token group))
        ;; create some collections
        coll1 (u/save-collection {:provider-id "PROV1"
                                  :entry-title "coll1"
                                  :native-id "coll1"
                                  :short-name "coll1"})
        ;; local helpers to make the body of the test cleaner
        create-acl #(e/grant (u/conn-context) %1 %2 %3)
        update-acl #(ac/update-acl (u/conn-context) %1 %2 {:token token})
        get-coll1-permissions #(get-permissions % coll1)
        ;;remove some ACLs created in fixtures which we dont want polluting tests
        fixture-acls (:items (u/search-for-acls (transmit-config/echo-system-token) {:target "INGEST_MANAGEMENT_ACL"}))
        _ (doseq [fixture-acl fixture-acls]
            (e/ungrant (u/conn-context) (:concept_id fixture-acl)))]

    (testing "no permissions granted"
      (are [user permissions]
        (= {coll1 permissions}
           (get-coll1-permissions user))
        :guest []
        :registered []
        "user1" []))

    (testing "provider level permissions"
      ;; update in the IMA grants update AND delete
      (let [acl-concept-id (create-acl  [{:permissions [:update]
                                          :user_type :guest}]
                            :provider_identity {:provider_id "PROV1"
                                                :target "INGEST_MANAGEMENT_ACL"})]
        (are [user permissions]
          (= {coll1 permissions}
             (get-coll1-permissions user))
          :guest ["update" "delete"]
          :registered []
          "user1" [])

        (testing "granted to registered users"
          (update-acl acl-concept-id
                      {:group_permissions [{:permissions [:update]
                                            :user_type :registered}]
                       :provider_identity {:provider_id "PROV1"
                                           :target "INGEST_MANAGEMENT_ACL"}})

          (are [user permissions]
            (= {coll1 permissions}
               (get-coll1-permissions user))
            :guest []
            :registered ["update" "delete"]
            "user1" ["update" "delete"]))

        (testing "granted to specific groups"
          (update-acl acl-concept-id
                      {:group_permissions [{:permissions [:update]
                                            :group_id created-group-concept-id}]
                       :provider_identity {:provider_id "PROV1"
                                           :target "INGEST_MANAGEMENT_ACL"}})

          (are [user permissions]
            (= {coll1 permissions}
               (get-coll1-permissions user))
            :guest []
            :registered []
            "user1" ["update" "delete"]
            "user2" []))))))

(deftest system-level-permission-check
  (let [token (e/login (u/conn-context) "user1" ["group-create-group"])
        ;; then create a group that contains our user, so we can find collections that grant access to this user
        group (u/make-group {:name "groupwithuser1" :members ["user1"]})
        created-group-concept-id (:concept_id (u/create-group token group))
        ;; local helpers to make the body of the test cleaner
        create-acl #(e/grant (u/conn-context) %1 %2 %3)
        update-acl #(ac/update-acl (u/conn-context) %1 %2 {:token token})
        get-system-permissions (fn [user system-object]
                                 (json/parse-string
                                  (ac/get-permissions
                                   (u/conn-context)
                                   (merge {:system_object system-object}
                                          (if (keyword? user)
                                            {:user_type (name user)}
                                            {:user_id user})))))]

    (testing "no permissions granted"
      (are [user permissions]
        (= {"INGEST_MANAGEMENT_ACL" permissions}
           (get-system-permissions user "INGEST_MANAGEMENT_ACL"))
        :guest []
        :registered []
        "user1" []))

    (let [acl-concept-id (create-acl [{:permissions [:read]
                                       :user_type :guest}]
                           :system_identity {:target "GROUP"})]

      (testing "granted to registered users"
        (update-acl acl-concept-id
                    {:group_permissions [{:permissions [:read]
                                          :user_type :registered}]
                     :system_identity {:target "GROUP"}})

        (are [user permissions]
          (= {"GROUP" permissions}
             (get-system-permissions user "GROUP"))
          :guest []
          :registered ["read"]
          "user1" ["read"]))

      (testing "other ACLs are not matched when searching other targets"
        (create-acl [{:permissions [:create]
                      :user_type :registered}]
                    :system_identity {:target "PROVIDER"})
        (are [user permissions]
          (= {"PROVIDER" permissions}
             (get-system-permissions user "PROVIDER"))
          :guest []
          :registered ["create"]
          "user1" ["create"]))

      (testing "granted to specific groups"
        (update-acl acl-concept-id
                    {:group_permissions [{:permissions [:read]
                                          :group_id created-group-concept-id}]
                     :system_identity {:target "GROUP"}})

        (are [user permissions]
          (= {"GROUP" permissions}
             (get-system-permissions user "GROUP"))
          :guest []
          :registered []
          "user1" ["read"]
          "user2" [])))))

(deftest provider-object-permission-check
  (let [token (e/login (u/conn-context) "user1" ["group-create-group"])
        group (u/make-group {:name "groupwithuser1" :members ["user1"]})
        created-group-concept-id (:concept_id (u/create-group token group))
        create-acl #(ac/create-acl (u/conn-context) % {:token token})
        update-acl #(ac/update-acl (u/conn-context) %1 %2 {:token token})
        get-provider-permissions (fn [user provider-id provider-object]
                                   (json/parse-string
                                    (ac/get-permissions
                                     (u/conn-context)
                                     (merge {:target provider-object
                                             :provider provider-id}
                                            (if (keyword? user)
                                              {:user_type (name user)}
                                              {:user_id user})))))]

    (testing "no permissions granted"
      (are [user permissions]
        (= {"PROVIDER_HOLDINGS" permissions}
           (get-provider-permissions user "PROV1" "PROVIDER_HOLDINGS"))
        :guest []
        :registered []
        "user1" []))

    (let [acl {:group_permissions [{:permissions [:read]
                                    :user_type :guest}]
               :provider_identity {:provider_id "PROV1"
                                   :target "PROVIDER_HOLDINGS"}}
          acl-concept-id (:concept_id (create-acl acl))]

      (testing "granted to registered users"
        (update-acl acl-concept-id
                    {:group_permissions [{:permissions [:read]
                                          :user_type :registered}]
                     :provider_identity {:provider_id "PROV1"
                                         :target "PROVIDER_HOLDINGS"}})

        (are [user permissions]
          (= {"PROVIDER_HOLDINGS" permissions}
             (get-provider-permissions user "PROV1" "PROVIDER_HOLDINGS"))
          :guest []
          :registered ["read"]
          "user1" ["read"]))

      (testing "other ACLs are not matched when searching other targets"
        (create-acl {:group_permissions [{:permissions [:update]
                                          :user_type :registered}]
                     :provider_identity {:provider_id "PROV1"
                                         :target "EXTENDED_SERVICE"}})
        (are [user permissions]
          (= {"EXTENDED_SERVICE" permissions}
             (get-provider-permissions user "PROV1" "EXTENDED_SERVICE"))
          :guest []
          :registered ["update"]
          "user1" ["update"]))

      (testing "granted to specific groups"
        (update-acl acl-concept-id
                    {:group_permissions [{:permissions [:create :read :update :delete]
                                          :group_id created-group-concept-id}]
                     :provider_identity {:provider_id "PROV1"
                                         :target "PROVIDER_OBJECT_ACL"}})

        (are [user permissions]
          (= {"PROVIDER_OBJECT_ACL" (set permissions)}
             (update (get-provider-permissions user "PROV1" "PROVIDER_OBJECT_ACL") "PROVIDER_OBJECT_ACL" set))
          :guest []
          :registered []
          "user1" ["create" "read" "update" "delete"]
          "user2" [])))))

(deftest single-instance-identity-group-permission-check
  (let [token (e/login (u/conn-context) "user1" ["group-create-group"])
        group1 (u/make-group {:name "group1" :members ["user1"]})
        group1-id (:concept_id (u/create-group token group1))
        group2 (u/make-group {:name "group2" :members ["user2"]})
        group2-id (:concept_id (u/create-group token group2))
        create-acl #(ac/create-acl (u/conn-context) % {:token token})
        update-acl #(ac/update-acl (u/conn-context) %1 %2 {:token token})
        get-group-permissions (fn [user target-group-ids]
                                (json/parse-string
                                 (ac/get-permissions
                                  (u/conn-context)
                                  (merge {:target_group_id target-group-ids}
                                         (if (keyword? user)
                                           {:user_type (name user)}
                                           {:user_id user})))))]

    (testing "no permissions granted"
      (are [user permissions]
        (= permissions
           (get-group-permissions user [group1-id]))
        :guest {group1-id []}
        :registered {group1-id []}
        "user1" {group1-id []}
        "user2" {group1-id []}))

    (let [acl {:group_permissions [{:permissions [:update]
                                    :user_type :guest}]
               :single_instance_identity {:target "GROUP_MANAGEMENT"
                                          :target_id group1-id}}
          acl-concept-id (:concept_id (create-acl acl))]

      (testing "granted to guest users"
        (are [user permissions]
          (= permissions
             (get-group-permissions user [group1-id]))
          :guest {group1-id ["update"]}
          :registered {group1-id []}
          "user1" {group1-id []}
          "user2" {group1-id []}))

      (testing "granted to registered users"
        (update-acl acl-concept-id
                    {:group_permissions [{:permissions [:delete]
                                          :user_type :registered}]
                     :single_instance_identity {:target "GROUP_MANAGEMENT"
                                                :target_id group1-id}})

        (are [user permissions]
          (= permissions
             (get-group-permissions user [group1-id]))
          :guest {group1-id []}
          :registered {group1-id ["delete"]}
          "user1" {group1-id ["delete"]}
          "user2" {group1-id ["delete"]}))

      (testing "granted to specific groups"
        (update-acl acl-concept-id
                    {:group_permissions [{:permissions [:update :delete]
                                          :group_id group1-id}]
                     :single_instance_identity {:target "GROUP_MANAGEMENT"
                                                :target_id group1-id}})

        (are [user permissions]
          (= permissions
             (get-group-permissions user [group1-id]))
          :guest {group1-id []}
          :registered {group1-id []}
          "user1" {group1-id ["update" "delete"]}
          "user2" {group1-id []})

        (testing "search for multiple target group ids"
          (are [user permissions]
            (= permissions
               (get-group-permissions user [group1-id group2-id]))
            :guest {group1-id [] group2-id []}
            :registered {group1-id [] group2-id []}
            "user1" {group1-id ["update" "delete"] group2-id []}
            "user2" {group1-id [] group2-id []})

          (testing "search for multiple target group ids, both target group id have granted permissions"
            ;; create anohter ACL grant user2 group2 update permission
            (create-acl {:group_permissions [{:permissions [:update]
                                              :group_id group2-id}]
                         :single_instance_identity {:target "GROUP_MANAGEMENT"
                                                    :target_id group2-id}})
            (are [user permissions]
              (= permissions
                 (get-group-permissions user [group1-id group2-id]))
              :guest {group1-id [] group2-id []}
              :registered {group1-id [] group2-id []}
              "user1" {group1-id ["update" "delete"] group2-id []}
              "user2" {group1-id [] group2-id ["update"]})))))))
