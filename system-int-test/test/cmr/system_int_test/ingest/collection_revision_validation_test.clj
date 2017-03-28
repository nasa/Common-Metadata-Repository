(ns cmr.system-int-test.ingest.collection-revision-validation-test
  "CMR Ingest revision validation integration tests"
  (:require
    [clojure.test :refer :all]
    [cmr.ingest.services.messages :as msg]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.utils.ingest-util :as ingest]))

(defn- assert-revision-conflict
  [concept-id format-str response]
  (is (= {:status 409
          :errors [(format format-str concept-id)]}
         response)))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest revision-id-validations
  (testing "ingesting a concept with the same concept-id and revision-id fails"
    (let [concept-id "C1-PROV1"
          existing-concept (data-umm-c/collection-concept {:EntryTitle "E1" :ShortName "S1" :revision-id 1 :concept-id concept-id})
          _ (ingest/ingest-concept existing-concept)
          response (ingest/ingest-concept existing-concept)]
      (is (= {:status 409
              :errors [(format "Expected revision-id of [2] got [1] for [%s]" concept-id)]}
             response))))
 
  (testing "attempting to ingest using an non-integer revision id returns an error"
    (let [response (ingest/ingest-concept (data-umm-c/collection-concept {:EntryTitle "E2" :ShortName "S2" :concept-id "C2-PROV1" :revision-id "NaN"}))]
      (is (= {:status 422
              :errors [(msg/invalid-revision-id "NaN")]}
             response))))
  
  (testing "attempting to ingest using a negative revision id returns an error"
    (let [response (ingest/ingest-concept (data-umm-c/collection-concept {:EntryTitle "E3" :ShortName "S3" :concept-id "C2-PROV1" :revision-id "-1"}))]
      (is (= {:status 422
              :errors [(msg/invalid-revision-id "-1")]}
             response))))
  
  (testing "ingesting a concept with just the revision-id succeeds"
    (let [response (ingest/ingest-concept (data-umm-c/collection-concept {:EntryTitle "E4" :ShortName "S4" :revision-id "2"}))]
      (is (and (= 200 (:status response)) (= 2 (:revision-id response))))))
  
  (testing "ingesting a concept while skipping revision-ids succeeds, 
            but fails if revision id is smaller than the maximum revision id"
    (let [concept-id "C3-PROV1"
          coll (data-umm-c/collection-concept {:EntryTitle "E5" :ShortName "S5" :concept-id concept-id})
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

(deftest revision-conflict-tests
  (testing "Update with lower revision id should be rejected 
            if it comes after an concept with a higher revision id"
    (let [concept (data-umm-c/collection-concept {:EntryTitle "E1" :ShortName "S1" :revision-id 4})
          concept-id (:concept-id (ingest/ingest-concept concept))
          response (ingest/ingest-concept (assoc concept :revision-id 2))]
      (assert-revision-conflict concept-id "Expected revision-id of [5] got [2] for [%s]" response)))

  (testing "Delete with lower revision id than latest concept should be rejected"
    (let [concept (data-umm-c/collection-concept {:EntryTitle "E2" :ShortName "S2" :revision-id 4})
          concept-id (:concept-id (ingest/ingest-concept concept))
          response (ingest/delete-concept concept {:revision-id 2})]
      (assert-revision-conflict concept-id "Expected revision-id of [5] got [2] for [%s]" response)))

  (testing "Ingest with lower revision id than latest tombstone should be rejected"
    (let [concept (data-umm-c/collection-concept {:EntryTitle "E3" :ShortName "S3"})
          concept-id (:concept-id (ingest/ingest-concept concept))
          _ (ingest/delete-concept concept {:revision-id 5})
          response (ingest/ingest-concept (assoc concept :revision-id 3))]
      (assert-revision-conflict concept-id "Expected revision-id of [6] got [3] for [%s]" response)))

  (testing "Delete with lower revision id than latest tombstone results in a 404"
    (let [concept (data-umm-c/collection-concept {:EntryTitle "E4" :ShortName "S4"})
          concept-id (:concept-id (ingest/ingest-concept concept))
          _ (ingest/delete-concept concept {:revision-id 5})
          {:keys [status errors]} (ingest/delete-concept concept {:revision-id 3})]
      (is (= status 404))
      (is (= errors [(format "Concept with native-id [%s] and concept-id [%s] is already deleted."
                             (:native-id concept) concept-id)]))))

  (testing "Deleting non-existent collection should be rejected"
    (let [concept (data-umm-c/collection-concept {:EntryTitle "E5" :ShortName "S5"})
          response (ingest/delete-concept concept {:revision-id 2})
          {:keys [status errors]} response]
      (is (= status 404))
      (is (re-find #"Collection .* does not exist" (first errors))))))

