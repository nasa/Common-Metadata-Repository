(ns cmr.system-int-test.bootstrap.db-synchronization-test
  "This tests putting the Catalog REST and Metadata DB in an inconsistent state and then using
  the bootstrap application to make them consistent again."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.umm.core :as umm]
            [cmr.system-int-test.data2.core :as d]
            [cmr.common.test.test-environment :as test-env]
            [cmr.bootstrap.test.catalog-rest :as cat-rest]
            [cmr.common.concepts :as concepts]
            [cmr.oracle.connection :as oracle]
            [cmr.common.mime-types :as mime-types]))


(use-fixtures :each (bootstrap/db-fixture "CPROV1" "CPROV2"))

(comment
  (do
    (bootstrap/db-fixture-setup "CPROV1" "CPROV2")

    (def concept-counter (atom 0))
    (def coll1-1 (coll-concept concept-counter "CPROV1" "coll1"))
    (def coll1-2 (updated-concept coll1-1))
    (def coll2-1 (coll-concept concept-counter "CPROV1" "coll2"))

    (cat-rest/insert-concept (bootstrap/system) coll1-1)
    (bootstrap/bulk-migrate-provider "CPROV1")
    (assert-concepts-in-mdb [coll1-1])

    (cat-rest/update-concept (bootstrap/system) coll1-2)
    (cat-rest/insert-concept (bootstrap/system) coll2-1)
    (cat-rest/delete-concept (bootstrap/system) coll1-1)
    )


  (bootstrap/synchronize-databases)

  (assert-concepts-in-mdb [coll1-2 (assoc coll2-1 :revision-id 2)])

  (bootstrap/db-fixture-tear-down "CPROV1"  "CPROV2")

  )

(defn next-concept-id
  "Returns the next concept id to use from the counter, concept type, and provider id."
  [concept-counter concept-type provider-id]
  (concepts/build-concept-id {:concept-type concept-type
                              :sequence-number (swap! concept-counter inc)
                              :provider-id provider-id}))

(defn coll-concept
  "Creates a new collection concept."
  ([concept-counter provider-id entry-title]
   (coll-concept concept-counter provider-id entry-title :echo10))
  ([concept-counter provider-id entry-title xml-format]
   (let [coll (dc/collection {:entry-title entry-title
                              ;; The summary will contain the revision number so subsequent revisions
                              ;; will have slightly different metadata
                              :summary "rev1"})]
     {:concept-type :collection
      :format (mime-types/format->mime-type xml-format)
      :metadata (umm/umm->xml coll xml-format)
      :concept-id (next-concept-id concept-counter :collection provider-id)
      :revision-id 1
      :deleted false
      :extra-fields {:short-name (get-in coll [:product :short-name])
                     :entry-id (or (:entry-id coll) (get-in coll [:product :short-name]))
                     :entry-title entry-title
                     :version-id (get-in coll [:product :version-id])
                     :delete-time nil}
      :provider-id provider-id
      :native-id entry-title})))

(defn coll-concept-with-delete-date-in-the-past
  "Creates a new collection concept with a delete date in the past"
  [concept-counter provider-id entry-title]
  (let [coll (dc/collection {:entry-title entry-title
                             ;; The summary will contain the revision number so subsequent revisions
                             ;; will have slightly different metadata
                             :summary "rev1"
                             :delete-time "2000-01-01T12:00:00Z"})]
    {:concept-type :collection
     :format (mime-types/format->mime-type :echo10)
     :metadata (umm/umm->xml coll :echo10)
     :concept-id (next-concept-id concept-counter :collection provider-id)
     :revision-id 1
     :deleted false
     :extra-fields {:short-name (get-in coll [:product :short-name])
                    :entry-id (or (:entry-id coll) (get-in coll [:product :short-name]))
                    :entry-title entry-title
                    :version-id (get-in coll [:product :version-id])
                    :delete-time "2000-01-01T12:00:00Z"}
     :provider-id provider-id
     :native-id entry-title}))

(defn gran-concept
  "Creates a new granule concept."
  [concept-counter collection-concept granule-ur]
  (let [provider-id (:provider-id collection-concept)
        coll (umm/parse-concept collection-concept)
        gran (dg/granule coll {:granule-ur granule-ur
                               ;; The producer granule id  will contain the revision number so
                               ;; subsequent revisions will have slightly different metadata.
                               :producer-gran-id "rev1"})]
    {:concept-type :granule
     :format "application/echo10+xml"
     :metadata (umm/umm->xml gran :echo10)
     :concept-id (next-concept-id concept-counter :granule provider-id)
     :revision-id 1
     :deleted false
     :extra-fields {:parent-collection-id (:concept-id collection-concept)
                    :delete-time nil}
     :provider-id provider-id
     :native-id granule-ur}))

(defn gran-concept-with-delete-date-in-the-past
  "Creates a new granule concept with a delete time in the past"
  [concept-counter collection-concept granule-ur]
  (let [provider-id (:provider-id collection-concept)
        coll (umm/parse-concept collection-concept)
        gran (dg/granule coll {:granule-ur granule-ur
                               ;; The producer granule id  will contain the revision number so
                               ;; subsequent revisions will have slightly different metadata.
                               :producer-gran-id "rev1"
                               :delete-time "2000-01-01T12:00:00Z"})]
    {:concept-type :granule
     :format "application/echo10+xml"
     :metadata (umm/umm->xml gran :echo10)
     :concept-id (next-concept-id concept-counter :granule provider-id)
     :revision-id 1
     :deleted false
     :extra-fields {:parent-collection-id (:concept-id collection-concept)
                    :delete-time "2000-01-01T12:00:00Z"}
     :provider-id provider-id
     :native-id granule-ur}))

(defmulti update-concept-metadata
  "Makes a superficial change to the concept for the new revision id."
  (fn [concept new-revision-id]
    (:concept-type concept)))

(defmethod update-concept-metadata :collection
  [concept new-revision-id]
  (-> concept
      umm/parse-concept
      (assoc :summary (str "rev" new-revision-id))
      (umm/umm->xml (mime-types/base-mime-type-to-format (:format concept)))))

(defmethod update-concept-metadata :granule
  [concept new-revision-id]
  (-> concept
      umm/parse-concept
      (assoc-in [:data-granule :producer-gran-id] (str "rev" new-revision-id))
      (umm/umm->xml (mime-types/base-mime-type-to-format (:format concept)))))

(defn updated-concept
  "Makes a superficial change to the concept and increments its revision id."
  [concept]
  (let [new-revision-id (inc (:revision-id concept))]
    (assoc concept
           :revision-id new-revision-id
           :metadata (update-concept-metadata concept new-revision-id))))

(defn deleted-concept
  "Returns the deleted version of this concept"
  [concept]
  (-> concept
      (update-in [:revision-id] inc)
      (assoc :metadata nil
             :deleted true)))

(defn assert-concepts-in-mdb
  "Checks that all of the concepts with the indicated revisions are in metadata db."
  [concepts]
  (doseq [concept concepts]
    (is (= concept
           (-> (ingest/get-concept (:concept-id concept) (:revision-id concept))
               (dissoc :revision-date))))))

(defn assert-concepts-not-in-mdb
  "Checks that all of the concepts with the indicated revisions are not in metadata db."
  [concepts]
  (doseq [concept concepts]
    (is (nil? (ingest/get-concept (:concept-id concept) (:revision-id concept))))))

(defn assert-tombstones-in-mdb
  "Checks that tombstones for each of the concepts are the latest revisions in the metadata db"
  [concepts]
  (doseq [concept concepts]
    (is (:deleted (ingest/get-concept (:concept-id concept))))))

(defn assert-concepts-indexed
  "Check that all of the concepts are indexed for searching with the indicated revisions."
  [concepts]
  (index/refresh-elastic-index)
  (doseq [[concept-type type-concepts] (group-by :concept-type concepts)
          concept-set (partition 1000 type-concepts)]
    (let [expected-tuples (map #(vector (:concept-id %) (:revision-id %)) concept-set)
          results (search/find-refs-with-post concept-type {:concept-id (map :concept-id concept-set)
                                                            :page-size 2000})
          found-tuples (map #(vector (:id %) (:revision-id %)) (:refs results))]
      (is (= (set expected-tuples) (set found-tuples))))))

(defn assert-concepts-not-indexed
  "Checks that all the concepts given are not in the search index."
  [concepts]
  (index/refresh-elastic-index)
  (doseq [[concept-type type-concepts] (group-by :concept-type concepts)
          concept-set (partition 1000 type-concepts)]
    (let [expected-tuples (map #(vector (:concept-id %) (:revision-id %)) type-concepts)
          results (search/find-refs-with-post concept-type {:concept-id (map :concept-id type-concepts)})]
      (is (= 0 (:hits results)) (str "Expected 0 found " (pr-str results))))))

(deftest db-synchronize-collection-updates-test
  (test-env/only-with-real-database
    (let [concept-counter (atom 1)
          coll1-1 (coll-concept concept-counter "CPROV1" "coll1" :iso-smap)
          coll1-2 (updated-concept coll1-1)
          coll2-1 (coll-concept concept-counter "CPROV1" "coll2")
          coll2-2 (updated-concept coll2-1)
          coll3-1 (coll-concept concept-counter "CPROV2" "coll3")
          coll3-2 (updated-concept coll3-1)
          ;; Collection 4 will not be updated but it will still get a newer revision id
          coll4-1 (coll-concept concept-counter "CPROV2" "coll4")
          coll4-2 (assoc coll4-1 :revision-id 2)

          ;; Collection 5 will be a new one
          coll5-1 (coll-concept concept-counter "CPROV2" "coll5")

          ;; Collection 6 will have a delete date in the past
          coll6-1 (coll-concept-with-delete-date-in-the-past concept-counter "CPROV2" "coll6")

          orig-colls [coll1-1 coll2-1 coll3-1 coll4-1]
          updated-colls [coll1-2 coll2-2 coll3-2 coll4-2 coll5-1]
          system (bootstrap/system)]

      ;; Save the concepts in Catalog REST
      (cat-rest/insert-concepts system orig-colls)

      ;; Migrate the providers. Catalog REST and Metadata DB are in sync
      (bootstrap/bulk-migrate-providers "CPROV1" "CPROV2")
      (bootstrap/bulk-index-providers "CPROV1" "CPROV2")

      (assert-concepts-in-mdb orig-colls)
      (assert-concepts-indexed orig-colls)

      ;; Update the concepts in catalog rest.
      (cat-rest/update-concepts system [coll1-2 coll2-2 coll3-2])
      ;; Collection 5 is inserted for the first time so it's not yet in Metadata DB
      (cat-rest/insert-concept system coll5-1)
      (cat-rest/insert-concept system coll6-1)


      ;; Catalog REST and Metadata DB are not in sync now.
      ;; Put them back in sync
      (bootstrap/synchronize-databases {:sync-types [:updates]})

      ;; Check that they are synchronized now with the latest data.
      (assert-concepts-in-mdb updated-colls)
      (assert-concepts-indexed updated-colls)

      ;; Collection 6 should be skipped since it has a delete date in the past
      (assert-concepts-not-in-mdb [coll6-1])
      (assert-concepts-not-indexed [coll6-1]))))

(deftest db-synchronize-collection-updates-between-dates-test
  (test-env/only-with-real-database
    (let [concept-counter (atom 1)
          ;; Original in sync concepts
          coll1-1 (coll-concept concept-counter "CPROV1" "coll1")
          coll2-1 (coll-concept concept-counter "CPROV1" "coll2")
          coll3-1 (coll-concept concept-counter "CPROV1" "coll3")
          orig-colls [coll1-1 coll2-1 coll3-1]

          ;; Changes before start time
          coll2-2 (updated-concept coll2-1)
          coll4-1 (coll-concept concept-counter "CPROV1" "coll4")

          ;; Changes between start and end time
          coll3-2 (updated-concept coll3-1)
          coll5-1 (coll-concept concept-counter "CPROV1" "coll5")
          coll6-1 (coll-concept concept-counter "CPROV1" "coll6")

          ;; Changes after end time
          coll7-1 (coll-concept concept-counter "CPROV1" "coll7")
          system (bootstrap/system)]
      ;; Save the concepts in Catalog REST
      (cat-rest/insert-concepts system orig-colls)

      ;; Migrate the providers. Catalog REST and Metadata DB are in sync
      (bootstrap/bulk-migrate-providers "CPROV1")
      (bootstrap/bulk-index-providers "CPROV1")

      ;; Make changes before the start time
      (cat-rest/update-concepts system [coll2-2])
      (cat-rest/insert-concepts system [coll4-1])
      (let [start-time (oracle/current-db-time (:db system))
            ;; Make changes within the captured times
            _ (cat-rest/update-concepts system [coll3-2])
            _ (cat-rest/insert-concepts system [coll5-1 coll6-1])
            end-time (oracle/current-db-time (:db system))]

        ;; Make changes after end time that will be ignored
        (cat-rest/insert-concepts system [coll7-1])

        ;; Catalog REST and Metadata DB are not in sync now.
        ;; Put them back in sync
        (bootstrap/synchronize-databases {:sync-types [:updates] :start-time start-time :end-time end-time}))


      ;; Check that they are synchronized now with the latest data.
      (assert-concepts-in-mdb [coll1-1 coll2-1 coll3-1 coll3-2 coll5-1 coll6-1])
      (assert-concepts-indexed [coll1-1 coll2-1 coll3-2 coll5-1 coll6-1])

      ;; Check that concepts before start and after end were not found
      (assert-concepts-not-in-mdb [coll2-2 coll4-1 coll7-1])
      (assert-concepts-not-indexed [coll4-1 coll7-1]))))

(deftest db-synchronize-collection-missing-items-test
  (test-env/only-with-real-database
    (let [concept-counter (atom 1)
          ;; Coll1 will exist in both
          coll1-1 (coll-concept concept-counter "CPROV1" "coll1" :iso-smap)
          ;; Coll 1 has been updated but this particular synchronization will have missed it
          coll1-2 (updated-concept coll1-1)
          ;;Collections 2 - 4 will be missing
          coll2-1 (coll-concept concept-counter "CPROV1" "coll2")
          coll3-1 (coll-concept concept-counter "CPROV2" "coll3")
          coll4-1 (coll-concept concept-counter "CPROV2" "coll4")

          ;; Collection 5 will have a tombstone
          coll5-1 (coll-concept concept-counter "CPROV2" "coll5")
          ;; The updated version will be revision 3 (tombstone is 2)
          coll5-3 (updated-concept (updated-concept coll5-1))

          orig-colls [coll1-1 coll5-1]
          updated-colls [coll1-1 coll2-1 coll3-1 coll4-1 coll5-3]
          system (bootstrap/system)]

      ;; Save the concepts in Catalog REST
      (cat-rest/insert-concepts system orig-colls)

      ;; Migrate the providers. Catalog REST and Metadata DB are in sync
      (bootstrap/bulk-migrate-providers "CPROV1" "CPROV2")
      (bootstrap/bulk-index-providers "CPROV1" "CPROV2")

      (assert-concepts-in-mdb orig-colls)
      (assert-concepts-indexed orig-colls)

      ;; Create a tombstone for concept 5
      (ingest/delete-concept coll5-1)

      ;; Insert/Update the concepts in catalog rest.
      (cat-rest/update-concepts system [coll1-2 coll5-3])
      (cat-rest/insert-concepts system [coll2-1 coll3-1 coll4-1])

      ;; Catalog REST and Metadata DB are not in sync now.
      ;; Put them back in sync
      (bootstrap/synchronize-databases {:sync-types [:missing]})

      ;; Check that they are synchronized now with the latest data.
      (assert-concepts-in-mdb updated-colls)
      (assert-concepts-indexed updated-colls))))

(deftest db-synchronize-collection-missing-with-specific-collection-test
  (test-env/only-with-real-database
    (let [concept-counter (atom 1)
          coll1-1 (coll-concept concept-counter "CPROV1" "coll1")
          ;;Collections 2 - 4 will be missing
          coll2-1 (coll-concept concept-counter "CPROV1" "coll2")
          system (bootstrap/system)]

      ;; Save the concepts in Catalog REST
      (cat-rest/insert-concepts system [coll1-1 coll2-1])

      ;; Catalog REST and Metadata DB are not in sync now.
      ;; Put them back in sync
      (bootstrap/synchronize-databases {:sync-types [:missing] :entry-title "coll2"
                                        :provider-id "CPROV1"})

      ;; Check that they are synchronized now with the latest data.
      (assert-concepts-in-mdb [coll2-1])
      (assert-concepts-indexed [coll2-1])
      ;; Only collection 2 should by synchronized
      (assert-concepts-not-in-mdb [coll1-1])
      (assert-concepts-not-indexed [coll1-1]))))


(deftest db-synchronize-collection-deletes-test
  (test-env/only-with-real-database
    (let [concept-counter (atom 1)
          coll1-1 (coll-concept concept-counter "CPROV1" "coll1" :iso-smap)
          coll2-1 (coll-concept concept-counter "CPROV1" "coll2")
          coll3-1 (coll-concept concept-counter "CPROV2" "coll3")
          coll4-1 (coll-concept concept-counter "CPROV2" "coll4")
          coll5-1 (coll-concept concept-counter "CPROV2" "coll5")
          ;; collection 5 is already deleted as a tombstone

          orig-colls [coll1-1 coll2-1 coll3-1 coll4-1 coll5-1]
          deleted-colls [coll1-1 coll3-1 coll5-1]
          non-deleted-colls [coll2-1 coll4-1]
          system (bootstrap/system)]

      ;; Save the concepts in Catalog REST
      (cat-rest/insert-concepts system orig-colls)

      ;; Migrate the providers. Catalog REST and Metadata DB are in sync
      (bootstrap/bulk-migrate-providers "CPROV1" "CPROV2")
      (bootstrap/bulk-index-providers "CPROV1" "CPROV2")

      (assert-concepts-in-mdb orig-colls)
      (assert-concepts-indexed orig-colls)

      ;; Delete the collections from Catalog REST
      (cat-rest/delete-concepts system deleted-colls)

      ;; Create a tombstone for concept 5
      (ingest/delete-concept coll5-1)

      ;; Catalog REST and Metadata DB are not in sync now.
      ;; Put them back in sync
      (bootstrap/synchronize-databases {:sync-types [:deletes]})

      ;; Check that they are synchronized now with the latest data.
      (assert-concepts-in-mdb non-deleted-colls)
      (assert-concepts-indexed non-deleted-colls)

      ;; Make sure the deleted collections are gone
      (assert-tombstones-in-mdb deleted-colls)
      (assert-concepts-not-indexed deleted-colls))))

(deftest db-synchronize-collection-deletes-specific-collection-test
  (test-env/only-with-real-database
    (let [concept-counter (atom 1)
          coll1-1 (coll-concept concept-counter "CPROV1" "coll1")
          coll2-1 (coll-concept concept-counter "CPROV1" "coll2")
          colls [coll1-1 coll2-1]
          system (bootstrap/system)]

      ;; Save the concepts in Catalog REST
      (cat-rest/insert-concepts system colls)

      ;; Migrate the providers. Catalog REST and Metadata DB are in sync
      (bootstrap/bulk-migrate-providers "CPROV1" "CPROV2")
      (bootstrap/bulk-index-providers "CPROV1" "CPROV2")

      (assert-concepts-in-mdb colls)
      (assert-concepts-indexed colls)

      ;; Delete the collections from Catalog REST
      (cat-rest/delete-concepts system colls)

      ;; Catalog REST and Metadata DB are not in sync now.
      ;; Put them back in sync
      (bootstrap/synchronize-databases {:sync-types [:deletes] :provider-id "CPROV1"
                                        :entry-title "coll2"})

      ;; Collection 1 was not targeted so it should still be in metadata db
      (assert-concepts-in-mdb [coll1-1])
      (assert-concepts-indexed [coll1-1])

      ;; Make sure the deleted collections are gone
      (assert-tombstones-in-mdb [coll2-1])
      (assert-concepts-not-indexed [coll2-1]))))

(deftest db-synchronize-collections-with-defaults-test
  (test-env/only-with-real-database
    (let [concept-counter (atom 0)
          coll1-1 (coll-concept concept-counter "CPROV1" "coll1")
          coll1-2 (updated-concept coll1-1)
          coll2-1 (coll-concept concept-counter "CPROV1" "coll2")
          coll2-2 (updated-concept coll2-1)
          ;; Collection 3 will have multiple revisions and then will be deleted.
          coll3-1 (coll-concept concept-counter "CPROV2" "coll3")
          coll3-2 (updated-concept coll3-1)
          coll3-3 (updated-concept coll3-2)
          ;; Collection 4 will not be updated but it will still get a newer revision id
          coll4-1 (coll-concept concept-counter "CPROV2" "coll4")
          coll4-2 (assoc coll4-1 :revision-id 2)
          ;; Collection 5 will be a new one
          coll5-1 (coll-concept concept-counter "CPROV2" "coll5")
          ;; Collection 6 will be deleted
          coll6-1 (coll-concept concept-counter "CPROV2" "coll6")

          ;; Collection 7 will have a tombstone
          coll7-1 (coll-concept concept-counter "CPROV2" "coll7")

          orig-colls [coll1-1 coll2-1 coll3-1 coll4-1 coll6-1 coll7-1] ;; 5 not added originally
          active-updates [coll1-2 coll2-2]
          deleted-colls [coll3-1 coll6-1 coll7-1]
          expected-colls [coll1-2 coll2-2 coll4-2 coll5-1]
          system (bootstrap/system)]

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; 1. Setup
      ;; Save the concepts in Catalog REST
      (cat-rest/insert-concepts system orig-colls)
      ;; Migrate the providers. Catalog REST and Metadata DB are in sync
      (bootstrap/bulk-migrate-providers "CPROV1" "CPROV2")
      (bootstrap/bulk-index-providers "CPROV1" "CPROV2")

      (assert-concepts-in-mdb orig-colls)
      (assert-concepts-indexed orig-colls)

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; 2. Make Catalog REST out of sync with Metadata DB
      (cat-rest/update-concepts system active-updates)
      ;; Collection 5 is inserted for the first time so it's not yet in Metadata DB
      (cat-rest/insert-concept system coll5-1)
      ;; Add a few more revisions for coll3
      (ingest/save-concept coll3-2)
      (ingest/save-concept coll3-3)
      (cat-rest/delete-concepts system deleted-colls)

      ;; Collection 7 was also deleted from metadata db
      (ingest/delete-concept coll7-1)


      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; 3. Synchronize
      (bootstrap/synchronize-databases)

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; 4. Verify
      (assert-concepts-in-mdb expected-colls)
      (assert-concepts-indexed expected-colls)
      ;; Check that the deleted items have tombstones in Metadata DB and are not indexed.
      (assert-tombstones-in-mdb deleted-colls)
      (assert-concepts-not-indexed deleted-colls))))

(deftest db-synchronize-granules-missing-test
  (test-env/only-with-real-database
    (let [concept-counter (atom 1)
          coll1 (coll-concept concept-counter "CPROV1" "coll1")
          coll2 (coll-concept concept-counter "CPROV2" "coll2")
          colls [coll1 coll2]
          ;; gran1 will exist in both
          gran1-1 (gran-concept concept-counter coll1 "gran1")
          ;; gran 1 has been updated but this particular synchronization will miss it
          gran1-2 (updated-concept gran1-1)
          ;;Granules 2 - 4 will be missing
          gran2-1 (gran-concept concept-counter coll1 "gran2")
          gran3-1 (gran-concept concept-counter coll2 "gran3")
          gran4-1 (gran-concept concept-counter coll2 "gran4")

          ;; granule 5 will have a tombstone
          gran5-1 (gran-concept concept-counter coll2 "gran5")
          ;; The updated version will be revision 3 (tombstone is 2)
          gran5-3 (updated-concept (updated-concept gran5-1))

          ;; Granule 6 will have a delete date in the past
          gran6-1 (gran-concept-with-delete-date-in-the-past concept-counter coll2 "gran6")

          orig-grans [gran1-1 gran5-1]
          updated-grans [gran1-1 gran2-1 gran3-1 gran4-1 gran5-3]
          system (bootstrap/system)]

      ;; Save the concepts in Catalog REST
      (cat-rest/insert-concepts system colls)
      (cat-rest/insert-concepts system orig-grans)

      ;; Migrate the providers. Catalog REST and Metadata DB are in sync
      (bootstrap/bulk-migrate-providers "CPROV1" "CPROV2")
      (bootstrap/bulk-index-providers "CPROV1" "CPROV2")

      (assert-concepts-in-mdb orig-grans)
      (assert-concepts-indexed orig-grans)

      ;; Create a tombstone for concept 5
      (ingest/delete-concept gran5-1)

      ;; Insert/Update the concepts in catalog rest.
      (cat-rest/update-concepts system [gran1-2 gran5-3])
      (cat-rest/insert-concepts system [gran2-1 gran3-1 gran4-1 gran6-1])

      ;; Catalog REST and Metadata DB are not in sync now.
      ;; Put them back in sync
      (bootstrap/synchronize-databases {:sync-types [:missing]})

      ;; Check that they are synchronized now with the latest data.
      (assert-concepts-in-mdb updated-grans)
      (assert-concepts-indexed updated-grans)

      ;; Check that granule 6 wasn't synchronized
      (assert-concepts-not-in-mdb [gran6-1])
      (assert-concepts-not-indexed [gran6-1]))))

(deftest db-synchronize-granules-missing-specific-collection-test
  (test-env/only-with-real-database
    (let [concept-counter (atom 1)
          coll1 (coll-concept concept-counter "CPROV1" "coll1")
          coll2 (coll-concept concept-counter "CPROV2" "coll2")
          colls [coll1 coll2]
          gran1-1 (gran-concept concept-counter coll1 "gran1")
          ;;Granules 2 - 4 will be missing
          gran2-1 (gran-concept concept-counter coll1 "gran2")
          gran3-1 (gran-concept concept-counter coll2 "gran3")
          gran4-1 (gran-concept concept-counter coll2 "gran4")

          orig-grans [gran1-1]
          system (bootstrap/system)]

      ;; Save the concepts in Catalog REST
      (cat-rest/insert-concepts system colls)
      (cat-rest/insert-concepts system orig-grans)

      ;; Migrate the providers. Catalog REST and Metadata DB are in sync
      (bootstrap/bulk-migrate-providers "CPROV1" "CPROV2")
      (bootstrap/bulk-index-providers "CPROV1" "CPROV2")

      (assert-concepts-in-mdb orig-grans)
      (assert-concepts-indexed orig-grans)

      ;; Insert/Update the concepts in catalog rest.
      (cat-rest/insert-concepts system [gran2-1 gran3-1 gran4-1])

      ;; Catalog REST and Metadata DB are not in sync now.
      ;; Put them back in sync
      (bootstrap/synchronize-databases {:sync-types [:missing] :provider-id "CPROV2"
                                        :entry-title "coll2"})

      ;; Check that they are synchronized now with the latest data.
      (assert-concepts-in-mdb [gran1-1 gran3-1 gran4-1])
      (assert-concepts-indexed [gran1-1 gran3-1 gran4-1])

      ;; Check that granules in other collections weren't synchronized
      (assert-concepts-not-in-mdb [gran2-1])
      (assert-concepts-not-indexed [gran2-1]))))

(deftest db-synchronize-granules-defaults-test
  (test-env/only-with-real-database
    (let [concept-counter (atom 0)
          coll1-1 (coll-concept concept-counter "CPROV1" "coll1")
          coll2-1 (coll-concept concept-counter "CPROV1" "coll2")
          coll3-1 (coll-concept concept-counter "CPROV2" "coll3")

          gran1-1 (gran-concept concept-counter coll1-1 "gran1")
          gran1-2 (updated-concept gran1-1)
          gran2-1 (gran-concept concept-counter coll2-1 "gran2")
          gran2-2 (updated-concept gran2-1)
          ;; Granule 3 will have multiple revisions and then will be deleted.
          gran3-1 (gran-concept concept-counter coll3-1 "gran3")
          gran3-2 (updated-concept gran3-1)
          gran3-3 (updated-concept gran3-2)
          ;; granule 4 will not be updated but it will still get a newer revision id
          gran4-1 (gran-concept concept-counter coll3-1 "gran4")
          gran4-2 (assoc gran4-1 :revision-id 2)
          ;; granule 5 will be a new one
          gran5-1 (gran-concept concept-counter coll3-1 "gran5")
          ;; granule 6 will be deleted
          gran6-1 (gran-concept concept-counter coll3-1 "gran6")

          ;; granule 7 will have a tombstone
          gran7-1 (gran-concept concept-counter coll3-1 "gran7")

          orig-grans [gran1-1 gran2-1 gran3-1 gran4-1 gran6-1 gran7-1] ;; 5 not added originally
          active-updates [gran1-2 gran2-2]
          deleted-grans [gran3-1 gran6-1 gran7-1]
          expected-grans [gran1-2 gran2-2 gran4-2 gran5-1]
          system (bootstrap/system)]

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; 1. Setup
      ;; Save the concepts in Catalog REST
      (cat-rest/insert-concepts system [coll1-1 coll2-1 coll3-1])
      (cat-rest/insert-concepts system orig-grans)
      ;; Migrate the providers. Catalog REST and Metadata DB are in sync
      (bootstrap/bulk-migrate-providers "CPROV1" "CPROV2")
      (bootstrap/bulk-index-providers "CPROV1" "CPROV2")

      (assert-concepts-in-mdb orig-grans)
      (assert-concepts-indexed orig-grans)

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; 2. Make Catalog REST out of sync with Metadata DB
      (cat-rest/update-concepts system active-updates)
      ;; granule 5 is inserted for the first time so it's not yet in Metadata DB
      (cat-rest/insert-concept system gran5-1)
      ;; Add a few more revisions for gran3
      (ingest/save-concept gran3-2)
      (ingest/save-concept gran3-3)
      (cat-rest/delete-concepts system deleted-grans)

      ;; granule 7 was also deleted from metadata db
      (ingest/delete-concept gran7-1)


      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; 3. Synchronize
      (bootstrap/synchronize-databases)

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; 4. Verify
      (assert-concepts-in-mdb expected-grans)
      (assert-concepts-indexed expected-grans)
      ;; Check that the deleted items have tombstones in Metadata DB and are not indexed.
      (assert-tombstones-in-mdb deleted-grans)
      (assert-concepts-not-indexed deleted-grans))))

(defn sorted-concept-id-map
  "Creates an empty sorted map that sorts the concept id keys by their numeric value."
  []
  (sorted-map-by
    (fn [cid1 cid2]
      (compare (:sequence-number (concepts/parse-concept-id cid1))
               (:sequence-number (concepts/parse-concept-id cid2))))))

(defn initial-holdings
  "Returns an in memory representation of the holdings that are expected to be in Metadata DB. It is
  a map of provider ids to a map of concept types to a map of concept ids to vector of concepts.
  The map of concept ids is sorted by the numeric id in the concept id. The vectors of concepts
  are in revision order."
  [& provider-ids]
  (into {} (for [provider-id provider-ids]
             [provider-id {:collection (sorted-concept-id-map)
                           :granule (sorted-concept-id-map)}])))

(defn holdings-append-concepts
  "Appends the given concepts to the in memory representation of the holdings"
  [holdings provider-id concept-type concepts]
  (update-in holdings [provider-id concept-type]
             (fn [concept-id-map]
               (reduce (fn [concept-id-map concept]
                         (update-in concept-id-map [(:concept-id concept)]
                                    (fn [concepts]
                                      (if (seq concepts)
                                        (conj concepts concept)
                                        [concept]))))
                       concept-id-map
                       concepts))))

(defn holdings->concepts
  "Returns all the concepts in the holdings."
  [holdings]
  (apply concat (for [[provider-id concept-type-map] holdings
                      [concept-type concepts-map] concept-type-map
                      [concept-id concepts] concepts-map]
                  concepts)))

(defn holdings->latest-concepts
  "Returns the latests revision of every concept in the holdings"
  [holdings]
  (for [[provider-id concept-type-map] holdings
        [concept-type concepts-map] concept-type-map
        [concept-id concepts] concepts-map]
    (last concepts)))

(defn first-n-holdings
  "Gets the first n holdings from the specified provider and concept type."
  ([holdings provider-id concept-type]
   (->> (holdings provider-id)
        concept-type
        vals
        (map last)))
  ([holdings provider-id concept-type n]
   (take n (first-n-holdings holdings provider-id concept-type))))

(defn last-n-holdings
  "Gets the last n holdings from the specified provider and concept type."
  [holdings provider-id concept-type n]
  (->> (first-n-holdings holdings provider-id concept-type)
       reverse
       (take n)))

(defn prettify-holdings
  "Simplify the holdings for pretty print."
  [holdings]
  (into {}
        (for [[provider-id concept-type-map] holdings]
          [provider-id
           (into {}
                 (for [[concept-type concept-id-map] concept-type-map]
                   [concept-type
                    (into (sorted-concept-id-map)
                          (for [[concept-id concepts] concept-id-map]
                            [concept-id
                             (mapv #(select-keys % [:deleted :revision-id])
                                   concepts)]))]))])))

(defn simulate-insert-sync
  "During synchronization of Catalog REST and Metadata DB every item in the date range will be
  copied to mdb as a new revision. This simulates that change in the holdings by looking for
  concepts that haven't changed from the old-holdings to the new-holdings and creating a new
  revision."
  [new-holdings old-holdings]
  (reduce (fn [holdings latest-concept]
            (let [concept-id (:concept-id latest-concept)
                  {:keys [provider-id concept-type]} (concepts/parse-concept-id concept-id)
                  latest-old-concept (last (get-in old-holdings [provider-id concept-type concept-id]))]
              (if (= (:revision-id latest-concept) (:revision-id latest-old-concept))
                ;; This concept isn't being updated so we need to add a copy with a different revision id.
                (holdings-append-concepts holdings provider-id concept-type
                                          [(update-in latest-concept [:revision-id] inc)])
                ;; No change needed
                holdings)))
          new-holdings
          (filter (complement :deleted)
                  (holdings->latest-concepts new-holdings))))


(defmulti insert-concepts
  "Inserts the concepts into Catalog REST, the in memory holdings, and optionally metadata db."
  (fn [holdings concept-counter concept-type num-inserts modify-mdb?]
    concept-type))

(defmethod insert-concepts :collection
  [holdings concept-counter concept-type num-inserts modify-mdb?]
  (let [system (bootstrap/system)]
    (reduce (fn [holdings provider-id]
              (let [num-existing (-> holdings (get provider-id) concept-type count)
                    concepts (for [n (range num-inserts)]
                               (coll-concept concept-counter provider-id
                                             (str "coll" (inc (+ num-existing n)))))]
                (cat-rest/insert-concepts system concepts)
                (when modify-mdb? (ingest/ingest-concepts concepts))
                (holdings-append-concepts holdings provider-id concept-type concepts)))
            holdings
            (keys holdings))))

(defmethod insert-concepts :granule
  [holdings concept-counter concept-type num-inserts modify-mdb?]
  (let [system (bootstrap/system)]
    (reduce (fn [holdings provider-id]
              (let [num-existing (-> holdings (get provider-id) concept-type count)
                    concepts (map (fn [n coll]
                                    (gran-concept concept-counter coll
                                                  (str "gran" (inc (+ num-existing n)))))
                                  (range num-inserts)
                                  (cycle (filter (complement :deleted)
                                                 (first-n-holdings holdings provider-id :collection))))]
                (cat-rest/insert-concepts system concepts)
                (when modify-mdb? (ingest/ingest-concepts concepts))
                (holdings-append-concepts holdings provider-id concept-type concepts)))
            holdings
            (keys holdings))))

(defn update-concepts
  "Updates the first N concepts into Catalog REST, the in memory holdings, and optionally metadata
  db."
  [holdings concept-type num-updates modify-mdb?]
  (let [system (bootstrap/system)]
    (reduce (fn [holdings provider-id]
              (let [concepts (map updated-concept
                                  (first-n-holdings holdings provider-id concept-type num-updates))]
                (cat-rest/update-concepts system concepts)
                (when modify-mdb? (ingest/ingest-concepts concepts))
                (holdings-append-concepts holdings provider-id concept-type concepts)))
            holdings
            (keys holdings))))

(defn delete-concepts
  "Deletes the last N concepts in Catalog REST, the in memory holdings, and optionally metadata
  db."
  [holdings concept-type num-deletes modify-mdb?]
  (let [system (bootstrap/system)]
    (reduce (fn [holdings provider-id]
              (let [tombstones (map deleted-concept
                                    (filter (complement :deleted)
                                            (last-n-holdings
                                              holdings provider-id concept-type num-deletes)))]
                (cat-rest/delete-concepts system tombstones)
                (when modify-mdb? (ingest/delete-concepts tombstones))
                (holdings-append-concepts holdings provider-id concept-type tombstones)))
            holdings
            (keys holdings))))


(defn modify-holdings
  "Makes changes to the holdings based on the counts of changes per concept type in the counts map."
  [holdings concept-counter modify-mdb? counts]
  (reduce (fn [holdings [concept-type {:keys [num-inserts num-updates num-deletes]}]]
            (-> holdings
                (insert-concepts concept-counter concept-type (or num-inserts 0) modify-mdb?)
                (update-concepts concept-type (or num-updates 0) modify-mdb?)
                (delete-concepts concept-type (or num-deletes 0) modify-mdb?)))
          holdings
          (sort-by first counts)))

(defn verify-holdings
  "Verifies that the latest version of all concepts in the holdings are in metadata db and indexed
  or not in the index if they are tombstones."
  [holdings]
  (let [latest-concepts (holdings->latest-concepts holdings)
        {tombstones true
         non-tombstones false} (group-by :deleted latest-concepts)]
    (assert-concepts-in-mdb non-tombstones)
    (assert-tombstones-in-mdb tombstones)
    (assert-concepts-indexed non-tombstones)
    (assert-concepts-not-indexed tombstones))
  holdings)

;; This test will simulate the problem that occurred in ops.
;; Catalog REST and Metadata DB were in sync. Then new operations against Metadata DB stopped but
;; continued to Catalog REST. Then the problem was resolved but the data was now out of sync.

;; 1. Put a bunch of data in Metadata DB and Catalog REST. They should be in sync
;; 2. Make a bunch of changes in Catalog REST only making them out of sync.
;; 3. Make a bunch of changes in both.
;; 4. Run synchronize.
;; 5. Verify data is correct in MDB and Indexer

(def NUM_GRANULES_FACTOR 2)

(defn setup-holdings
  []
  (let [concept-counter (atom 0)
        ;; Map of provider ids to maps of concept types to maps of concept ids to sequences of concept revisions
        increase-by-factor #(* % NUM_GRANULES_FACTOR)
        orig-holdings (-> (initial-holdings "CPROV1" "CPROV2")
                          ;; Setup initial holdings
                          (modify-holdings concept-counter true
                                           {:collection {:num-inserts 10
                                                         :num-updates 2}
                                            :granule {:num-inserts (increase-by-factor 10)
                                                      :num-updates (increase-by-factor 1)
                                                      :num-deletes (increase-by-factor 1)}})
                          ;; We should be in sync
                          verify-holdings)]
    ;; Make Catalog REST out of sync with Metadata db
    (-> orig-holdings
        (modify-holdings concept-counter false
                         {:granule {:num-deletes (increase-by-factor 4)}})
        (modify-holdings concept-counter false
                         {:collection {:num-inserts 2
                                       :num-updates 4}
                          :granule {:num-inserts (increase-by-factor 1)
                                    :num-updates (increase-by-factor 2)}})
        (simulate-insert-sync orig-holdings))))


(deftest db-synchronize-many-items
  (test-env/only-with-real-database
    (let [updated-holdings (setup-holdings)]
      (bootstrap/synchronize-databases)
      (verify-holdings updated-holdings))))

(deftest db-synchronize-by-collection
  (test-env/only-with-real-database
    (let [updated-holdings (setup-holdings)]
      (doseq [[provider-id concept-type-map] updated-holdings
              [concept-id concepts] (:collection concept-type-map)]
        (bootstrap/synchronize-databases {:provider-id provider-id
                                          :entry-title (:native-id (first concepts))}))

      (verify-holdings updated-holdings))))

(deftest db-synchronize-by-provider-id
  (test-env/only-with-real-database
    (let [updated-holdings (setup-holdings)]
      (doseq [provider-id (keys updated-holdings)]
        (bootstrap/synchronize-databases {:provider-id provider-id}))
      (verify-holdings updated-holdings))))

