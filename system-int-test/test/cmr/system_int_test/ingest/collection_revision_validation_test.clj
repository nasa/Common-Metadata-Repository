(ns cmr.system-int-test.ingest.collection-revision-validation-test
  "CMR Ingest revision validation integration tests"
  (:require
    [clojure.test :refer :all]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.data2.collection :as dc]
    [cmr.ingest.services.messages :as msg]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; The following tests are included in this file
;; See individual deftest for detailed test info.
;; 
;; 1. revision-id-validations
;; 2. revision-conflict-tests
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; helper function section
;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- assert-revision-conflict
  [concept-id format-str response]
  (is (= {:status 409
          :errors [(format format-str concept-id)]}
         response)))

;;;;;;;;;;;;;;;;;;;;
;; Testing section
;;;;;;;;;;;;;;;;;;;;
(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; The following tests are included in revision-id-validations:
;; 1. ingesting a concept with the same concept-id and revision-id fails
;; 2. attempting to ingest using an non-integer revision id returns an error
;; 3. attempting to ingest using a negative revision id returns an error
;; 4. ingesting a concept with just the revision-id succeeds
;; 5. ingesting a concept while skipping revision-ids succeeds,
;;    but fails if revision id is smaller than the maximum revision id
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest revision-id-validations
  (testing "ingesting a concept with the same concept-id and revision-id fails"
    (let [concept-id "C1-PROV1"
          existing-concept (dc/collection-concept {:revision-id 1 :concept-id concept-id})
          _ (ingest/ingest-concept existing-concept)
          response (ingest/ingest-concept existing-concept)]
      (is (= {:status 409
              :errors [(format "Expected revision-id of [2] got [1] for [%s]" concept-id)]}
             response))))
 
  (testing "attempting to ingest using an non-integer revision id returns an error"
    (let [response (ingest/ingest-concept (dc/collection-concept {:concept-id "C2-PROV1"
                                                                  :revision-id "NaN"}))]
      (is (= {:status 422
              :errors [(msg/invalid-revision-id "NaN")]}
             response))))
  
  (testing "attempting to ingest using a negative revision id returns an error"
    (let [response (ingest/ingest-concept (dc/collection-concept {:concept-id "C2-PROV1"
                                                                  :revision-id "-1"}))]
      (is (= {:status 422
              :errors [(msg/invalid-revision-id "-1")]}
             response))))
  
  (testing "ingesting a concept with just the revision-id succeeds"
    (let [response (ingest/ingest-concept (dc/collection-concept {:revision-id "2"}))]
      (is (and (= 200 (:status response)) (= 2 (:revision-id response))))))
  
  (testing "ingesting a concept while skipping revision-ids succeeds, 
            but fails if revision id is smaller than the maximum revision id"
    (let [concept-id "C3-PROV1"
          coll (dc/collection-concept {:concept-id concept-id})
          _ (ingest/ingest-concept (assoc coll :revision-id "2"))
          response1 (ingest/ingest-concept (assoc coll :revision-id "6"))
          response2 (ingest/ingest-concept (assoc coll :revision-id "4"))]
      (is (and (= 200 (:status response1)) (= 6 (:revision-id response1))))
      (is (= {:status 409
              :errors [(format "Expected revision-id of [7] got [4] for [%s]" concept-id)]}
             response2)))))

;; Added to test out of order processing of ingest and delete requests with revision-ids in their
;; header. The proper handling of incorrectly ordered requests is important for Virtual Product
;; Service which picks events off the queue and sends them to ingest service. It cannot be
;; guaranteed that the ingest events are processed by Virtual Product Service in the same order
;; that the events are placed on the queue.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; The following tests are included in revision-conflict-tests
;; 1. Update with lower revision id should be rejected
;; 2. Delete with lower revision id than latest concept should be rejected
;; 3. Ingest with lower revision id than latest tombstone should be rejected
;; 4. Delete with lower revision id than latest tombstone results in a 404 
;; 5. Deleting non-existent collection should be rejected
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest revision-conflict-tests
  (testing "Update with lower revision id should be rejected 
            if it comes after an concept with a higher revision id"
    (let [concept (dc/collection-concept {:revision-id 4})
          concept-id (:concept-id (ingest/ingest-concept concept))
          response (ingest/ingest-concept (assoc concept :revision-id 2))]
      (assert-revision-conflict concept-id "Expected revision-id of [5] got [2] for [%s]" response)))

  (testing "Delete with lower revision id than latest concept should be rejected"
    (let [concept (dc/collection-concept {:revision-id 4})
          concept-id (:concept-id (ingest/ingest-concept concept))
          response (ingest/delete-concept concept {:revision-id 2})]
      (assert-revision-conflict concept-id "Expected revision-id of [5] got [2] for [%s]" response)))

  (testing "Ingest with lower revision id than latest tombstone should be rejected"
    (let [concept (dc/collection-concept {})
          concept-id (:concept-id (ingest/ingest-concept concept))
          _ (ingest/delete-concept concept {:revision-id 5})
          response (ingest/ingest-concept (assoc concept :revision-id 3))]
      (assert-revision-conflict concept-id "Expected revision-id of [6] got [3] for [%s]" response)))

  (testing "Delete with lower revision id than latest tombstone results in a 404"
    (let [concept (dc/collection-concept {})
          concept-id (:concept-id (ingest/ingest-concept concept))
          _ (ingest/delete-concept concept {:revision-id 5})
          {:keys [status errors]} (ingest/delete-concept concept {:revision-id 3})]
      (is (= status 404))
      (is (= errors [(format "Concept with native-id [%s] and concept-id [%s] is already deleted."
                             (:native-id concept) concept-id)]))))

  (testing "Deleting non-existent collection should be rejected"
    (let [concept (dc/collection-concept {})
          response (ingest/delete-concept concept {:revision-id 2})
          {:keys [status errors]} response]
      (is (= status 404))
      (is (re-find #"Collection .* does not exist" (first errors))))))

