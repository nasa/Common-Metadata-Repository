(ns cmr.system-int-test.ingest.message-queue-test
  "Tests behavior of ingest and indexer under different message queue failure scenarios."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.index-util :as index-util]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       (index-util/reset-message-queue-retry-behavior-fixture)]))

(defn ingest-coll
  "Ingests the collection."
  [coll]
  (d/ingest "PROV1" coll))

(defn make-coll
  "Creates and ingests a collection using the unique number given."
  [n]
  (ingest-coll (dc/collection {:entry-title (str "ET" n)})))

(defn ingest-gran
  "Ingests the granule."
  [granule]
  (d/ingest "PROV1" granule))

(defn make-gran
  "Creates and ingests a granule using the unique number given."
  [coll n]
  (ingest-gran (dg/granule coll {:granule-ur (str "GR" n)})))

(deftest message-queue-concept-history-test
  (s/only-with-real-message-queue
    (let [coll1-1 (make-coll 1)
          coll2-1 (make-coll 2)
          coll3-1 (make-coll 3)
          coll2-2 (make-coll 2)
          coll2-3 (make-coll 2)
          gran1-1 (make-gran coll1-1 1)]
      (index-util/wait-until-indexed)
      (testing "successfully processed concepts"
        (is (= {[(:concept-id gran1-1) (:revision-id gran1-1)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "processed"}],
                [(:concept-id coll2-3) (:revision-id coll2-3)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "processed"}],
                [(:concept-id coll2-2) (:revision-id coll2-2)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "processed"}],
                [(:concept-id coll3-1) (:revision-id coll3-1)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "processed"}],
                [(:concept-id coll2-1) (:revision-id coll2-1)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "processed"}],
                [(:concept-id coll1-1) (:revision-id coll1-1)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "processed"}]}
               (index-util/get-concept-message-queue-history)))))))

(deftest message-queue-retry-test
  (s/only-with-real-message-queue
    (testing "Initial index fails and retries once, completes successfully on retry"
      (index-util/set-message-queue-retry-behavior 1)
      (let [collection (make-coll 1)
            granule (make-gran collection 1)]
        ;; Verify the collection and granule are in Oracle - metadata-db find concepts
        (is (ingest/concept-exists-in-mdb? (:concept-id collection) (:revision-id collection)))
        (is (ingest/concept-exists-in-mdb? (:concept-id granule) (:revision-id granule)))
        (index-util/wait-until-indexed)
        ;; Verify the collection and granule are indexed - search returns correct results
        (are [search concept-type expected]
             (d/refs-match? expected (search/find-refs concept-type search))
             (select-keys collection [:concept-id]) :collection [collection]
             (select-keys granule [:concept-id]) :granule [granule])

        ;; Verify retried exactly one time and at the correct retry interval
        (is (= {[(:concept-id granule) (:revision-id granule)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "processed"}],
                [(:concept-id collection) (:revision-id collection)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "processed"}]}
               (index-util/get-concept-message-queue-history)))))))

(deftest message-queue-failure-test
  (s/only-with-real-message-queue
    (testing "Indexing attempts fail with retryable error and eventually all retries are exhausted"
      (index-util/set-message-queue-retry-behavior 6)
      (let [collection (make-coll 1)
            granule (make-gran collection 1)]
        ;; Verify the collection and granule are in Oracle - metadata-db find concepts
        (is (ingest/concept-exists-in-mdb? (:concept-id collection) (:revision-id collection)))
        (is (ingest/concept-exists-in-mdb? (:concept-id granule) (:revision-id granule)))
        (index-util/wait-until-indexed)
        ;; Verify the collection and granule are not indexed
        (are [search concept-type expected]
             (d/refs-match? expected (search/find-refs concept-type search))
             (select-keys collection [:concept-id]) :collection []
             (select-keys granule [:concept-id]) :granule [])

        ;; Verify retried five times and then marked as a failure
        (is (= {[(:concept-id granule) (:revision-id granule)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "failed"}],
                [(:concept-id collection) (:revision-id collection)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "failed"}]}
               (index-util/get-concept-message-queue-history)))))))


