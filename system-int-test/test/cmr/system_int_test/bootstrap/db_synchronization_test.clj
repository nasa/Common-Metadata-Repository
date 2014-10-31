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
            [cmr.system-int-test.utils.test-environment :as test-env]
            [cmr.bootstrap.test.catalog-rest :as cat-rest]
            [cmr.common.concepts :as concepts]))


(use-fixtures :each (bootstrap/db-fixture "CPROV1" "CPROV2"))

;; Consider doing a concurrency test with db synchronization and Catalog REST updates
;; Testing the conflicts
;; - Get the database in an inconsistent state with N items
;; - Thread 1: Update N/2 items in CR 5 times
;; - Thread 2: Run the synchronization
;; - Wait for threads to join
;; - Check that items at the end are all correct

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
  "TODO"
  [concept-counter concept-type provider-id]
  (concepts/build-concept-id {:concept-type concept-type
                              :sequence-number (swap! concept-counter inc)
                              :provider-id provider-id}))

(defn coll-concept
  "TODO"
  [concept-counter provider-id entry-title]
  (let [coll (dc/collection {:entry-title entry-title
                             ;; The summary will contain the revision number so subsequent revisions
                             ;; will have slightly different metadata
                             :summary "rev1"})]
    {:concept-type :collection
     :format "application/echo10+xml"
     :metadata (umm/umm->xml coll :echo10)
     :concept-id (next-concept-id concept-counter :collection provider-id)
     :revision-id 1
     :deleted false
     :extra-fields {:short-name (get-in coll [:product :short-name])
                    :entry-title entry-title
                    :version-id (get-in coll [:product :version-id])
                    :delete-time nil}
     :provider-id provider-id
     :native-id entry-title}))

(defn updated-concept
  "TODO"
  [concept]
  (let [new-revision-id (inc (:revision-id concept))
        updated-metadata (-> concept
                             umm/parse-concept
                             (assoc :summary (str "rev" new-revision-id))
                             (umm/umm->xml :echo10))]
    (assoc concept
           :revision-id new-revision-id
           :metadata updated-metadata)))

(defn assert-concepts-in-mdb
  "TODO"
  [concepts]
  (doseq [concept concepts]
    (is (= concept
           (dissoc (ingest/get-concept (:concept-id concept)) :revision-date)))))

(defn assert-tombstones-in-mdb
  "TODO"
  [concepts]
  (doseq [concept concepts]
    (is (:deleted (ingest/get-concept (:concept-id concept))))))

(defn assert-concepts-indexed
  "TODO"
  [concepts]
  (index/refresh-elastic-index)
  (doseq [[concept-type type-concepts] (group-by :concept-type concepts)]
    (let [expected-tuples (map #(vector (:concept-id %) (:revision-id %)) type-concepts)
          results (search/find-refs concept-type {:concept-id (map :concept-id type-concepts)})
          found-tuples (map #(vector (:id %) (:revision-id %)) (:refs results))]
      (is (= (set expected-tuples) (set found-tuples))))))

(defn assert-concepts-not-indexed
  "TODO"
  [concepts]
  (index/refresh-elastic-index)
  (doseq [[concept-type type-concepts] (group-by :concept-type concepts)]
    (let [expected-tuples (map #(vector (:concept-id %) (:revision-id %)) type-concepts)
          results (search/find-refs concept-type {:concept-id (map :concept-id type-concepts)})]
      (is (= 0 (:hits results)) (str "Expected 0 found " (pr-str results))))))

;; TODO add a test that allows _many_ items across multiple providers to be out of synch and checks
;; that they are all correct at the end.

;; TODO Add delete tests

;; TODO add tests that send start and end times for checking for missing items.

;; TODO add some tombstones in metadata db befor the sync.
;; Some of the tombstoned items will be readded before the sync.

(deftest db-synchronize-inserts-test
  (test-env/only-with-real-database
    (let [concept-counter (atom 1)
          coll1-1 (coll-concept concept-counter "CPROV1" "coll1")
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


      ;; Catalog REST and Metadata DB are not in sync now.
      ;; Put them back in sync
      (bootstrap/synchronize-databases)

      ;; Check that they are synchronized now with the latest data.
      (assert-concepts-in-mdb updated-colls)
      (assert-concepts-indexed updated-colls)

      )))



(deftest db-synchronize-collections-test
  (test-env/only-with-real-database
    (let [concept-counter (atom 0)
          coll1-1 (coll-concept concept-counter "CPROV1" "coll1")
          coll1-2 (updated-concept coll1-1)
          coll2-1 (coll-concept concept-counter "CPROV1" "coll2")
          coll2-2 (updated-concept coll2-1)
          ;; Collection 3 will have multiple revisions and then will be deleted
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
      (assert-concepts-not-indexed deleted-colls)

      )))



