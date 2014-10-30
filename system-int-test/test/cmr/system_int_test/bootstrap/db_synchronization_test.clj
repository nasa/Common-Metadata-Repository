(ns cmr.system-int-test.bootstrap.db-synchronization-test
  "This tests putting the Catalog REST and Metadata DB in an inconsistent state and then using
  the bootstrap application to make them consistent again."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
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
    (bootstrap/db-fixture-setup "CPROV1")

    (def concept-counter (atom 0))
    (def coll1-1 (coll-concept concept-counter "CPROV1" "coll1"))
    (def coll1-2 (updated-concept coll1-1))

    (cat-rest/insert-concept (bootstrap/system) coll1-1)
    (bootstrap/bulk-migrate-provider "CPROV1")
    (assert-concept-in-mdb coll1-1)

    (cat-rest/update-concept (bootstrap/system) coll1-2))


  (bootstrap/synchronize-databases)

    (assert-concept-in-mdb coll1-2)

  (bootstrap/db-fixture-tear-down "CPROV1")

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

;; TODO we need tests with multiple collections, inserts, updates, deletes all being different
;; and multiple providers

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
          orig-colls [coll1-1 coll2-1 coll3-1 coll4-1]
          updated-colls [coll1-2 coll2-2 coll3-2 coll4-2]
          system (bootstrap/system)]

      ;; Save the concepts in Catalog REST
      (cat-rest/insert-concepts system orig-colls)

      ;; Migrate the providers. Catalog REST and Metadata DB are in sync
      (bootstrap/bulk-migrate-providers "CPROV1" "CPROV2")

      (assert-concepts-in-mdb orig-colls)

      ;; Update the concepts in catalog rest.
      (cat-rest/update-concepts system [coll1-2 coll2-2 coll3-2])

      ;; Catalog REST and Metadata DB are not in sync now.
      ;; Put them back in sync
      (bootstrap/synchronize-databases)

      ;; Check that they are synchronized now with the latest data.
      (assert-concepts-in-mdb updated-colls)

      ;; TODO check that we can search for an item and find it. (all items have been indexed)


      )))


