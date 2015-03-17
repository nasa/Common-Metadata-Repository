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

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))


(defn ingest-coll
  "Ingests the collection"
  [coll]
  (d/ingest "PROV1" coll))

(defn make-coll
  "Creates and ingests a collection using the unique number given"
  [n]
  (ingest-coll (dc/collection {:entry-title (str "ET" n)})))

(defn update-coll
  "Updates the collection with the given attributes"
  [coll attribs]
  (ingest-coll (merge coll attribs)))

(defn ingest-gran
  "Validates and ingests the granle"
  [coll granule]
  (d/ingest "PROV1" granule))

(defn make-gran
  "Creates and ingests a granule using the unique number given"
  [coll n]
  (ingest-gran coll (dg/granule coll {:granule-ur (str "GR" n)})))

(defn update-gran
  "Updates the granule with the given attributes"
  [coll gran attribs]
  (ingest-gran coll (merge gran attribs)))

(deftest message-queue-concept-history-test
  (s/only-with-real-message-queue
    (let [coll1 (make-coll 1)
          coll2 (make-coll 2)
          coll3 (make-coll 3)
          coll4 (make-coll 2)
          coll5 (make-coll 2)
          gran1 (make-gran coll1 1)]
      (index-util/wait-until-indexed)
      (testing "successfully processed concepts"
        (is (= {[(:concept-id gran1) (:revision-id gran1)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "processed"}],
                [(:concept-id coll5) (:revision-id coll5)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "processed"}],
                [(:concept-id coll4) (:revision-id coll4)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "processed"}],
                [(:concept-id coll3) (:revision-id coll3)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "processed"}],
                [(:concept-id coll2) (:revision-id coll2)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "processed"}],
                [(:concept-id coll1) (:revision-id coll1)]
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
             {:concept-id (:concept-id collection)} :collection [collection]
             {:concept-id (:concept-id granule)} :granule [granule])

        ;; Verify retried exactly one time and at the correct retry interval
        (is (= {[(:concept-id granule) (:revision-id granule)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "processed"}],
                [(:concept-id collection) (:revision-id collection)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "processed"}]}
               (index-util/get-concept-message-queue-history))))

      (index-util/set-message-queue-retry-behavior 0))))

;; Manually verify retry intervals
;; Manually verify number of retries
;; Manually verify there is a message logged that we can create a Splunk alert against to
;; determine what data to index
(deftest message-queue-failure-test
  (s/only-with-real-message-queue
    (testing "Indexing attempts fail with retryable error and eventually all retries are exhausted"
      ;; TODO - change this to 6 once I can make retry interval short
      (index-util/set-message-queue-retry-behavior 1)
      (let [collection (make-coll 1)
            granule (make-gran collection 1)]
        ;; Verify the collection and granule are in Oracle - metadata-db find concepts
        (is (ingest/concept-exists-in-mdb? (:concept-id collection) (:revision-id collection)))
        (is (ingest/concept-exists-in-mdb? (:concept-id granule) (:revision-id granule)))
        (index-util/wait-until-indexed)
        ;; Verify the collection and granule are not indexed
        (are [search concept-type expected]
             (d/refs-match? expected (search/find-refs concept-type search))
             {:concept-id (:concept-id collection)} :collection []
             {:concept-id (:concept-id granule)} :granule [])

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
               (index-util/get-concept-message-queue-history))))

      (index-util/set-message-queue-retry-behavior 0))))


