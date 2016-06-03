(ns cmr.access-control.int-test.acl-search-test
  (require [clojure.test :refer :all]
           [cmr.transmit.access-control :as ac]
           [cmr.mock-echo.client.echo-util :as e]
           [cmr.common.util :as util :refer [are2]]
           [cmr.access-control.int-test.fixtures :as fixtures]
           [cmr.access-control.test.util :as u]
           [cmr.access-control.data.access-control-index :as access-control-index]))

(use-fixtures :each
              (fixtures/reset-fixture {"prov1guid" "PROV1", "prov2guid" "PROV2"}
                                      ["user1" "user2" "user3" "user4" "user5"])
              (fixtures/grant-all-group-fixture ["prov1guid" "prov2guid"]))
(use-fixtures :once (fixtures/int-test-fixtures))


(ac/search-for-acls {:system {:access-control-connection {:protocol "http"
                                                          :port 5011
                                                          :context ""
                                                          :host "localhost"}}}
                    {}
                    {:http-options {:accept nil}
                     :raw? true})

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

