(ns cmr.system-int-test.ingest.message-queue-test
  "Tests behavior of ingest and indexer under different message queue failure scenarios."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]))

;; Setup provider

;; Test 1 - Initial index fails and retries once, completes successfully on retry

;; Turn message failures on - implement new endpoint, call new endpoint

;; Ingest collection and granule - normal way

;; Verify the collection and granule are in Oracle - metadata-db find concepts

;; Wait for at least one retry - TODO figure out how

;; Verify the collection and granule are not indexed - search returns 0 results

;; Turn on normal processing on messages - call new endpoint

;; Wait until the indexing queue is empty - wait-for-indexed

;; Verify the collection and granule are indexed - search returns correct results


;; Test 2 - all retries fail

;; Turn message failures on

;; Ingest one collection and one granule

;; Verify the collection and granule are in Oracle

;; Wait until the indexing queue is empty

;; Verify the collection and granule are not indexed

;; Manually verify retry intervals

;; Manually verify number of retries

;; Manually verify there is a message logged that we can create a Splunk alert against to
;; determine what data to index