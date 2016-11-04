(ns cmr.access-control.int-test.permission-check-test
  "Tests the access control permission check routes."
  (require [clojure.test :refer :all]
           [cheshire.core :as json]
           [cmr.transmit.access-control :as ac]
           [cmr.transmit.metadata-db2 :as mdb]
           [cmr.mock-echo.client.echo-util :as e]
           [cmr.common.util :refer [are2]]
           [cmr.access-control.int-test.fixtures :as fixtures]
           [cmr.access-control.test.util :as u]
           [cmr.umm.umm-core :as umm-core]
           [cmr.umm.umm-granule :as umm-g]
           [cmr.umm-spec.test.expected-conversion :refer [example-collection-record]]
           [clj-time.core :as t]
           [cmr.common.util :as util]))

(use-fixtures :once (fixtures/int-test-fixtures))

(use-fixtures :each
              (fixtures/reset-fixture {"prov1guid" "PROV1"} ["user1" "user2"])
              (fn [f]
                (e/grant-all-ingest (u/conn-context) "prov1guid")
                (f))
              (fixtures/grant-all-group-fixture ["prov1guid"])
              (fixtures/grant-all-acl-fixture))

(deftest invalid-params-test
  (are [params errors]
    (= {:status 400 :body {:errors errors} :content-type :json}
       (ac/get-permissions (u/conn-context) params {:raw? true}))
    {} ["One of [concept_id], [system_object], or [provider] and [target] are required."
        "One of parameters [user_type] or [user_id] are required."]
    {:target "PROVIDER_HOLDINGS"} ["One of [concept_id], [system_object], or [provider] and [target] are required."
                                   "One of parameters [user_type] or [user_id] are required."]
    {:user_id "" :concept_id []} ["One of [concept_id], [system_object], or [provider] and [target] are required."
                                  "One of parameters [user_type] or [user_id] are required."]
    {:user_id "foobar"} ["One of [concept_id], [system_object], or [provider] and [target] are required."]
    {:concept_id "C12345-ABC2" :system_object "GROUP" :user_id "bat"} ["One of [concept_id], [system_object], or [provider] and [target] are required."]
    {:concept_id "C1200000-PROV1" :user_type "GROUP" :user_id "foo"} ["One of parameters [user_type] or [user_id] are required."]
    {:not_a_valid_param "foo"} ["Parameter [not_a_valid_param] was not recognized."]
    {:user_id "foo" :concept_id ["XXXXX"]} ["Concept-id [XXXXX] is not valid."])
  (are [params re]
    (some #(re-find re %)
          (:errors (:body (ac/get-permissions (u/conn-context) params {:raw? true}))))
    {:user_id "foo" :system_object "GROUPE"} #"Parameter \[system_object\] must be one of: .*GROUP.*"
    {:user_id "foo" :system_object "group"} #"Parameter \[system_object\] must be one of: .*GROUP.*"
    {:user_id "foo" :provider "PROV1" :target "PROVIDER_HOLDINGZ"} #"Parameter \[target\] must be one of: .*PROVIDER_HOLDINGS.*"))

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

(def granule-num
  "An atom storing the next number used to generate unique granules."
  (atom 0))

(defn save-granule
  "Saves a granule with given property map to metadata db and returns concept id."
  ([parent-collection-id]
   (save-granule parent-collection-id {}))
  ([parent-collection-id attrs]
   (let [short-name (str "gran" (swap! granule-num inc))
         version-id "v1"
         native-id short-name
         entry-id (str short-name "_" version-id)
         granule-ur (str short-name "ur")
         parent-collection (mdb/get-latest-concept (u/conn-context) parent-collection-id)
         parent-entry-title (:entry-title (:extra-fields parent-collection))
         timestamps (umm-g/map->DataProviderTimestamps
                     {:insert-time "2012-01-11T10:00:00.000Z"})
         granule-umm (umm-g/map->UmmGranule
                      {:granule-ur granule-ur
                       :data-provider-timestamps timestamps
                       :collection-ref (umm-g/map->CollectionRef
                                        {:entry-title parent-entry-title})})
         granule-umm (merge granule-umm attrs)]
     ;; We don't want to publish messages in metadata db since different envs may or may not be running
     ;; the indexer when we run this test.
     (u/without-publishing-messages
      (:concept-id
       (mdb/save-concept (u/conn-context)
                         {:format "application/echo10+xml"
                          :metadata (umm-core/umm->xml granule-umm :echo10)
                          :concept-type :granule
                          :provider-id "PROV1"
                          :native-id native-id
                          :revision-id 1
                          :extra-fields {:short-name short-name
                                         :entry-title short-name
                                         :entry-id entry-id
                                         :granule-ur granule-ur
                                         :version-id version-id
                                         :parent-collection-id parent-collection-id
                                         :parent-entry-title parent-entry-title}}))))))

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
        gran1 (save-granule coll1)
        ;; local helpers to make the body of the test cleaner
        create-acl #(:concept_id (ac/create-acl (u/conn-context) % {:token token}))
        update-acl #(ac/update-acl (u/conn-context) %1 %2 {:token token})
        get-collection-permissions #(get-permissions %1 coll1)
        get-granule-permissions #(get-permissions %1 gran1)]

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
        (u/wait-until-indexed)

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
              (u/wait-until-indexed)
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
               :catalog_item_identity {:name "coll2 guest read"
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
                :registered []))))))

    (testing "granule level permissions"
      (testing "no permissions granted"
        (is (= {gran1 []}
               (get-granule-permissions "user1")))))))

(deftest collection-catalog-item-identifier-access-value-test
  ;; tests ACLs which grant access to collections based on their access value
  (let [token (e/login-guest (u/conn-context))
        save-access-value-collection (fn [short-name access-value]
                                         (u/save-collection {:entry-title (str short-name " entry title")
                                                             :short-name short-name
                                                             :native-id short-name
                                                             :provider-id "PROV1"
                                                             :access-value access-value}))
        ;; one collection with a low access value
        coll1 (save-access-value-collection "coll1" 1)
        ;; one with an intermediate access value
        coll2 (save-access-value-collection "coll2" 4)
        ;; one with a higher access value
        coll3 (save-access-value-collection "coll3" 9)
        ;; and one with no access value
        coll4 (save-access-value-collection "coll4" nil)
        create-acl #(:concept_id (ac/create-acl (u/conn-context) % {:token token}))
        update-acl #(ac/update-acl (u/conn-context) %1 %2 {:token token})
        get-coll-permissions #(get-permissions :guest coll1 coll2 coll3 coll4)]

    (u/wait-until-indexed)

    (testing "no permissions granted"
      (is (= {coll1 []
              coll2 []
              coll3 []
              coll4 []}
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
                coll4 []}
               (get-coll-permissions))))

      (testing "ACL matching only high access values"
        (update-acl acl-id {:group_permissions [{:permissions [:read]
                                                 :user_type :guest}]
                            :catalog_item_identity {:name "coll2 guest read"
                                                    :collection_applicable true
                                                    :collection_identifier {:access_value {:min_value 4 :max_value 10}}
                                                    :provider_id "PROV1"}})

        (is (= {coll1 []
                coll2 ["read"]
                coll3 ["read"]
                coll4 []}
               (get-coll-permissions))))

      (testing "ACL matching only one access value"
        (update-acl acl-id {:group_permissions [{:permissions [:read]
                                                 :user_type :guest}]
                            :catalog_item_identity {:name "coll2 guest read"
                                                    :collection_applicable true
                                                    :collection_identifier {:access_value {:min_value 4 :max_value 5}}
                                                    :provider_id "PROV1"}})

        (is (= {coll1 []
                coll2 ["read"]
                coll3 []
                coll4 []}
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
                coll4 ["read"]}
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

    (u/wait-until-indexed)
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
        ;; required for ACLs that will reference this group
        _ (u/wait-until-indexed)
        save-prov1-collection #(u/save-collection {:provider-id "PROV1"
                                                      :entry-title (str % " entry title")
                                                      :native-id %
                                                      :short-name %})
        coll1 (save-prov1-collection "coll1")
        gran1 (save-granule coll1)
        create-acl #(:concept_id (ac/create-acl (u/conn-context) % {:token token}))
        update-acl #(ac/update-acl (u/conn-context) %1 %2 {:token token})]

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
        ;; required for ACLs that will reference this group
        _ (u/wait-until-indexed)
        save-prov1-collection #(u/save-collection {:provider-id "PROV1"
                                                     :entry-title (str % " entry title")
                                                     :native-id %
                                                     :short-name %})
        coll1 (save-prov1-collection "coll1")
        coll2 (save-prov1-collection "coll2")
        gran1 (save-granule coll1)
        gran2 (save-granule coll2)
        create-acl #(:concept_id (ac/create-acl (u/conn-context) % {:token token}))
        update-acl #(ac/update-acl (u/conn-context) %1 %2 {:token token})]

    (u/wait-until-indexed)

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
        (are [user permissions]
          (= {gran1 permissions
              ;; also ensure that the other granule under coll2 doesn't get any permissions from this ACL
              gran2 []}
             (get-permissions user gran1 gran2))
          :guest ["read"]
          :registered []
          "user1" [])

        (testing "permissions granted to registered users"
          (update-acl acl {:group_permissions [{:permissions [:read :order]
                                                :user_type :registered}]
                           :catalog_item_identity {:name "prov1 granule read"
                                                   :granule_applicable true
                                                   :collection_applicable true
                                                   :collection_identifier {:entry_titles ["coll1 entry title"]}
                                                   :provider_id "PROV1"}})

          (are [user permissions]
            (= {gran1 permissions
                gran2 []}
               (get-permissions user gran1 gran2))
            :guest []
            :registered ["read" "order"]
            "user1" ["read" "order"]))

        (testing "no permissions are granted with granule_applicable = false"
          (update-acl acl {:group_permissions [{:permissions [:read :order]
                                                :user_type :registered}]
                           :catalog_item_identity {:name "prov1 granule read"
                                                   :granule_applicable false
                                                   :collection_applicable true
                                                   :collection_identifier {:entry_titles ["coll1 entry title"]}
                                                   :provider_id "PROV1"}})

          (are [user permissions]
            (= {gran1 permissions
                gran2 []}
               (get-permissions user gran1 gran2))
            :guest []
            :registered []
            "user1" []))

        (testing "permissions granted to a specific group"
          (update-acl acl {:group_permissions [{:permissions [:read]
                                                :group_id created-group-concept-id}]
                           :catalog_item_identity {:name "prov1 granule read"
                                                   :granule_applicable true
                                                   :collection_applicable true
                                                   :collection_identifier {:entry_titles ["coll1 entry title"]}
                                                   :provider_id "PROV1"}})

          (are [user permissions]
            (= {gran1 permissions
                gran2 []}
               (get-permissions user gran1 gran2))
            :guest []
            :registered []
            "user1" ["read"]))))))

(deftest granule-permissions-with-access-value-test
  (let [token (e/login (u/conn-context) "user1" ["group-create-group"])
        group (u/make-group {:name "groupwithuser1" :members ["user1"]})
        created-group-concept-id (:concept_id (u/create-group token group))
        ;; required for ACLs that will reference this group
        _ (u/wait-until-indexed)
        save-prov1-collection #(u/save-collection {:provider-id "PROV1"
                                                   :entry-title (str % " entry title")
                                                   :native-id %
                                                   :short-name %})
        coll1 (save-prov1-collection "coll1")
        ;; no access value
        gran1 (save-granule coll1)
        ;; mid access value
        gran2 (save-granule coll1 {:access-value 5})
        ;; high access value
        gran3 (save-granule coll1 {:access-value 10})
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
                                                  :granule_identifier {:access_value {:max_value 7}}
                                                  :provider_id "PROV1"}})
        ;; specific group read granules with access value 7 or higher
        acl3 (create-acl {:group_permissions [{:permissions [:read]
                                               :user_type :registered}]
                          :catalog_item_identity {:name "prov1 granules w/ min access value"
                                                  :granule_applicable true
                                                  :granule_identifier {:access_value {:min_value 7}}
                                                  :provider_id "PROV1"}})]
    (u/wait-until-indexed)
    (are [user result]
      (= result (get-permissions user gran1 gran2 gran3))
      :guest {gran1 ["read"]
              gran2 []
              gran3 []}
      :registered {gran1 []
                   gran2 ["read"]
                   gran3 ["read"]})))

(deftest granule-permissions-with-temporal-value-test
  (let [token (e/login (u/conn-context) "user1" ["group-create-group"])
        group (u/make-group {:name "groupwithuser1" :members ["user1"]})
        created-group-concept-id (:concept_id (u/create-group token group))
        ;; required for ACLs that will reference this group
        _ (u/wait-until-indexed)
        save-prov1-collection #(u/save-collection {:provider-id "PROV1"
                                                   :entry-title (str % " entry title")
                                                   :native-id %
                                                   :short-name %})
        coll1 (save-prov1-collection "coll1")
        ;; no temporal
        gran1 (save-granule coll1)
        ;; temporal range
        gran2 (save-granule coll1 {:temporal {:range-date-time {:beginning-date-time "2002-01-01T00:00:00Z"
                                                                :ending-date-time "2005-01-01T00:00:00Z"}}})
        ;; single date-time
        gran3 (save-granule coll1 {:temporal {:single-date-time "1999-01-01T00:00:00Z"}})

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
                                                  :provider_id "PROV1"}})]
    (u/wait-until-indexed)
    (are [user result]
      (= result (get-permissions user gran1 gran2 gran3))
      :guest {gran1 []
              gran2 ["read"]
              gran3 []}
      :registered {gran1 []
                   gran2 []
                   gran3 ["read"]})))

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
        create-acl #(ac/create-acl (u/conn-context) % {:token token})
        update-acl #(ac/update-acl (u/conn-context) %1 %2 {:token token})
        get-coll1-permissions #(get-permissions % coll1)]

    (testing "no permissions granted"
      (are [user permissions]
        (= {coll1 permissions}
           (get-coll1-permissions user))
        :guest []
        :registered []
        "user1" []))

    (testing "provider level permissions"
      ;; update in the IMA grants update AND delete
      (let [acl {:group_permissions [{:permissions [:update]
                                      :user_type :guest}]
                 :provider_identity {:provider_id "PROV1"
                                     :target "INGEST_MANAGEMENT_ACL"}}
            acl-concept-id (:concept_id (create-acl acl))]
        (u/wait-until-indexed)

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
        create-acl #(ac/create-acl (u/conn-context) % {:token token})
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
        (= {"GROUP" permissions}
           (get-system-permissions user "GROUP"))
        :guest []
        :registered []
        "user1" []))

    (let [acl {:group_permissions [{:permissions [:read]
                                    :user_type :guest}]
               :system_identity {:target "GROUP"}}
          acl-concept-id (:concept_id (create-acl acl))]
      (u/wait-until-indexed)

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
        (create-acl {:group_permissions [{:permissions [:create]
                                          :user_type :registered}]
                     :system_identity {:target "PROVIDER"}})
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
      (u/wait-until-indexed)

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
                    {:group_permissions [{:permissions [:read]
                                          :group_id created-group-concept-id}]
                     :provider_identity {:provider_id "PROV1"
                                         :target "PROVIDER_HOLDINGS"}})

        (are [user permissions]
          (= {"PROVIDER_HOLDINGS" permissions}
             (get-provider-permissions user "PROV1" "PROVIDER_HOLDINGS"))
          :guest []
          :registered []
          "user1" ["read"]
          "user2" [])))))
