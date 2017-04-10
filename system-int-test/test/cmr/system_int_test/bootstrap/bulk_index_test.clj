(ns cmr.system-int-test.bootstrap.bulk-index-test
  "Integration test for CMR bulk indexing."
  (:require
    [clj-time.coerce :as cr]
    [clj-time.core :as t]
    [clojure.java.jdbc :as j]
    [clojure.test :refer :all]
    [cmr.access-control.test.util :as u]
    [cmr.common.date-time-parser :as p]
    [cmr.common.util :as util :refer [are3]]
    [cmr.mock-echo.client.echo-util :as e]
    [cmr.oracle.connection :as oracle]
    [cmr.system-int-test.data2.collection :as dc]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.granule :as dg]
    [cmr.system-int-test.system :as s]
    [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
    [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.metadata-db-util :as mdb]
    [cmr.system-int-test.utils.search-util :as search]
    [cmr.system-int-test.utils.tag-util :as tags]
    [cmr.transmit.access-control :as ac]
    [cmr.transmit.config :as tc]
    [cmr.umm.echo10.echo10-core :as echo10]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       tags/grant-all-tag-fixture]))

(defn- save-collection
  "Saves a collection concept"
  ([n]
   (save-collection n {}))
  ([n attributes]
   (let [unique-str (str "coll" n)
         umm (dc/collection {:short-name unique-str :entry-title unique-str})
         xml (echo10/umm->echo10-xml umm)
         coll (mdb/save-concept (merge
                                 {:concept-type :collection
                                  :format "application/echo10+xml"
                                  :metadata xml
                                  :extra-fields {:short-name unique-str
                                                 :entry-title unique-str
                                                 :entry-id unique-str
                                                 :version-id "v1"}
                                  :revision-date "2000-01-01T10:00:00Z"
                                  :provider-id "PROV1"
                                  :native-id unique-str
                                  :short-name unique-str}
                                 attributes))]
     ;; Make sure the concept was saved successfully
     (is (= 201 (:status coll)))
     (merge umm (select-keys coll [:concept-id :revision-id])))))
  
(defn- save-granule
  "Saves a granule concept"
  ([n collection]
   (save-granule n collection {}))
  ([n collection attributes]
   (let [unique-str (str "gran" n)
         umm (dg/granule collection {:granule-ur unique-str})
         xml (echo10/umm->echo10-xml umm)
         gran (mdb/save-concept (merge
                                 {:concept-type :granule
                                  :provider-id "PROV1"
                                  :native-id unique-str
                                  :format "application/echo10+xml"
                                  :metadata xml
                                  :revision-date "2000-01-01T10:00:00Z"
                                  :extra-fields {:parent-collection-id (:concept-id collection)
                                                 :parent-entry-title (:entry-title collection)
                                                 :granule-ur unique-str}}
                                 attributes))]
     ;; Make sure the concept was saved successfully
     (is (= 201 (:status gran)))
     (merge umm (select-keys gran [:concept-id :revision-id])))))

(defn- save-tag
  "Saves a tag concept"
  ([n]
   (save-tag n {}))
  ([n attributes]
   (let [unique-str (str "tag" n)
         tag (mdb/save-concept (merge
                                {:concept-type :tag
                                 :native-id unique-str
                                 :user-id "user1"
                                 :format "application/edn"
                                 :metadata (str "{:tag-key \"" unique-str "\" :description \"A good tag\" :originator-id \"user1\"}")
                                 :revision-date "2000-01-01T10:00:00Z"}
                                attributes))]
     ;; Make sure the concept was saved successfully
     (is (= 201 (:status tag)))
     (merge tag (select-keys tag [:concept-id :revision-id])))))

(defn- save-acl
  "Saves an acl"
  [n attributes target]
  (let [unique-str (str "acl" n)
        acl (mdb/save-concept (merge
                               {:concept-type :acl
                                :provider-id "CMR"
                                :native-id unique-str
                                :format "application/edn"
                                :metadata (pr-str {:group-permissions [{:user-type "guest"
                                                                        :permissions ["read" "update"]}]
                                                   :system-identity {:target target}})
                                :revision-date "2000-01-01T10:00:00Z"}
                               attributes))]
    ;; Make sure the acl was saved successfully
    (is (= 201 (:status acl)))
    acl))

(defn- save-group
  "Saves a group"
  ([n]
   (save-group n {}))
  ([n attributes]
   (let [unique-str (str "group" n)
         group (mdb/save-concept (merge
                                  {:concept-type :access-group
                                   :provider-id "CMR"
                                   :native-id unique-str
                                   :format "application/edn"
                                   :metadata "{:name \"Administrators\"
                                               :description \"The group of users that manages the CMR.\"
                                               :members [\"user1\" \"user2\"]}"
                                   :revision-date "2000-01-01T10:00:00Z"}
                                  attributes))]
     ;; Make sure the group was saved successfully
     (is (= 201 (:status group)))
     group)))

(defn- delete-concept
  "Creates a tombstone for the concept in metadata-db."
  [concept]
  (let [tombstone (update-in concept [:revision-id] inc)]
    (is (= 201 (:status (mdb/tombstone-concept tombstone))))))

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

(defn- get-last-replicated-revision-date
  "Helper to get the last replicated revision date from the database."
  [db]
  (j/with-db-transaction
   [conn db]
   (->> (j/query conn ["SELECT LAST_REPLICATED_REVISION_DATE FROM REPLICATION_STATUS"])
        first
        :last_replicated_revision_date
        (oracle/oracle-timestamp->str-time conn))))

(deftest index-system-concepts-test
  (s/only-with-real-database
   ;; Disable message publishing so items are not indexed as part of the initial save.
   (dev-sys-util/eval-in-dev-sys `(cmr.metadata-db.config/set-publish-messages! false))
   (let [acl1 (save-acl 1
                        {:extra-fields {:acl-identity "system:token"
                                        :target-provider-id "PROV1"}}
                        "TOKEN")
         acl2 (save-acl 2
                        {:extra-fields {:acl-identity "system:group"
                                        :target-provider-id "PROV1"}}
                        "GROUP")
         acl3 (save-acl 3
                        {:extra-fields {:acl-identity "system:user"
                                        :target-provider-id "PROV1"}}
                        "USER")
         _ (delete-concept acl3)
         group1 (save-group 1 {})
         group2 (save-group 2 {})
         group3 (save-group 3 {})
         _ (delete-concept group2)
         tag1 (save-tag 1 {})
         _ (delete-concept tag1)
         ;; this tag has no originator-id to test a bug fix for a bug in tag processing related to missing originator-ids
         tag2 (save-tag 2 {:metadata "{:tag-key \"tag2\" :description \"A good tag\"}"})
         tag3 (save-tag 3 {})]
     (bootstrap/bulk-index-system-concepts)
     ;; Force elastic data to be flushed, not actually waiting for index requests to finish
     (index/wait-until-indexed)

     ;; ACLs
     (let [response (ac/search-for-acls (u/conn-context) {} {:token (tc/echo-system-token)})
           items (:items response)]
       (search-results-match? items [acl1 acl2]))

     ;; Groups
     (let [response (ac/search-for-groups (u/conn-context) {})
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
    (dev-sys-util/eval-in-dev-sys `(cmr.metadata-db.config/set-publish-messages! true)))))

;; Commented out due to requiring a database link in order to work - this job is only transitional
;; for moving to NGAP so we will remove it once we have fully transitioned.
#_(deftest index-recently-replicated-test
    (s/only-with-real-database
     ;; Disable message publishing so items are not indexed as part of the initial save.
     (dev-sys-util/eval-in-dev-sys `(cmr.metadata-db.config/set-publish-messages! false))
     (let [last-replicated-datetime "3016-01-01T10:00:00Z"
           coll-missed-cutoff (save-collection 1 {:revision-date "3016-01-01T09:59:40Z"})
           coll-within-buffer (save-collection 2 {:revision-date "3016-01-01T09:59:41Z"})
           coll-after-date (save-collection 3 {:revision-date "3016-01-02T00:00:00Z"})
           gran-missed-cutoff (save-granule 1 coll-missed-cutoff {:revision-date "3016-01-01T09:59:40Z"})
           gran-within-buffer (save-granule 2 coll-within-buffer {:revision-date "3016-01-01T09:59:41Z"})
           gran-after-date (save-granule 3 coll-after-date {:revision-date "3016-01-02T00:00:00Z"})
           tag-missed-cutoff (save-tag 1 {:revision-date "3016-01-01T09:59:40Z"})
           tag-within-buffer (save-tag 2 {:revision-date "3016-01-01T09:59:41Z"})
           tag-after-date (save-tag 3 {:revision-date "3016-01-02T00:00:00Z"})
           acl-missed-cutoff (save-acl 1
                                       {:revision-date "3016-01-01T09:59:40Z"
                                        :extra-fields {:acl-identity "system:token"
                                                       :target-provider-id "PROV1"}}
                                       "TOKEN")
           acl-within-buffer (save-acl 2
                                       {:revision-date "3016-01-01T09:59:41Z"
                                        :extra-fields {:acl-identity "system:group"
                                                       :target-provider-id "PROV1"}}
                                       "GROUP")
           acl-after-date (save-acl 3
                                    {:revision-date "3016-01-02T00:00:00Z"
                                     :extra-fields {:acl-identity "system:user"
                                                    :target-provider-id "PROV1"}}
                                    "USER")
           group-missed-cutoff (save-group 1 {:revision-date "3016-01-01T09:59:40Z"})
           group-within-buffer (save-group 2 {:revision-date "3016-01-01T09:59:41Z"})
           group-after-date (save-group 3 {:revision-date "3016-01-02T00:00:00Z"})
           db (get-in (s/context) [:system :bootstrap-db])
           stmt "UPDATE REPLICATION_STATUS SET LAST_REPLICATED_REVISION_DATE = ?"]
       (j/db-do-prepared db stmt [(cr/to-sql-time (p/parse-datetime last-replicated-datetime))])
       (bootstrap/index-recently-replicated)
       ;; Force elastic data to be flushed, not actually waiting for index requests to finish
       (index/wait-until-indexed)
       (testing "Concepts with revision date within 20 seconds of last replicated date are indexed."
         (are3 [concept-type expected]
           (d/refs-match? expected (search/find-refs concept-type {}))

           "Collections"
           :collection [coll-within-buffer coll-after-date]

           "Granules"
           :granule [gran-within-buffer gran-after-date])

         ;; ACLs
         (let [response (ac/search-for-acls (u/conn-context) {} {:token (tc/echo-system-token)})
               items (:items response)]
           (search-results-match? items [acl-within-buffer acl-after-date]))

         ;; Groups
         (let [response (ac/search-for-groups (u/conn-context) {})
               ;; Need to filter out admin group created by fixture
               items (filter #(not (= "mock-admin-group-guid" (:legacy_guid %))) (:items response))]
           (search-results-match? items [group-within-buffer group-after-date]))


         (are3 [expected-tags]
           (let [result-tags (update
                               (tags/search {})
                               :items
                               (fn [items]
                                 (map #(select-keys % [:concept-id :revision-id]) items)))]
             (tags/assert-tag-search expected-tags result-tags))

           "Tags"
           [tag-within-buffer tag-after-date])))

     (testing "When there are no concepts to index the date is not changed."
       (j/db-do-prepared db stmt [(cr/to-sql-time (p/parse-datetime "3016-02-01T10:00:00.000Z"))])
       (bootstrap/index-recently-replicated)
       (is (= "3016-02-01T10:00:00.000Z" (get-last-replicated-revision-date db))))

     (testing "Max revision dates are tracked correctly"
       (testing "for provider concepts"
         (let [coll (save-collection 1 {:revision-date "3016-02-02T10:59:40Z"})]
           (save-granule 1 coll {:revision-date "3016-02-01T11:59:40Z"})
           (save-tag 1 {:revision-date "3016-02-01T18:59:40Z"})
           (bootstrap/index-recently-replicated))
         (is (= "3016-02-02T10:59:40.000Z" (get-last-replicated-revision-date db))))
       (testing "for system concepts"
         (let [coll (save-collection 1 {:revision-date "3016-02-03T10:59:40Z"})]
           (save-granule 1 coll {:revision-date "3016-03-01T11:59:40Z"})
           (save-tag 1 {:revision-date "3016-03-01T12:10:47Z"})
           (bootstrap/index-recently-replicated))
         (is (= "3016-03-01T12:10:47.000Z" (get-last-replicated-revision-date db))))))

   ;; Re-enable message publishing.
   (dev-sys-util/eval-in-dev-sys `(cmr.metadata-db.config/set-publish-messages! true)))


(deftest bulk-index-by-concept-id
  (s/only-with-real-database
    ;; Disable message publishing so items are not indexed.
    (dev-sys-util/eval-in-dev-sys `(cmr.metadata-db.config/set-publish-messages! false))
    (let [;; saved but not indexed
          coll1 (save-collection 1)
          coll2 (save-collection 2 {})
          colls (map :concept-id [coll1 coll2])
          gran1 (save-granule 1 coll1)
          gran2 (save-granule 2 coll2 {})
          tag1 (save-tag 1)
          tag2 (save-tag 2 {})
          acl1 (save-acl 1
                         {:extra-fields {:acl-identity "system:token"
                                         :target-provider-id "PROV1"}}
                         "TOKEN")
          acl2 (save-acl 2
                         {:extra-fields {:acl-identity "system:group"
                                         :target-provider-id "PROV1"}}
                         "GROUP")
          group1 (save-group 1)
          group2 (save-group 2 {})]

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
        

        ;; Commented out until ACLs and groups are supported in the index by concept-id API
        ; ;; ACLs
        ; (let [response (ac/search-for-acls (u/conn-context) {} {:token (tc/echo-system-token)})
        ;       items (:items response)]
        ;   (search-results-match? items [acl2]))

        ; ;; ;; Groups
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
    (dev-sys-util/eval-in-dev-sys `(cmr.metadata-db.config/set-publish-messages! true))))

(deftest bulk-delete-by-concept-id 
  (s/only-with-real-database
    (let [coll1 (save-collection 1)
          coll2 (save-collection 2)
          coll2-id (:concept-id coll2)
          coll3 (save-collection 3)
          gran1 (save-granule 1 coll2)
          gran2 (save-granule 2 coll2)
          gran3 (save-granule 3 coll2)
          gran4 (save-granule 4 coll3)
          gran5 (save-granule 5 coll3)
          tag1 (save-tag 1)
          tag2 (save-tag 2 {})
          acl1 (save-acl 1
                         {:extra-fields {:acl-identity "system:token"
                                         :target-provider-id "PROV1"}}
                         "TOKEN")
          acl2 (save-acl 2
                         {:extra-fields {:acl-identity "system:group"
                                         :target-provider-id "PROV1"}}
                         "GROUP")
          group1 (save-group 1)
          group2 (save-group 2 {})]

      ;; Force coll2 granules into their own index to make sure
      ;; granules outside of 1_small_collections get deleted properly.
      (bootstrap/start-rebalance-collection coll2-id)
      (bootstrap/finalize-rebalance-collection coll2-id)

      (bootstrap/bulk-delete-concepts "PROV1" :collection (map :concept-id [coll1]))
      (bootstrap/bulk-delete-concepts "PROV1" :granule (map :concept-id [gran1 gran3 gran4]))
      (bootstrap/bulk-delete-concepts "PROV1" :tag [(:concept-id tag1)])
      
      ;; Commented out until ACLs and groups are supported in the delete by concept-id API
      ; (bootstrap/bulk-index-concepts "CMR" :access-group [(:concept-id group2)])
      ; (bootstrap/bulk-index-concepts "CMR" :acl [(:concept-id acl2)])

      (index/wait-until-indexed)

      (testing "Concepts are deleted"
        ;; Collections and granules
        (are3 [concept-type expected]
          (d/refs-match? expected (search/find-refs concept-type {}))

          "Collections"
          :collection [coll2 coll3]

          "Granules"
          :granule [gran2 gran5])

        (are3 [expected-tags]
            (let [result-tags (update
                                (tags/search {})
                                :items
                                (fn [items]
                                  (map #(select-keys % [:concept-id :revision-id]) items)))]
              (tags/assert-tag-search expected-tags result-tags))
    
            "Tags"
            [tag2])))))

(deftest bulk-index-after-date-time
  (s/only-with-real-database
    ;; Disable message publishing so items are not indexed.
    (dev-sys-util/eval-in-dev-sys `(cmr.metadata-db.config/set-publish-messages! false))
    (let [;; saved but not indexed
          coll1 (save-collection 1)
          coll2 (save-collection 2 {:revision-date "3016-01-01T10:00:00Z"})
          gran1 (save-granule 1 coll1)
          gran2 (save-granule 2 coll2 {:revision-date "3016-01-01T10:00:00Z"})
          tag1 (save-tag 1)
          tag2 (save-tag 2 {:revision-date "3016-01-01T10:00:00Z"})
          acl1 (save-acl 1
                         {:revision-date "2000-01-01T09:59:40Z"
                          :extra-fields {:acl-identity "system:token"
                                         :target-provider-id "PROV1"}}
                         "TOKEN")
          acl2 (save-acl 2
                         {:revision-date "3016-01-01T09:59:41Z"
                          :extra-fields {:acl-identity "system:group"
                                         :target-provider-id "PROV1"}}
                         "GROUP")
          group1 (save-group 1)
          group2 (save-group 2 {:revision-date "3016-01-01T10:00:00Z"})]

      (bootstrap/bulk-index-after-date-time "2015-01-01T12:00:00Z")
      (index/wait-until-indexed)

      (testing "Only concepts after date are indexed."
        (are3 [concept-type expected]
          (d/refs-match? expected (search/find-refs concept-type {}))

          "Collections"
          :collection [coll2]

          "Granules"
          :granule [gran2])

        ;; ACLs
        (let [response (ac/search-for-acls (u/conn-context) {} {:token (tc/echo-system-token)})
              items (:items response)]
          (search-results-match? items [acl2]))

        ;; Groups
        (let [response (ac/search-for-groups (u/conn-context) {})
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
    (dev-sys-util/eval-in-dev-sys `(cmr.metadata-db.config/set-publish-messages! true))))

;; This test runs bulk index with some concepts in mdb that are good, and some that are
;; deleted, and some that have not yet been deleted, but have an expired deletion date.
(deftest bulk-index-with-some-deleted
  (s/only-with-real-database
   ;; Disable message publishing so items are not indexed as part of the initial save.
   (dev-sys-util/eval-in-dev-sys `(cmr.metadata-db.config/set-publish-messages! false))
   (let [;; coll1 is a regular collection that is ingested
         umm1 (dc/collection {:short-name "coll1" :entry-title "coll1"})
         xml1 (echo10/umm->echo10-xml umm1)
         coll1 (mdb/save-concept {:concept-type :collection
                                  :format "application/echo10+xml"
                                  :metadata xml1
                                  :extra-fields {:short-name "coll1"
                                                 :entry-title "coll1"
                                                 :entry-id "coll1"
                                                 :version-id "v1"}
                                  :provider-id "PROV1"
                                  :native-id "coll1"
                                  :short-name "coll1"})
         umm1 (merge umm1 (select-keys coll1 [:concept-id :revision-id]))
         ;; coll2 is a regualr collection that is ingested and will be deleted later
         coll2 (d/ingest "PROV1" (dc/collection {:short-name "coll2" :entry-title "coll2"}))
         ;; coll3 is a collection with an expired delete time
         umm3 (dc/collection {:short-name "coll3" :entry-title "coll3" :delete-time "2000-01-01T12:00:00Z"})
         xml3 (echo10/umm->echo10-xml umm3)
         coll3 (mdb/save-concept {:concept-type :collection
                                  :format "application/echo10+xml"
                                  :metadata xml3
                                  :extra-fields {:short-name "coll3"
                                                 :entry-title "coll3"
                                                 :entry-id "coll3"
                                                 :version-id "v1"
                                                 :delete-time "2000-01-01T12:00:00Z"}
                                  :provider-id "PROV1"
                                  :native-id "coll3"
                                  :short-name "coll3"})
         ;; gran1 is a regular granule that is ingested
         ummg1 (dg/granule coll1 {:granule-ur "gran1"})
         xmlg1 (echo10/umm->echo10-xml ummg1)
         gran1 (mdb/save-concept {:concept-type :granule
                                  :provider-id "PROV1"
                                  :native-id "gran1"
                                  :format "application/echo10+xml"
                                  :metadata xmlg1
                                  :extra-fields {:parent-collection-id (:concept-id umm1)
                                                 :parent-entry-title "coll1"
                                                 :granule-ur "gran1"}})
         ummg1 (merge ummg1 (select-keys gran1 [:concept-id :revision-id]))
         ;; gran2 is a regular granule that is ingested and will be deleted later
         ummg2 (dg/granule coll1 {:granule-ur "gran2"})
         xmlg2 (echo10/umm->echo10-xml ummg2)
         gran2 (mdb/save-concept {:concept-type :granule
                                  :provider-id "PROV1"
                                  :native-id "gran2"
                                  :format "application/echo10+xml"
                                  :metadata xmlg2
                                  :extra-fields {:parent-collection-id (:concept-id umm1)
                                                 :parent-entry-title "coll1"
                                                 :granule-ur "gran2"}})
         ummg2 (merge ummg2 (select-keys gran2 [:concept-id :revision-id]))
         ;; gran3 is a granule with an expired delete time
         ummg3 (dg/granule coll1 {:granule-ur "gran3" :delete-time "2000-01-01T12:00:00Z"})
         xmlg3 (echo10/umm->echo10-xml ummg3)
         gran3 (mdb/save-concept {:concept-type :granule
                                  :provider-id "PROV1"
                                  :native-id "gran3"
                                  :format "application/echo10+xml"
                                  :metadata xmlg3
                                  :extra-fields {:parent-collection-id (:concept-id umm1)
                                                 :parent-entry-title "coll1"
                                                 :granule-ur "gran3"}})]

     ;; Verify that all of the ingest requests completed successfully
     (doseq [concept [coll1 coll2 coll3 gran1 gran2 gran3]] (is (= 201 (:status concept))))
     ;; bulk index all collections and granules
     (bootstrap/bulk-index-provider "PROV1")
     (index/wait-until-indexed)

     (testing "Expired documents are not indexed during bulk indexing"
       (are [search concept-type expected]
         (d/refs-match? expected (search/find-refs concept-type search))
         {:concept-id (:concept-id coll1)} :collection [umm1]
         {:concept-id (:concept-id coll2)} :collection [coll2]
         {:concept-id (:concept-id coll3)} :collection []
         {:concept-id (:concept-id gran1)} :granule [ummg1]
         {:concept-id (:concept-id gran2)} :granule [ummg2]
         {:concept-id (:concept-id gran3)} :granule []))

     (testing "Deleted documents get deleted during bulk indexing"
       (let [coll2-tombstone {:concept-id (:concept-id coll2)
                              :revision-id (inc (:revision-id coll2))}
             gran2-tombstone {:concept-id (:concept-id gran2)
                              :revision-id (inc (:revision-id gran2))}]
         ;; delete coll2 and gran2 in metadata-db
         (mdb/tombstone-concept coll2-tombstone)
         (mdb/tombstone-concept gran2-tombstone)
         ;; bulk index all collections and granules
         (bootstrap/bulk-index-provider "PROV1")
         (index/wait-until-indexed)
         (are [search concept-type expected]
           (d/refs-match? expected (search/find-refs concept-type search))
           {:concept-id (:concept-id coll1)} :collection [umm1]
           {:concept-id (:concept-id coll2)} :collection []
           {:concept-id (:concept-id coll3)} :collection []
           {:concept-id (:concept-id gran1)} :granule [ummg1]
           {:concept-id (:concept-id gran2)} :granule []
           {:concept-id (:concept-id gran3)} :granule []))))

   ;; Re-enable message publishing.
   (dev-sys-util/eval-in-dev-sys `(cmr.metadata-db.config/set-publish-messages! true))))

;; This test verifies that the bulk indexer can run concurrently with ingest and indexing of items.
;; This test performs the following steps:
;; 1. Saves ten collections in metadata db.
;; 2. Saves three granules for each of those collections in metadata db.
;; 3. Ingests ten granules five times each in a separate thread.
;; 4. Concurrently executes a bulk index operation for the provider.
;; 5. Waits for the bulk indexing and granule ingest to complete.
;; 6. Searches for all of the saved/ingested concepts by concept-id.
;; 7. Verifies that the concepts returned by search have the expected revision ids.

(deftest bulk-index-after-ingest
  (s/only-with-real-database
    (let [collections (for [x (range 1 11)]
                        (let [umm (dc/collection {:short-name (str "short-name" x)
                                                  :entry-title (str "title" x)})
                              xml (echo10/umm->echo10-xml umm)
                              concept-map {:concept-type :collection
                                           :format "application/echo10+xml"
                                           :metadata xml
                                           :extra-fields {:short-name (str "short-name" x)
                                                          :entry-title (str "title" x)
                                                          :entry-id (str "entry-id" x)
                                                          :version-id "v1"}
                                           :provider-id "PROV1"
                                           :native-id (str "coll" x)
                                           :short-name (str "short-name" x)}
                              {:keys [concept-id revision-id]} (mdb/save-concept concept-map)]
                          (assoc umm :concept-id concept-id :revision-id revision-id)))
          granules1 (mapcat (fn [collection]
                              (doall
                                (for [x (range 1 4)]
                                  (let [pid (:concept-id collection)
                                        umm (dg/granule collection)
                                        xml (echo10/umm->echo10-xml umm)
                                        concept-map {:concept-type :granule
                                                     :provider-id "PROV1"
                                                     :native-id (str "gran-" pid "-" x)
                                                     :extra-fields {:parent-collection-id pid
                                                                    :parent-entry-title
                                                                    (:entry-title collection)
                                                                    :granule-ur (str "gran-" pid "-" x)}
                                                     :format "application/echo10+xml"
                                                     :metadata xml}
                                        {:keys [concept-id revision-id]} (mdb/save-concept concept-map)]
                                    (assoc umm :concept-id concept-id :revision-id revision-id)))))
                            collections)
          ;; granules2 and f (the future) are used to ingest ten granules five times each in
          ;; a separate thread to verify that bulk indexing with concurrent ingest does the right
          ;; thing.
          granules2 (let [collection (first collections)
                          pid (:concept-id collection)]
                      (for [x (range 1 11)]
                        (dg/granule collection {:granule-ur (str "gran2-" pid "-" x)})))
          f (future (dotimes [n 5]
                      (doall (map (fn [gran]
                                    (Thread/sleep 100)
                                    (d/ingest "PROV1" gran))
                                  granules2))))]

      (bootstrap/bulk-index-provider "PROV1")
      ;; force our future to complete
      @f
      (index/wait-until-indexed)

      (testing "retrieval after bulk indexing returns the latest revision."
        (doseq [collection collections]
          (let [{:keys [concept-id revision-id]} collection
                response (search/find-refs :collection {:concept-id concept-id})
                es-revision-id (:revision-id (first (:refs response)))]
            ;; the latest revision should be indexed
            (is (= es-revision-id revision-id))))
        (doseq [granule granules1]
          (let [{:keys [concept-id revision-id]} granule
                response (search/find-refs :granule {:concept-id concept-id})
                es-revision-id (:revision-id (first (:refs response)))]
            (is (= es-revision-id revision-id) (str "Failure for granule " concept-id))))
        (doseq [granule (last @f)]
          (let [{:keys [concept-id]} granule
                response (search/find-refs :granule {:concept-id concept-id})
                es-revision-id (:revision-id (first (:refs response)))]
            (is (= 5 es-revision-id) (str "Failure for granule " concept-id))))))))

(deftest invalid-provider-bulk-index-validation-test
  (s/only-with-real-database
    (testing "Validation of a provider supplied in a bulk-index request."
      (let [{:keys [status errors]} (bootstrap/bulk-index-provider "NCD4580")]
        (is (= [400 ["Provider: [NCD4580] does not exist in the system"]]
               [status errors]))))))

(deftest collection-bulk-index-validation-test
  (s/only-with-real-database
    (let [umm1 (dc/collection {:short-name "coll1" :entry-title "coll1"})
          xml1 (echo10/umm->echo10-xml umm1)
          coll1 (mdb/save-concept {:concept-type :collection
                                   :format "application/echo10+xml"
                                   :metadata xml1
                                   :extra-fields {:short-name "coll1"
                                                  :entry-title "coll1"
                                                  :entry-id "coll1"
                                                  :version-id "v1"}
                                   :provider-id "PROV1"
                                   :native-id "coll1"
                                   :short-name "coll1"})
          umm1 (merge umm1 (select-keys coll1 [:concept-id :revision-id]))
          ummg1 (dg/granule coll1 {:granule-ur "gran1"})
          xmlg1 (echo10/umm->echo10-xml ummg1)
          gran1 (mdb/save-concept {:concept-type :granule
                                   :provider-id "PROV1"
                                   :native-id "gran1"
                                   :format "application/echo10+xml"
                                   :metadata xmlg1
                                   :extra-fields {:parent-collection-id (:concept-id umm1)
                                                  :parent-entry-title "coll1"}})
          valid-prov-id "PROV1"
          valid-coll-id (:concept-id umm1)
          invalid-prov-id "NCD4580"
          invalid-coll-id "C12-PROV1"
          err-msg1 (format "Provider: [%s] does not exist in the system" invalid-prov-id)
          err-msg2 (format "Collection [%s] does not exist." invalid-coll-id)
          {:keys [status errors] :as succ-stat} (bootstrap/bulk-index-collection
                                                  valid-prov-id valid-coll-id)
          ;; invalid provider and collection
          {:keys [status errors] :as fail-stat1} (bootstrap/bulk-index-collection
                                                   invalid-prov-id invalid-coll-id)
          ;; valid provider and invalid collection
          {:keys [status errors] :as fail-stat2} (bootstrap/bulk-index-collection
                                                   valid-prov-id invalid-coll-id)
          ;; invalid provider and valid collection
          {:keys [status errors] :as fail-stat3} (bootstrap/bulk-index-collection
                                                   invalid-prov-id valid-coll-id)]

      (testing "Validation of a collection supplied in a bulk-index request."
        (are [expected actual] (= expected actual)
             [202 nil] [(:status succ-stat) (:errors succ-stat)]
             [400 [err-msg1]] [(:status fail-stat1) (:errors fail-stat1)]
             [400 [err-msg2]] [(:status fail-stat2) (:errors fail-stat2)]
             [400 [err-msg1]] [(:status fail-stat3) (:errors fail-stat3)])))))

;; This test is to verify that bulk index works with tombstoned tag associations
(deftest bulk-index-collections-with-tag-association-test
  (s/only-with-real-database
    (let [[coll1 coll2] (for [n (range 1 3)]
                          (d/ingest "PROV1" (dc/collection {:entry-title (str "coll" n)})))
          ;; Wait until collections are indexed so tags can be associated with them
          _ (index/wait-until-indexed)
          user1-token (e/login (s/context) "user1")
          tag1-colls [coll1 coll2]
          tag-key "tag1"
          tag1 (tags/save-tag
                 user1-token
                 (tags/make-tag {:tag-key tag-key})
                 tag1-colls)]

      (index/wait-until-indexed)
      ;; disassociate tag1 from coll2 and not send indexing events
      (dev-sys-util/eval-in-dev-sys
        `(cmr.metadata-db.config/set-publish-messages! false))
      (tags/disassociate-by-query user1-token tag-key {:concept_id (:concept-id coll2)})
      (dev-sys-util/eval-in-dev-sys
        `(cmr.metadata-db.config/set-publish-messages! true))

      (bootstrap/bulk-index-provider "PROV1")
      (index/wait-until-indexed)

      (testing "All tag parameters with XML references"
        (is (d/refs-match? [coll1]
                           (search/find-refs :collection {:tag-key "tag1"})))))))
