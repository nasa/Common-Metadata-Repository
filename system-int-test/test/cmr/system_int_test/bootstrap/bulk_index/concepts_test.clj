(ns cmr.system-int-test.bootstrap.bulk-index.concepts-test
  "Integration test for CMR bulk indexing."
  (:require
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as u]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.bootstrap.bulk-index.core :as core]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.tag-util :as tags]
   [cmr.transmit.access-control :as ac]
   [cmr.transmit.config :as tc]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn auto-index-fixture
  "Disable automatic indexing during tests."
  [f]
  (core/disable-automatic-indexing)
  (f)
  (core/reenable-automatic-indexing))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture {"provguid1" "PROV1"
                                             "provguid2" "PROV2"}
                                            {:grant-all-ingest? true
                                             :grant-all-search? true
                                             :grant-all-access-control? false})
                      tags/grant-all-tag-fixture
                      auto-index-fixture]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- normalize-search-result-item
  "Returns a map with just concept-id and revision-id for the given item."
  [result-item]
  (-> result-item
      util/map-keys->kebab-case
      (select-keys [:concept-id :revision-id])))

(defn- search-results-match?
  "Compare search results to expected results."
  [results expected]
  (let [search-items (set (map normalize-search-result-item results))
        exp-items (set (map #(dissoc % :status) expected))]
    (is (= exp-items search-items))))

(defn- create-read-update-token
  "Create a token with read/update permission."
  []
  (let [admin-read-update-group-concept-id (e/get-or-create-group (s/context) "admin-read-update-group")]
    (e/grant-group-admin (s/context) admin-read-update-group-concept-id :read :update)
    ;; Create and return token
    (e/login (s/context) "admin-read-update" [admin-read-update-group-concept-id])))

(defn- verify-collections-granules-are-indexed
  "Verify the given collections and granules are indexed through search"
  ([collections granules]
   (verify-collections-granules-are-indexed collections granules nil))
  ([collections granules search-token]
   (are3 [concept-type expected]
         (d/refs-match? expected (search/find-refs concept-type {:token search-token}))

         "Collections"
         :collection collections

         "Granules"
         :granule granules)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest ^:oracle index-system-concepts-test-with-update-token
  (s/only-with-real-database
   (let [read-update-token (create-read-update-token)
         {:keys [status errors]} (bootstrap/bulk-index-system-concepts {tc/token-header read-update-token})]
     (is (= [202 nil]
            [status errors])))))

(deftest ^:oracle index-system-concepts-test
  (s/only-with-real-database
   ;; Remove fixture ACLs
   (let [response (ac/search-for-acls (u/conn-context) {} {:token (tc/echo-system-token)})
         items (:items response)]
     (doseq [acl items]
       (e/ungrant (s/context) (:concept_id acl))))

   (let [acl1 (core/save-acl 1
                             {:extra-fields {:acl-identity "system:token"
                                             :target-provider-id "PROV1"}}
                             "TOKEN")
         acl2 (core/save-acl 2
                             {:extra-fields {:acl-identity "system:group"
                                             :target-provider-id "PROV1"}}
                             "GROUP")
         acl3 (core/save-acl 3
                             {:extra-fields {:acl-identity "system:user"
                                             :target-provider-id "PROV1"}}
                             "USER")
         _ (core/delete-concept acl3)
         group1 (core/save-group 1 {})
         group2 (core/save-group 2 {})
         group3 (core/save-group 3 {})
         _ (core/delete-concept group2)
         tag1 (core/save-tag 1 {})
         _ (core/delete-concept tag1)
         ;; this tag has no originator-id to test a bug fix for a bug in tag processing related to missing originator-ids
         tag2 (core/save-tag 2 {:metadata "{:tag-key \"tag2\" :description \"A good tag\"}"})
         tag3 (core/save-tag 3 {})
         {:keys [status errors]} (bootstrap/bulk-index-system-concepts nil)]

     (is (= [401 ["You do not have permission to perform that action."]]
            [status errors]))

     (bootstrap/bulk-index-system-concepts)
     ;; Force elastic data to be flushed, not actually waiting for index requests to finish
     (index/wait-until-indexed)

     ;; ACLs
     (let [response (ac/search-for-acls (u/conn-context) {} {:token (tc/echo-system-token)})
           items (:items response)]
       (search-results-match? items [acl1 acl2]))

     ;; Groups
     (let [response (ac/search-for-groups (u/conn-context) {:token (tc/echo-system-token)})
           ;; Need to filter out admin group created by fixture
           items (filter #(not (= "mock-admin-group-guid" (:legacy_guid %))) (:items response))]
       (search-results-match? items [group1 group3]))

     (are3 [expected-tags]
           (let [result-tags (update
                              (tags/search {})
                              :items
                              (fn [items]
                                (map #(select-keys % [:concept-id :revision-id]) items)))]
             (tags/assert-tag-search expected-tags result-tags))

           "Tags"
           [tag2 tag3]))))

(deftest ^:oracle bulk-index-by-concept-id
  (s/only-with-real-database
   (let [;; saved but not indexed
         coll1 (core/save-collection "PROV1" 1)
         coll2 (core/save-collection "PROV1" 2 {})
         colls (map :concept-id [coll1 coll2])
         gran1 (core/save-granule "PROV1" 1 coll1)
         gran2 (core/save-granule "PROV1" 2 coll2 {})
         tag1 (core/save-tag 1)
         tag2 (core/save-tag 2 {})
         acl1 (core/save-acl 1
                             {:extra-fields {:acl-identity "system:token"
                                             :target-provider-id "PROV1"}}
                             "TOKEN")
         acl2 (core/save-acl 2
                             {:extra-fields {:acl-identity "system:group"
                                             :target-provider-id "PROV1"}}
                             "GROUP")
         group1 (core/save-group 1)
         group2 (core/save-group 2 {})
         {:keys [status errors]} (bootstrap/bulk-index-concepts "PROV1" :collection colls nil)]

     (is (= [401 ["You do not have permission to perform that action."]]
            [status errors]))

     (bootstrap/bulk-index-concepts "PROV1" :collection colls)
     (bootstrap/bulk-index-concepts "PROV1" :granule [(:concept-id gran2)])
     (bootstrap/bulk-index-concepts "PROV1" :tag [(:concept-id tag1)])

     (index/wait-until-indexed)

     (testing "Concepts are indexed."
       (verify-collections-granules-are-indexed [coll1 coll2] [gran2])
       (are3 [expected-tags]
             (let [result-tags (update
                                (tags/search {})
                                :items
                                (fn [items]
                                  (map #(select-keys % [:concept-id :revision-id]) items)))]
               (tags/assert-tag-search expected-tags result-tags))

             "Tags"
             [tag1])))))

(deftest ^{:kaocha/skip true
           :oracle true} zzz_bulk-index-after-date-time
  ;; prefixed with zzz_ to ensure it runs last. There is a side-effect
  ;; where if this runs before bulk-index-all-providers, concepts are
  ;; not indexed correctly.
  (s/only-with-real-database
   ;; Remove fixture ACLs
   (let [response (ac/search-for-acls (u/conn-context) {} {:token (tc/echo-system-token)})
         items (:items response)]
     (doseq [acl items]
       (e/ungrant (s/context) (:concept_id acl))))

   (let [;; saved but not indexed
         coll1 (core/save-collection "PROV1" 1)
         coll2 (core/save-collection "PROV1" 2 {:revision-date "3016-01-01T10:00:00Z"})
         gran1 (core/save-granule "PROV1" 1 coll1)
         gran2 (core/save-granule "PROV1" 2 coll2 {:revision-date "3016-01-01T10:00:00Z"})
         tag1 (core/save-tag 1)
         tag2 (core/save-tag 2 {:revision-date "3016-01-01T10:00:00Z"})
         acl1 (core/save-acl 1
                             {:revision-date "2000-01-01T09:59:40Z"
                              :extra-fields {:acl-identity "system:token"
                                             :target-provider-id "PROV1"}}
                             "TOKEN")
         acl2 (core/save-acl 2
                             {:revision-date "3016-01-01T09:59:41Z"
                              :extra-fields {:acl-identity "system:group"
                                             :target-provider-id "PROV1"}}
                             "GROUP")
         group1 (core/save-group 1)
         group2 (core/save-group 2 {:revision-date "3016-01-01T10:00:00Z"})
         {:keys [status errors]} (bootstrap/bulk-index-after-date-time "2015-01-01T12:00:00Z" nil)]

     (is (= [401 ["You do not have permission to perform that action."]]
            [status errors]))

     (bootstrap/bulk-index-after-date-time "2015-01-01T12:00:00Z")
     (index/wait-until-indexed)

     (testing "Only concepts after date are indexed."
       (verify-collections-granules-are-indexed [coll2] [gran2] (tc/echo-system-token))

       ;; ACLs
       (let [response (ac/search-for-acls (u/conn-context) {} {:token (tc/echo-system-token)})
             items (:items response)]
         (search-results-match? items [acl2]))

       ;; Groups
       (let [response (ac/search-for-groups (u/conn-context) {:token (tc/echo-system-token)})
             ;; Need to filter out admin group created by fixture
             items (filter #(not (= "mock-admin-group-guid" (:legacy_guid %))) (:items response))]
         (search-results-match? items [group2]))

       (are3 [expected-tags]
             (let [result-tags (update
                                (tags/search {})
                                :items
                                (fn [items]
                                  (map #(select-keys % [:concept-id :revision-id]) items)))]
               (tags/assert-tag-search expected-tags result-tags))

             "Tags"
             [tag2])))))

(deftest ^:oracle bulk-index-all-providers
  (s/only-with-real-database
   (let [;; saved but not indexed
         coll1 (core/save-collection "PROV1" 1)
         coll2 (core/save-collection "PROV2" 2)

         gran1 (core/save-granule "PROV1" 1 coll1)
         gran2 (core/save-granule "PROV1" 2 coll1)
         gran3 (core/save-granule "PROV2" 2 coll2)]

     (testing "indexing all providers without permission"
       (let [{:keys [status errors]} (bootstrap/bulk-index-all-providers)]
         (is (= [401 ["You do not have permission to perform that action."]]
                [status errors]))))

     ;; sanity check that no collections or granules are indexed
     (verify-collections-granules-are-indexed [] [])

     ;; bulk index all providers with permission
     (let [{:keys [status message]} (bootstrap/bulk-index-all-providers
                                     {tc/token-header (tc/echo-system-token)})]
       (is (= [202 "Processing bulk indexing of all providers."]
              [status message])))
     (index/wait-until-indexed)

     (testing "all collections and granules are indexed."
       (verify-collections-granules-are-indexed [coll1 coll2] [gran1 gran2 gran3])))))
