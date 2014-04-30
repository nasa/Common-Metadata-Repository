(ns ^{:doc "CMR Ingest integration tests"}
  cmr.system-int-test.ingest.collection-ingest-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.index-util :as index]
            [ring.util.io :as io]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [clojure.string :as string]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.old-ingest-util :as old-ingest]))


(use-fixtures :each (ingest/reset-fixture "PROV1"))

;;; tests
;;; ensure metadata, indexer and ingest apps are accessable on ports 3001, 3004 and 3002 resp;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Verify a new concept is ingested successfully.
(deftest collection-ingest-test
  (testing "ingest of a new concept"
    (let [concept (old-ingest/distinct-concept "PROV1" 0)
          {:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
      (is (ingest/concept-exists-in-mdb? concept-id revision-id))
      (is (= revision-id 0)))))

;; Verify a new concept with concept-id is ingested successfully.
(deftest collection-w-concept-id-ingest-test
  (testing "ingest of a new concept with concept-id present"
    (let [concept (old-ingest/distinct-concept-w-concept-id "PROV1" 7)
          supplied-concept-id (:concept-id concept)
          {:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
      (is (ingest/concept-exists-in-mdb? concept-id revision-id))
      (is (= supplied-concept-id concept-id))
      (is (= revision-id 0)))))

;; Ingest same concept N times and verify same concept-id is returned and
;; revision id is 1 greater on each subsequent ingest
(deftest repeat-same-collection-ingest-test
  (testing "ingest same concept n times ..."
    (let [n 4
          concept (old-ingest/distinct-concept "PROV1" 1)
          created-concepts (take n (repeatedly n #(ingest/ingest-concept concept)))]
      (is (apply = (map :concept-id created-concepts)))
      (is (= (range 0 n) (map :revision-id created-concepts))))))

;; Verify ingest behaves properly if empty body is presented in the request.
(deftest empty-collection-ingest-test
  (let [concept-with-empty-body  (assoc (old-ingest/distinct-concept "PROV1" 2) :metadata "")
        {:keys [status errors]} (ingest/ingest-concept concept-with-empty-body)]
    (is (= status 400))
    (is (re-find #"Invalid XML file." (first errors)))))

;; Verify non-existent concept deletion results in not found / 404 error.
(deftest delete-non-existent-collection-test
  (let [concept (old-ingest/distinct-concept "PROV1" 3)
        fake-provider-id (str (:provider-id concept) (:native-id concept))
        non-existent-concept (assoc concept :provider-id fake-provider-id)
        {:keys [status]} (ingest/delete-concept non-existent-concept)]
    (is (= status 404))))

;; Verify existing concept can be deleted and operation results in revision id 1 greater than
;; max revision id of the concept prior to the delete
(deftest delete-collection-test
  (let [concept (old-ingest/distinct-concept "PROV1" 3)
        ingest-result (ingest/ingest-concept concept)
        delete-result (ingest/delete-concept concept)
        ingest-revision-id (:revision-id ingest-result)
        delete-revision-id (:revision-id delete-result)]
    (is (= 1 (- delete-revision-id ingest-revision-id)))))

;; Verify ingest behaves properly if request is missing content type.
(deftest missing-content-type-ingest-test
  (let [concept-with-no-content-type  (assoc (old-ingest/distinct-concept "PROV1" 4) :content-type "")
        {:keys [status errors]} (ingest/ingest-concept concept-with-no-content-type)]
    (is (= status 400))
    (is (re-find #"Invalid content-type" (first errors)))))

;; Verify ingest behaves properly if request contains invalid  content type.
(deftest invalid-content-type-ingest-test
  (let [concept-with-no-content-type (assoc (old-ingest/distinct-concept "PROV1" 4) :content-type "blah")
        {:keys [status errors]} (ingest/ingest-concept concept-with-no-content-type)]
    (is (= status 400))
    (is (re-find #"Invalid content-type" (first errors)))))

;; Verify deleting same concept twice is not an error if ignore conflict is true.
(deftest delete-same-collection-twice-test
  (let [concept (old-ingest/distinct-concept "PROV1" 5)
        ingest-result (ingest/ingest-concept concept)
        delete1-result (ingest/delete-concept concept)
        delete2-result (ingest/delete-concept concept)]
    (is (= 200 (:status ingest-result)))
    (is (= 200 (:status delete1-result)))
    (is (= 200 (:status delete2-result)))))





