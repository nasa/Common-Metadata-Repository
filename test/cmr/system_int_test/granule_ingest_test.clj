(ns ^{:doc "CMR granule ingest integration tests"}
  cmr.system-int-test.granule-ingest-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.ingest-util :as ingest]
            [cmr.system-int-test.index-util :as index]
            [ring.util.io :as io]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [clojure.string :as string]
            [cmr.system-int-test.ingest-util :as util]))

;;; tests
;;; ensure metadata, indexer and ingest apps are accessable on ports 3001, 3004 and 3002 resp;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Verify a new granule is ingested successfully.
(deftest granule-ingest-test
  (testing "ingest of a new granule"
    (let [collection (util/collection-concept "PROV1" 5)
          _ (util/ingest-collection collection)
          granule (util/granule-concept "PROV1" "C1000000000-PROV1" 5)
          {:keys [concept-id revision-id]} (util/ingest-granule granule)]
      (is (util/concept-exists-in-mdb? concept-id revision-id))
      (is (= revision-id 0)))))

; Verify a new granule with concept-id is ingested successfully.
(deftest granule-w-concept-id-ingest-test
  (testing "ingest of a new granule with concept-id present"
    (let [collection (util/collection-concept "PROV1" 5)
          _ (util/ingest-collection collection)
          granule (util/granule-concept "PROV1" "C1000000000-PROV1" 5 "G1-PROV1")
          supplied-concept-id (:concept-id granule)
          {:keys [concept-id revision-id]} (util/ingest-granule granule)]
      (is (util/concept-exists-in-mdb? concept-id revision-id))
      (is (= supplied-concept-id concept-id))
      (is (= revision-id 0)))))

;; Ingest same granule N times and verify same concept-id is returned and
;; revision id is 1 greater on each subsequent ingest
(deftest repeat-same-granule-ingest-test
  (testing "ingest same granule n times ..."
    (let [collection (util/collection-concept "PROV1" 5)
          _ (util/ingest-collection collection)
          n 4
          granule (util/granule-concept "PROV1" "C1000000000-PROV1" 5 "G1-PROV1")
          created-granules (take n (repeatedly n #(util/ingest-granule granule)))]
      (is (apply = (map :concept-id created-granules)))
      (is (= (range 0 n) (map :revision-id created-granules))))))

;; Verify ingest behaves properly if empty body is presented in the request.
(deftest empty-granule-ingest-test
  (let [collection (util/collection-concept "PROV1" 5)
        _ (util/ingest-collection collection)
        granule-with-empty-body  (assoc (util/granule-concept "PROV1" "C1000000000-PROV1" 5 "G1-PROV1") :metadata "")
        {:keys [status errors-str]} (util/ingest-granule granule-with-empty-body)]
    (is (= status 400))
    (is (re-find #"Invalid XML file." errors-str))))

;; Verify non-existent granule deletion results in not found / 404 error.
(deftest delete-non-existent-granule-test
  (let [granule (util/granule-concept "PROV1" "C1000000000-PROV1" 5 "G1-PROV1")
        fake-provider-id (str (:provider-id granule) (:native-id granule))
        non-existent-granule (assoc granule :provider-id fake-provider-id)
        {:keys [status]} (util/delete-concept non-existent-granule)]
    (is (= status 404))))

;; Verify existing granule can be deleted and operation results in revision id 1 greater than
;; max revision id of the granule prior to the delete
(deftest delete-granule-test
  (let [collection (util/collection-concept "PROV1" 5)
        _ (util/ingest-collection collection)
        granule (util/granule-concept "PROV1" "C1000000000-PROV1" 5 "G1-PROV1")
        ingest-result (util/ingest-granule granule)
        delete-result (util/delete-concept granule)
        ingest-revision-id (:revision-id ingest-result)
        delete-revision-id (:revision-id delete-result)]
    (is (= 1 (- delete-revision-id ingest-revision-id)))))

;; Verify ingest behaves properly if request is missing content type.
(deftest missing-content-type-ingest-test
  (let [collection (util/collection-concept "PROV1" 5)
        _ (util/ingest-collection collection)
        granule-with-no-content-type  (assoc (util/granule-concept "PROV1" "C1000000000-PROV1" 5 "G1-PROV1") :content-type "")
        {:keys [status errors-str]} (util/ingest-granule granule-with-no-content-type)]
    (is (= status 400))
    (is (re-find #"Invalid content-type" errors-str))))

;; Verify ingest behaves properly if request contains invalid  content type.
(deftest invalid-content-type-ingest-test
  (let [collection (util/collection-concept "PROV1" 5)
        _ (util/ingest-collection collection)
        granule-with-no-content-type (assoc (util/granule-concept "PROV1" "C1000000000-PROV1" 5 "G1-PROV1") :content-type "blah")
        {:keys [status errors-str]} (util/ingest-granule granule-with-no-content-type)]
    (is (= status 400))
    (is (re-find #"Invalid content-type" errors-str))))

;; Verify deleting same granule twice is not an error if ignore conflict is true.
(deftest delete-same-granule-twice-test
  (let [collection (util/collection-concept "PROV1" 5)
        _ (util/ingest-collection collection)
        granule (util/granule-concept "PROV1" "C1000000000-PROV1" 5 "G1-PROV1")
        ingest-result (util/ingest-granule granule)
        delete1-result (util/delete-concept granule)
        delete2-result (util/delete-concept granule)]
    (is (= 200 (:status ingest-result)))
    (is (= 200 (:status delete1-result)))
    (is (= 200 (:status delete2-result)))))


;;; Fixtures

(use-fixtures :each (util/reset-fixture "PROV1"))




