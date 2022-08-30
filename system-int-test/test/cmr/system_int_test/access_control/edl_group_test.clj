(ns cmr.system-int-test.access-control.edl-group-test
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.access-control.config :as access-control-config]
   [cmr.access-control.test.util :as u]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.mock-echo.client.mock-urs-client :as mock-urs-client]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.transmit.access-control :as ac]
   [cmr.transmit.config :as transmit-config]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}
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

(defn get-current-sids
  "For given token, returns list of sids."
  [token]
  (json/parse-string
   (ac/get-current-sids
    (u/conn-context)
    token)))

(deftest toggle-cmr-group-sids
  (mock-urs-client/create-users (u/conn-context) [{:username "edl-group-user1" 
                                                   :password "edl-group-user1-pass"}])
  (dev-sys-util/eval-in-dev-sys `(access-control-config/set-enable-edl-groups! true))
  (let [cmr-group (:concept_id
                   (u/ingest-group
                    (transmit-config/echo-system-token)
                    {:name "cmr-test-group"}
                    ["edl-group-user1"]))
        token (e/login (system/context) "edl-group-user1")]

    ;; Explicitly setting configs for each test case for clarity.
    (testing "Both EDL and CMR group sids are turned on"
      (dev-sys-util/eval-in-dev-sys `(access-control-config/set-enable-edl-groups! true))
      (dev-sys-util/eval-in-dev-sys `(access-control-config/set-enable-cmr-group-sids! true))
      (is (= (set (get-current-sids token)) (set ["registered" cmr-group "group-id-1" "group-id-2"]))))
    (testing "Both EDL and CMR group sids are turned off"
      (dev-sys-util/eval-in-dev-sys `(access-control-config/set-enable-edl-groups! false))
      (dev-sys-util/eval-in-dev-sys `(access-control-config/set-enable-cmr-group-sids! false))
      (is (= (get-current-sids token) ["registered"])))
    (testing "EDL sids are on and CMR group sids are off"
      (dev-sys-util/eval-in-dev-sys `(access-control-config/set-enable-edl-groups! true))
      (dev-sys-util/eval-in-dev-sys `(access-control-config/set-enable-cmr-group-sids! false))
      (is (= (set (get-current-sids token)) (set ["registered" "group-id-1" "group-id-2"]))))
    (testing "EDL sids are off and CMR group sids are on"
      (dev-sys-util/eval-in-dev-sys `(access-control-config/set-enable-edl-groups! false))
      (dev-sys-util/eval-in-dev-sys `(access-control-config/set-enable-cmr-group-sids! true))
      (is (= (set (get-current-sids token)) (set ["registered" cmr-group]))))))

(deftest collection-simple-catalog-item-identity-permission-check-test
  (let [save-prov1-collection #(u/save-collection {:provider-id "PROV1"
                                                   :entry-title (str % " entry title")
                                                   :native-id %
                                                   :short-name %})
        coll1 (save-prov1-collection "coll1")
        get-collection-permissions #(get-permissions %1 coll1)
        fixture-acls (:items (u/search-for-acls (transmit-config/echo-system-token) {:target "INGEST_MANAGEMENT_ACL"}))]
    (doseq [fixture-acl fixture-acls]
      (e/ungrant (u/conn-context) (:concept_id fixture-acl)))

    (testing "acls granting collection catalog-item-identity access to edl groups"
      (dev-sys-util/eval-in-dev-sys `(access-control-config/set-enable-edl-groups! true))
      (create-acl {:group_permissions [{:permissions [:read :order]
                                        :group_id "group-id-1"}]
                   :catalog_item_identity {:name "coll1 read and order"
                                           :collection_applicable true
                                           :provider_id "PROV1"}})

      (are [user permissions]
        (= {coll1 permissions}
           (get-collection-permissions user))
        :guest []
        :registered []
        "edl-group-user1" ["read" "order"]
        "user2" []))))

(deftest granule-permissions-test
  (let [save-prov1-collection #(u/save-collection {:provider-id "PROV1"
                                                   :entry-title (str % " entry title")
                                                   :native-id %
                                                   :short-name %})
        coll1 (save-prov1-collection "coll1")
        gran1 (u/save-granule coll1)]
    (testing "permissions granted to a edl group"
      (dev-sys-util/eval-in-dev-sys `(access-control-config/set-enable-edl-groups! true))
      (create-acl {:group_permissions [{:permissions [:read]
                                        :group_id "group-id-1"}]
                   :catalog_item_identity {:name "prov1 granule read"
                                           :granule_applicable true
                                           :provider_id "PROV1"}})

      (are [user permissions]
        (= {gran1 permissions}
           (get-permissions user gran1))
        :guest []
        :registered []
        "edl-group-user1" ["read"]))))

(deftest collection-provider-level-permission-check-test
  (let [coll1 (u/save-collection {:provider-id "PROV1"
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
                                        :group_id "group-id-1"}]
                   :provider_identity {:provider_id "PROV1"
                                       :target "INGEST_MANAGEMENT_ACL"}})

      (are [user permissions]
        (= {coll1 permissions}
           (get-coll1-permissions user))
        :guest []
        :registered []
        "edl-group-user1" ["update" "delete"]
        "user2" []))))

(deftest system-provider-object-permission-check
  (let [get-system-permissions (fn [user system-object]
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
                                        :group_id "group-id-1"}]
                   :provider_identity {:provider_id "PROV1"
                                       :target "PROVIDER_OBJECT_ACL"}})

      (are [user permissions]
        (= {"PROVIDER_OBJECT_ACL" (set permissions)}
           (update (get-provider-permissions user "PROV1" "PROVIDER_OBJECT_ACL") "PROVIDER_OBJECT_ACL" set))
        :guest []
        :registered []
        "edl-group-user1" ["create" "read" "update" "delete"]
        "user2" []))

    (testing "system permissions granted to edl groups"
      (dev-sys-util/eval-in-dev-sys `(access-control-config/set-enable-edl-groups! true))
      (let [concept-id (e/grant
                        (u/conn-context)
                        [{:permissions [:read]
                          :group_id "group-id-1"}]
                        :system_identity {:target "GROUP"})]
        (ac/update-acl (u/conn-context)
                       concept-id
                       {:group_permissions [{:permissions [:read]
                                             :group_id "group-id-1"}]
                        :system_identity {:target "GROUP"}}
                       {:token (transmit-config/echo-system-token)}))

      (are [user permissions]
        (= {"GROUP" permissions}
           (get-system-permissions user "GROUP"))
        :guest []
        :registered []
        "edl-group-user1" ["read"]
        "user2" []))))

(deftest search-acls-by-edl-group-test
  (dev-sys-util/eval-in-dev-sys `(access-control-config/set-enable-edl-groups! true))
  (e/login (system/context) "edl-group-user1")
  (e/login (system/context) "user1")
  (let [test-context (assoc (u/conn-context) :token (transmit-config/echo-system-token))
        fixture-acl-names ["Provider - PROV1 - GROUP" "System - GROUP"]]
    (testing "initial search find fixture ACLs"
      ;; search by permitted group
      (let [{:keys [hits items]} (ac/search-for-acls
                                  test-context {:permitted-group ["group-id-1"]})]
        (is (= 0 hits))
        (is (= [] (map :name items))))

      ; search by group permission
      (let [{:keys [hits items]} (ac/search-for-acls
                                  test-context
                                  {:group-permission {:0 {:permitted-group ["group-id-1"]}}})]
        (is (= 0 hits))
        (is (= [] (map :name items))))

      ;; search by permitted user
      (let [{:keys [hits items]} (ac/search-for-acls
                                  test-context {:permitted-user ["edl-group-user1"]})]
        (is (= 2 hits))
        (is (= (set fixture-acl-names) (set (map :name items))))))

    (testing "search ACLs with EDL group"
      (create-acl {:group_permissions [{:permissions [:read :order]
                                        :group_id "group-id-1"}]
                   :catalog_item_identity {:name "group1 read and order"
                                           :collection_applicable true
                                           :provider_id "PROV1"}})
      (create-acl {:group_permissions [{:permissions [:update]
                                        :group_id "group-id-2"}]
                   :provider_identity {:provider_id "PROV1"
                                       :target "INGEST_MANAGEMENT_ACL"}})
      (create-acl {:group_permissions [{:permissions [:read :order]
                                        :group_id "group-id-3"}]
                   :catalog_item_identity {:name "group3 read and order"
                                           :collection_applicable true
                                           :provider_id "PROV1"}})
      (u/wait-until-indexed)
      ;; search by permitted group
      (are3 [permitted-groups expected-hits expected-names]
        (let [{:keys [hits items]} (ac/search-for-acls
                                    test-context {:permitted-group permitted-groups})]
          (is (= expected-hits hits))
          (is (= expected-names (map :name items))))

        "search by EDL group, found"
        ["group-id-1"]
        1
        ["group1 read and order"]

        "search by EDL group, not found"
        ["non_existent_group"]
        0
        []

        "search by multiple EDL groups"
        ["group-id-1" "non_existent_group" "group-id-2"]
        2
        ["group1 read and order" "Provider - PROV1 - INGEST_MANAGEMENT_ACL"])

      ; search by group permission
      (are3 [permitted-groups expected-hits expected-names]
        (let [{:keys [hits items]} (ac/search-for-acls
                                    test-context
                                    {:group-permission {:0 {:permitted-group permitted-groups}}})]
          (is (= expected-hits hits))
          (is (= (set expected-names) (set (map :name items)))))

        "search by EDL group, found"
        ["group-id-1"]
        1
        ["group1 read and order"]

        "search by EDL group, not found"
        ["non_existent_group"]
        0
        []

        "search by multiple EDL groups"
        ["group-id-1" "non_existent_group" "group-id-2"]
        2
        ["group1 read and order" "Provider - PROV1 - INGEST_MANAGEMENT_ACL"])

      ;; search by permitted user find both EDL group ACLs
      (are3 [permitted-user expected-hits expected-names]
        (let [{:keys [hits items]} (ac/search-for-acls
                                    test-context {:permitted-user permitted-user})]
          (is (= expected-hits hits))
          (is (= (set expected-names) (set (map :name items)))))

        "search by EDL group via user, found"
        "edl-group-user1"
        4
        (concat fixture-acl-names
                ["group1 read and order"
                 "Provider - PROV1 - INGEST_MANAGEMENT_ACL"])

        "search by EDL group via user, not found"
        "user1"
        2
        fixture-acl-names)

      ;; CMR-8477 search all acls with include-legacy-group-guid true
      (testing "search all ACLs with EDL group with include-legacy-group-guid true"
        (let [{:keys [hits items]} (ac/search-for-acls
                                    test-context
                                    {:include-legacy-group-guid true
                                     :include-full-acl true})]
          (is (= 5 hits))
          (is (= (set (concat fixture-acl-names
                              ["group1 read and order"
                               "group3 read and order"
                               "Provider - PROV1 - INGEST_MANAGEMENT_ACL"]))
                 (set (map :name items)))))))))
