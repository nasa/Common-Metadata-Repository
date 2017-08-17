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

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture {"provguid1" "PROV1"}
                                            {:grant-all-ingest? true
                                             :grant-all-search? true
                                             :grant-all-access-control? false})
                     tags/grant-all-tag-fixture]))

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

(deftest index-system-concepts-test
  (s/only-with-real-database
   ;; Disable message publishing so items are not indexed as part of the initial save.
   (core/disable-automatic-indexing)

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
         tag3 (core/save-tag 3 {})]
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
       [tag2 tag3])
    ;; Re-enable message publishing.
    (core/reenable-automatic-indexing))))

(deftest bulk-index-by-concept-id
  (s/only-with-real-database
    ;; Disable message publishing so items are not indexed.
    (core/disable-automatic-indexing)
    (let [;; saved but not indexed
          coll1 (core/save-collection 1)
          coll2 (core/save-collection 2 {})
          colls (map :concept-id [coll1 coll2])
          gran1 (core/save-granule 1 coll1)
          gran2 (core/save-granule 2 coll2 {})
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
          group2 (core/save-group 2 {})]

      (bootstrap/bulk-index-concepts "PROV1" :collection colls)
      (bootstrap/bulk-index-concepts "PROV1" :granule [(:concept-id gran2)])
      (bootstrap/bulk-index-concepts "PROV1" :tag [(:concept-id tag1)])
      ;; Commented out until ACLs and groups are supported in the index by concept-id API
      ; (bootstrap/bulk-index-concepts "CMR" :access-group [(:concept-id group2)])
      ; (bootstrap/bulk-index-concepts "CMR" :acl [(:concept-id acl2)])

      (index/wait-until-indexed)

      (testing "Concepts are indexed."
        ;; Collections and granules
        (are3 [concept-type expected]
          (d/refs-match? expected (search/find-refs concept-type {}))

          "Collections"
          :collection [coll1 coll2]

          "Granules"
          :granule [gran2])

        ; ;; ACLs
        ; (let [response (ac/search-for-acls (u/conn-context) {} {:token (tc/echo-system-token)})
        ;       items (:items response)]
        ;   (search-results-match? items [acl2]))

        ;; Groups
        ; (let [response (ac/search-for-groups (u/conn-context) {})
        ;       ;; Need to filter out admin group created by fixture
        ;       items (filter #(not (= "mock-admin-group-guid" (:legacy_guid %))) (:items response))]
        ;   (search-results-match? items [group2]))

        (are3 [expected-tags]
          (let [result-tags (update
                              (tags/search {})
                              :items
                              (fn [items]
                                (map #(select-keys % [:concept-id :revision-id]) items)))]
            (tags/assert-tag-search expected-tags result-tags))

          "Tags"
          [tag1])))

    ;; Re-enable message publishing.
    (core/reenable-automatic-indexing)))

(deftest bulk-index-after-date-time
  (s/only-with-real-database
    ;; Disable message publishing so items are not indexed.
    (core/disable-automatic-indexing)

    ;; Remove fixture ACLs
    (let [response (ac/search-for-acls (u/conn-context) {} {:token (tc/echo-system-token)})
          items (:items response)]
      (doseq [acl items]
        (e/ungrant (s/context) (:concept_id acl))))

    (let [;; saved but not indexed
          coll1 (core/save-collection 1)
          coll2 (core/save-collection 2 {:revision-date "3016-01-01T10:00:00Z"})
          gran1 (core/save-granule 1 coll1)
          gran2 (core/save-granule 2 coll2 {:revision-date "3016-01-01T10:00:00Z"})
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
          group2 (core/save-group 2 {:revision-date "3016-01-01T10:00:00Z"})]

      (bootstrap/bulk-index-after-date-time "2015-01-01T12:00:00Z")
      (index/wait-until-indexed)

      (testing "Only concepts after date are indexed."
        (are3 [concept-type expected]
          (d/refs-match? expected (search/find-refs concept-type {:token (tc/echo-system-token)}))

          "Collections"
          :collection [coll2]

          "Granules"
          :granule [gran2])

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
          [tag2])))
    ;; Re-enable message publishing.
    (core/reenable-automatic-indexing)))




