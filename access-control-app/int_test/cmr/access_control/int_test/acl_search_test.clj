(ns cmr.access-control.int-test.acl-search-test
  (require [clojure.test :refer :all]
           [cmr.transmit.access-control :as ac]
           [cmr.mock-echo.client.echo-util :as e]
           [cmr.common.util :as util :refer [are3]]
           [cmr.access-control.int-test.fixtures :as fixtures]
           [cmr.access-control.test.util :as u]
           [cmr.access-control.data.access-control-index :as access-control-index]))

(use-fixtures :each
  (fixtures/reset-fixture {"prov1guid" "PROV1", "prov2guid" "PROV2"}
                          ["user1" "user2" "user3" "user4" "user5"])
  (fixtures/grant-all-group-fixture ["prov1guid" "prov2guid"]))
(use-fixtures :once (fixtures/int-test-fixtures))


(deftest invalid-search-test
  (testing "Accept header"
    (testing "Other than JSON is rejected"
      (is (= {:status 400
              :body {:errors ["The mime types specified in the accept header [application/text] are not supported."]}
              :content-type :json}
             (ac/search-for-acls (u/conn-context) {} {:http-options {:accept "application/text"}
                                                      :raw? true}))))
    (testing "No Accept header is ok"
      (is (= 200
             (:status (ac/search-for-acls (u/conn-context) {} {:http-options {:accept nil} :raw? true}))))))
  (testing "Unknown parameters are rejected"
    (is (= {:status 400
            :body {:errors ["Parameter [foo] was not recognized."]}
            :content-type :json}
           (ac/search-for-acls (u/conn-context) {:foo "bar"} {:raw? true})))))

(def sample-system-acl
  "A sample system ACL."
  {:group_permissions [{:user_type "guest" :permissions ["create"]}]
   :system_identity {:target "REPLACME"}})

(def sample-provider-acl
  "A sample provider ACL."
  {:group_permissions [{:user_type "guest" :permissions ["create"]}]
   :provider_identity {:target "REPLACME"
                       :provider_id "PROV1"}})

(def sample-single-instance-acl
  "A sample single instance ACL."
  {:group_permissions [{:user_type "guest" :permissions ["create"]}]
   :single_instance_identity {:target "GROUP_MANAGEMENT"
                              :target_id "REPLACEME"}})

(def sample-catalog-item-acl
  "A sample catalog item ACL."
  {:group_permissions [{:user_type "guest" :permissions ["create"]}]
   :catalog_item_identity {:name "REPLACEME"
                           :provider_id "PROV1"
                           :collection_applicable true}})

(defn system-acl
  "Creates a system acl for testing with the given target."
  [target]
  (assoc-in sample-system-acl [:system_identity :target] target))

(defn provider-acl
  "Creates a provider acl for testing with the given target."
  [target]
  (assoc-in sample-provider-acl [:provider_identity :target] target))

(defn single-instance-acl
  "Creates a single instance acl for testing with the given group concept id as the target."
  [group-concept-id]
  (assoc-in sample-single-instance-acl [:single_instance_identity :target_id] group-concept-id))

(defn catalog-item-acl
  "Creates a catalog item acl for testing with the given name."
  [name]
  (assoc-in sample-catalog-item-acl [:catalog_item_identity :name] name))

(defn ingest-acl
  "Ingests the acl. Returns the ACL with the concept id and revision id."
  [token acl]
  (let [{:keys [concept_id revision_id]} (ac/create-acl (u/conn-context) acl {:token token})]
    (assoc acl :concept-id concept_id :revision-id revision_id)))

(defn acl->search-response-item
  "Returns the expected search response item for an ACL."
  [acl]
  (let [acl (util/map-keys->kebab-case acl)
        {:keys [protocol host port context]} (get-in (u/conn-context) [:system :access-control-connection])
        expected-location (format "%s://%s:%s%s/acls/%s"
                                  protocol host port context (:concept-id acl))]
    {:name (access-control-index/acl->display-name acl)
     :revision_id (:revision-id acl),
     :concept_id (:concept-id acl)
     :identity_type (access-control-index/acl->identity-type acl)
     :location expected-location}))

(defn acls->search-response
  "Returns the expected search response for a given number of hits and the acls."
  ([hits acls]
   (acls->search-response hits acls 10 1))
  ([hits acls page-size page-num]
   (let [all-items (->> acls
                        (map acl->search-response-item)
                        (sort-by :name)
                        vec)
         start (* (dec page-num) page-size)
         end (+ start page-size)
         end (if (> end hits) hits end)
         items (subvec all-items start end)]
     {:hits hits
      :items items})))

(deftest acl-search-test
  (let [token (e/login (u/conn-context) "user1")
        acl1 (ingest-acl token (system-acl "SYSTEM_AUDIT_REPORT"))
        acl2 (ingest-acl token (system-acl "METRIC_DATA_POINT_SAMPLE"))
        acl3 (ingest-acl token (system-acl "SYSTEM_INITIALIZER"))
        acl4 (ingest-acl token (system-acl "ARCHIVE_RECORD"))

        acl5 (ingest-acl token (provider-acl "AUDIT_REPORT"))
        acl6 (ingest-acl token (provider-acl "OPTION_ASSIGNMENT"))

        ;; Eventually validation will prevent this without creating the group first.
        acl7 (ingest-acl token (single-instance-acl "AG1234-CMR"))
        acl8 (ingest-acl token (single-instance-acl "AG1235-CMR"))

        acl9 (ingest-acl token (catalog-item-acl "All Collections"))
        acl10 (ingest-acl token (catalog-item-acl "All Granules"))

        system-acls [acl1 acl2 acl3 acl4]
        provider-acls [acl5 acl6]
        single-instance-acls [acl7 acl8]
        catalog-item-acls [acl9 acl10]
        all-acls (concat system-acls provider-acls single-instance-acls catalog-item-acls)]
    (u/wait-until-indexed)

    (testing "Find all ACLs"
      (let [response (ac/search-for-acls (u/conn-context) {})]
        (is (= (acls->search-response (count all-acls) all-acls)
               (dissoc response :took)))
        (testing "Expected Names"
          ;; We verify the exact expected names here to ensure that they look correct.
          (is (= ["All Collections"
                  "All Granules"
                  "Group - AG1234-CMR"
                  "Group - AG1235-CMR"
                  "Provider - PROV1 - AUDIT_REPORT"
                  "Provider - PROV1 - OPTION_ASSIGNMENT"
                  "System - ARCHIVE_RECORD"
                  "System - METRIC_DATA_POINT_SAMPLE"
                  "System - SYSTEM_AUDIT_REPORT"
                  "System - SYSTEM_INITIALIZER"]
                 (map :name (:items response)))))))
    (testing "ACL Search Paging"
      (testing "Page Size"
        (is (= (acls->search-response (count all-acls) all-acls 4 1)
               (dissoc (ac/search-for-acls (u/conn-context) {:page_size 4}) :took))))
      (testing "Page Number"
        (is (= (acls->search-response (count all-acls) all-acls 4 2)
               (dissoc (ac/search-for-acls (u/conn-context) {:page_size 4 :page_num 2}) :took)))))))

(deftest acl-search-permitted-group-test
  (let [token (e/login (u/conn-context) "user1")
        acl1 (ingest-acl token (system-acl "SYSTEM_AUDIT_REPORT"))
        acl2 (ingest-acl token (assoc (system-acl "METRIC_DATA_POINT_SAMPLE")
                                      :group_permissions
                                      [{:user_type "registered" :permissions ["create"]}]))
        acl3 (ingest-acl token (assoc (system-acl "ARCHIVE_RECORD")
                                      :group_permissions
                                      [{:group_id "AG12345-PROV" :permissions ["create"]}]))

        acl4 (ingest-acl token (provider-acl "AUDIT_REPORT"))
        acl5 (ingest-acl token (assoc (provider-acl "OPTION_DEFINITION")
                                      :group_permissions
                                      [{:user_type "registered" :permissions ["create"]}]))
        acl6 (ingest-acl token (assoc (provider-acl "OPTION_ASSIGNMENT")
                                      :group_permissions
                                      [{:group_id "AG12345-PROV" :permissions ["create"]}]))

        acl7 (ingest-acl token (catalog-item-acl "All Collections"))
        acl8 (ingest-acl token (assoc (catalog-item-acl "All Granules")
                                      :group_permissions
                                      [{:user_type "registered" :permissions ["create"]}
                                       {:group_id "AG10000-PROV" :permissions ["create"]}]))

        guest-acls [acl1 acl4 acl7]
        registered-acls [acl2 acl5 acl8]
        AG12345-acls [acl3 acl6]
        AG10000-acls [acl8]
        all-acls (concat guest-acls registered-acls AG12345-acls)]
    (u/wait-until-indexed)

    (testing "Search ACLs by permitted group"
      (are [permitted-groups acls]
           (let [response (ac/search-for-acls (u/conn-context) {:permitted-group permitted-groups})]
             (= (acls->search-response (count acls) acls)
                (dissoc response :took)))

           ["guest"] guest-acls
           ["registered"] registered-acls
           ["AG12345-PROV"] AG12345-acls
           ["AG10000-PROV"] AG10000-acls
           ;; permitted-group search is case insensitive by default
           ["REGISTERED" "AG10000-PROV"] registered-acls
           ["GUEST" "AG10000-PROV"] (concat guest-acls AG10000-acls)
           ["AG12345-PROV" "AG10000-PROV"] (concat AG12345-acls AG10000-acls)
           ["guest" "registered" "AG12345-PROV" "AG10000-PROV"] all-acls))

    (testing "Search ACLs by permitted group with options"
      (are [permitted-groups options acls]
           (let [response (ac/search-for-acls (u/conn-context)
                                              (merge {:permitted-group permitted-groups} options))]
             (= (acls->search-response (count acls) acls)
                (dissoc response :took)))

           ["GUEST"] {"options[permitted_group][ignore_case]" true} guest-acls
           ["GUEST"] {"options[permitted_group][ignore_case]" false} []))

    (testing "Search ACLs by permitted group with invalid values"
      (are [permitted-groups invalid-msg]
           (= {:status 400
               :body {:errors [(format "Parameter permitted_group has invalid values [%s]. Only 'guest', 'registered' or a group concept id can be specified."
                                       invalid-msg)]}
               :content-type :json}
              (ac/search-for-acls (u/conn-context) {:permitted-group permitted-groups} {:raw? true}))

           ["gust"] "gust"
           ["GUST" "registered" "AG10000-PROV" "G10000-PROV"] "GUST, G10000-PROV"))))

(deftest acl-search-by-identity-type-test
  (let [token (e/login (u/conn-context) "user1")
        acl-system (ingest-acl token (system-acl "SYSTEM_AUDIT_REPORT"))
        acl-provider (ingest-acl token (provider-acl "AUDIT_REPORT"))
        acl-single-instance (ingest-acl token (single-instance-acl "AG1234-CMR"))
        acl-catalog-item (ingest-acl token (catalog-item-acl "All Collections"))
        all-acls [acl-system acl-provider acl-single-instance acl-catalog-item]]
    (u/wait-until-indexed)

    (testing "Search with invalid identity type returns error"
      (is (= {:status 400
              :body {:errors [(str "Parameter identity_type has invalid values [foo]. "
                                   "Only 'provider', 'system', 'single_instance', or 'catalog_item' can be specified.")]}
              :content-type :json}
             (ac/search-for-acls (u/conn-context) {:identity-type "foo"} {:raw? true}))))

    (testing "Search with valid identity types"
      (are3 [identity-types expected-acls]
        (let [response (ac/search-for-acls (u/conn-context) {:identity-type identity-types})]
          (is (= (acls->search-response (count expected-acls) expected-acls)
                 (dissoc response :took))))

        "Identity type 'provider'"
        ["provider"] [acl-provider]

        "Identity type 'system'"
        ["system"] [acl-system]

        "Identity type 'single_instance'"
        ["single_instance"] [acl-single-instance]

        "Identity type 'catalog_item'"
        ["catalog_item"] [acl-catalog-item]

        "Multiple identity types"
        ["provider" "single_instance"] [acl-provider acl-single-instance]

        "All identity types"
        ["provider" "system" "single_instance" "catalog_item"] all-acls

        "Identity type searches are always case-insensitive"
        ["PrOvIdEr"] [acl-provider]))))

(deftest acl-search-by-permitted-user-test
  (let [token (e/login (u/conn-context) "user1")
        group1 (u/ingest-group token {:name "group1"} ["user1"])
        group2 (u/ingest-group token {:name "group2"} ["USER1" "user2"])
        group3 (u/ingest-group token {:name "group3"} nil)
        acl-guest (ingest-acl token (system-acl "SYSTEM_AUDIT_REPORT"))
        acl-registered (ingest-acl token (assoc (system-acl "METRIC_DATA_POINT_SAMPLE"))
                                      :group_permissions
                                      [{:user_type "registered" :permissions ["create"]}])
        acl-group1 (ingest-acl token (assoc (system-acl "ARCHIVE_RECORD")
                                      :group_permissions
                                      [{:group_id (:concept_id group1) :permissions ["create"]}]))

        acl5 (ingest-acl token (assoc (provider-acl "OPTION_DEFINITION")
                                      :group_permissions
                                      [{:user_type "registered" :permissions ["create"]}]))
        acl6 (ingest-acl token (assoc (provider-acl "OPTION_ASSIGNMENT")
                                      :group_permissions
                                      [{:group_id (:concept_id group2) :permissions ["create"]}]))

        acl7 (ingest-acl token (assoc (catalog-item-acl "All Granules")
                                      :group_permissions
                                      [{:group_id (:concept_id group3) :permissions ["create"]}]))

        guest-acls [acl1 acl4 acl7]
        registered-acls [acl2 acl5 acl8]
        all-acls [acl1 acl2 acl3 acl4 acl5 acl6 acl7]]

    (u/wait-until-indexed)

    (testing "Search with non-existent user type returns error"
      (is (= {:status 400
              :body {:errors [(str "The following users do not exist [foo]")]}
              :content-type :json}
             (ac/search-for-acls (u/conn-context) {:permitted-user "foo"} {:raw? true}))))
    (testing "Search with valid users"
      (are3 [users expected-acls]
        (let [response (ac/search-for-acls (u/conn-context) {:permitted-user users})]
          (is (= (acls->search-response (count expected-acls) expected-acls)
                (dissoc response :took))))

        "User1 is not in group3"
        ["user1"] all-acls))))

        ; "Identity type 'system'"
        ; ["system"] [acl-system]
        ;
        ; "Identity type 'single_instance'"
        ; ["single_instance"] [acl-single-instance]
        ;
        ; "Identity type 'catalog_item'"
        ; ["catalog_item"] [acl-catalog-item]
        ;
        ;  "Multiple identity types"
        ;  ["provider" "single_instance"] [acl-provider acl-single-instance]
        ;
        ;  "All identity types"
        ;  ["provider" "system" "single_instance" "catalog_item"] all-acls
        ;
        ;  "Identity type searches are always case-insensitive"
        ;  ["PrOvIdEr"] [acl-provider]))))
