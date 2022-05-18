(ns cmr.system-int-test.access-control.edl-group-test
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.access-control.config :as access-control-config]
   [cmr.access-control.test.util :as u]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.transmit.access-control :as ac]
   [cmr.transmit.config :as transmit-config]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"
                                           "provguid3" "PROV3"}
                                          {:grant-all-search? false
                                           :grant-all-ingest? false
                                           :grant-all-access-control? true}))


(defn create-acl
  [acl]
  (ac/create-acl (u/conn-context) acl {:token (transmit-config/echo-system-token)}))

(deftest create-acl-with-edl-id
  (let [acl {:group_permissions [{:group_id "EDLGroupName1"
                                  :permissions ["read" "order"]}]
             :catalog_item_identity {:name "test"
                                     :provider_id "PROV1"
                                     :granule_applicable true
                                     :collection_applicable true}}]

    (testing "Can make an ACL with an EDL group ID when toggle set true"
      (dev-sys-util/eval-in-dev-sys `(access-control-config/set-enable-edl-groups! true))
      (is (= 200 (:status (data-core/create-acl acl)))))

    (testing "Error returned when try to ingest ACL with an EDL group ID when toggle set false"
      (dev-sys-util/eval-in-dev-sys `(access-control-config/set-enable-edl-groups! false))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"clj-http: status 400"
                            (data-core/create-acl acl))))))

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
  (let [token (e/login (u/conn-context) "user1" ["group-create-group"])
        save-prov1-collection #(u/save-collection {:provider-id "PROV1"
                                                   :entry-title (str % " entry title")
                                                   :native-id %
                                                   :short-name %})
        coll1 (save-prov1-collection "coll1")
        get-collection-permissions #(get-permissions %1 coll1)
        fixture-acls (:items (u/search-for-acls (transmit-config/echo-system-token) {:target "INGEST_MANAGEMENT_ACL"}))
        _ (doseq [fixture-acl fixture-acls]
            (e/ungrant (u/conn-context) (:concept_id fixture-acl)))]

      (testing "acls granting collection catalog-item-identity access to edl groups"
        (dev-sys-util/eval-in-dev-sys `(access-control-config/set-enable-edl-groups! true))
        (create-acl {:group_permissions [{:permissions [:read :order]
                                          :group_id "cmr_test_group"}]
                     :catalog_item_identity {:name "coll1 read and order"
                                             :collection_applicable true
                                             :provider_id "PROV1"}})

        (are [user permissions]
          (= {coll1 permissions}
             (get-collection-permissions user))
          :guest []
          :registered []
          "user1" ["read" "order"]
          "user2" []))))

(deftest granule-permissions-test
  (let [token (e/login (u/conn-context) "user1" ["group-create-group"])
        save-prov1-collection #(u/save-collection {:provider-id "PROV1"
                                                   :entry-title (str % " entry title")
                                                   :native-id %
                                                   :short-name %})
        coll1 (save-prov1-collection "coll1")
        gran1 (u/save-granule coll1)]
    (testing "permissions granted to a edl group"
      (dev-sys-util/eval-in-dev-sys `(access-control-config/set-enable-edl-groups! true))
      (create-acl {:group_permissions [{:permissions [:read]
                                        :group_id "cmr_test_group"}]
                   :catalog_item_identity {:name "prov1 granule read"
                                           :granule_applicable true
                                           :provider_id "PROV1"}})

      (are [user permissions]
        (= {gran1 permissions}
           (get-permissions user gran1))
        :guest []
        :registered []
        "user1" ["read"]))))

(deftest collection-provider-level-permission-check-test
  (let [token (e/login (u/conn-context) "user1" ["group-create-group"])
        coll1 (u/save-collection {:provider-id "PROV1"
                                  :entry-title "coll1"
                                  :native-id "coll1"
                                  :short-name "coll1"})
        get-coll1-permissions #(get-permissions % coll1)
        fixture-acls (:items (u/search-for-acls (transmit-config/echo-system-token) {:target "INGEST_MANAGEMENT_ACL"}))
        _ (doseq [fixture-acl fixture-acls]
            (e/ungrant (u/conn-context) (:concept_id fixture-acl)))]

    (testing "granted to edl groups"
      (dev-sys-util/eval-in-dev-sys `(access-control-config/set-enable-edl-groups! true))
      (create-acl {:group_permissions [{:permissions [:update]
                                        :group_id "cmr_test_group"}]
                   :provider_identity {:provider_id "PROV1"
                                       :target "INGEST_MANAGEMENT_ACL"}})

      (are [user permissions]
        (= {coll1 permissions}
           (get-coll1-permissions user))
        :guest []
        :registered []
        "user1" ["update" "delete"]
        "user2" []))))

(deftest system-provider-object-permission-check
  (let [token (e/login (u/conn-context) "user1" ["group-create-group"])
        get-system-permissions (fn [user system-object]
                                 (json/parse-string
                                  (ac/get-permissions
                                   (u/conn-context)
                                   (merge {:system_object system-object}
                                          (if (keyword? user)
                                            {:user_type (name user)}
                                            {:user_id user})))))
        get-provider-permissions (fn [user provider-id provider-object]
                                   (json/parse-string
                                    (ac/get-permissions
                                     (u/conn-context)
                                     (merge {:target provider-object
                                             :provider provider-id}
                                            (if (keyword? user)
                                              {:user_type (name user)}
                                              {:user_id user})))))]

    (testing "provider object permissions granted to edl groups"
      (dev-sys-util/eval-in-dev-sys `(access-control-config/set-enable-edl-groups! true))
      (create-acl {:group_permissions [{:permissions [:create :read :update :delete]
                                        :group_id "cmr_test_group"}]
                   :provider_identity {:provider_id "PROV1"
                                       :target "PROVIDER_OBJECT_ACL"}})

      (are [user permissions]
        (= {"PROVIDER_OBJECT_ACL" (set permissions)}
           (update (get-provider-permissions user "PROV1" "PROVIDER_OBJECT_ACL") "PROVIDER_OBJECT_ACL" set))
        :guest []
        :registered []
        "user1" ["create" "read" "update" "delete"]
        "user2" []))

    (testing "system permissions granted to edl groups"
      (dev-sys-util/eval-in-dev-sys `(access-control-config/set-enable-edl-groups! true))
      (let [concept-id (e/grant
                        (u/conn-context)
                        [{:permissions [:read]
                          :group_id "cmr_test_group"}]
                        :system_identity {:target "GROUP"})]
        (ac/update-acl (u/conn-context)
                       concept-id
                       {:group_permissions [{:permissions [:read]
                                             :group_id "cmr_test_group"}]
                        :system_identity {:target "GROUP"}}
                       {:token (transmit-config/echo-system-token)}))

      (are [user permissions]
        (= {"GROUP" permissions}
           (get-system-permissions user "GROUP"))
        :guest []
        :registered []
        "user1" ["read"]
        "user2" []))))
