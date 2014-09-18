(ns ^{:doc "CMR granule ingest integration tests"}
  cmr.system-int-test.ingest.granule-ingest-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.index-util :as index]
            [ring.util.io :as io]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [clojure.string :as string]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.old-ingest-util :as old-ingest]
            ))


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

;;; Verify a new granule is ingested successfully.
(deftest granule-ingest-test
  (testing "ingest of a new granule"
    (let [collection (old-ingest/collection-concept "PROV1" 5)
          _ (ingest/ingest-concept collection)
          granule (old-ingest/granule-concept "PROV1" "C1000000000-PROV1" 5)
          {:keys [concept-id revision-id]} (ingest/ingest-concept granule)]
      (is (ingest/concept-exists-in-mdb? concept-id revision-id))
      (is (= 1 revision-id)))))

; Verify a new granule with concept-id is ingested successfully.
(deftest granule-w-concept-id-ingest-test
  (testing "ingest of a new granule with concept-id present"
    (let [collection (old-ingest/collection-concept "PROV1" 5)
          _ (ingest/ingest-concept collection)
          granule (old-ingest/granule-concept "PROV1" "C1000000000-PROV1" 5 "G1-PROV1")
          supplied-concept-id (:concept-id granule)
          {:keys [concept-id revision-id]} (ingest/ingest-concept granule)]
      (is (ingest/concept-exists-in-mdb? concept-id revision-id))
      (is (= supplied-concept-id concept-id))
      (is (= 1 revision-id)))))

;;; Ingest same granule N times and verify same concept-id is returned and
;;; revision id is 1 greater on each subsequent ingest
(deftest repeat-same-granule-ingest-test
  (testing "ingest same granule n times ..."
    (let [collection (old-ingest/collection-concept "PROV1" 5)
          _ (ingest/ingest-concept collection)
          n 4
          granule (old-ingest/granule-concept "PROV1" "C1000000000-PROV1" 5 "G1-PROV1")
          created-granules (take n (repeatedly n #(ingest/ingest-concept granule)))]
      (is (apply = (map :concept-id created-granules)))
      (is (= (range 1 (inc n)) (map :revision-id created-granules))))))

;;; Verify ingest behaves properly if empty body is presented in the request.
(deftest empty-granule-ingest-test
  (let [collection (old-ingest/collection-concept "PROV1" 5)
        _ (ingest/ingest-concept collection)
        granule-with-empty-body  (assoc (old-ingest/granule-concept "PROV1" "C1000000000-PROV1" 5 "G1-PROV1") :metadata "")
        {:keys [status errors]} (ingest/ingest-concept granule-with-empty-body)]
    (is (= 400 status))
    (is (re-find #"XML content is too short." (first errors)))))

;;; Verify non-existent granule deletion results in not found / 404 error.
(deftest delete-non-existent-granule-test
  (let [granule (old-ingest/granule-concept "PROV1" "C1000000000-PROV1" 5 "G1-PROV1")
        fake-provider-id (str (:provider-id granule) (:native-id granule))
        non-existent-granule (assoc granule :provider-id fake-provider-id)
        {:keys [status]} (ingest/delete-concept non-existent-granule)]
    (is (= 404 status))))

;;; Verify existing granule can be deleted and operation results in revision id 1 greater than
;;; max revision id of the granule prior to the delete
(deftest delete-granule-test
  (let [collection (old-ingest/collection-concept "PROV1" 5)
        _ (ingest/ingest-concept collection)
        granule (old-ingest/granule-concept "PROV1" "C1000000000-PROV1" 5 "G1-PROV1")
        ingest-result (ingest/ingest-concept granule)
        delete-result (ingest/delete-concept granule)
        ingest-revision-id (:revision-id ingest-result)
        delete-revision-id (:revision-id delete-result)]
    (is (= 1 (- delete-revision-id ingest-revision-id)))))

;;; Verify ingest behaves properly if request is missing content type.
(deftest missing-content-type-ingest-test
  (let [collection (old-ingest/collection-concept "PROV1" 5)
        _ (ingest/ingest-concept collection)
        granule-with-no-content-type  (assoc (old-ingest/granule-concept "PROV1" "C1000000000-PROV1" 5 "G1-PROV1") :format "")
        {:keys [status errors]} (ingest/ingest-concept granule-with-no-content-type)]
    (is (= 400 status))
    (is (re-find #"Invalid content-type" (first errors)))))

;;; Verify ingest behaves properly if request contains invalid  content type.
(deftest invalid-content-type-ingest-test
  (let [collection (old-ingest/collection-concept "PROV1" 5)
        _ (ingest/ingest-concept collection)
        granule-with-no-content-type (assoc (old-ingest/granule-concept "PROV1" "C1000000000-PROV1" 5 "G1-PROV1") :format "blah")
        {:keys [status errors]} (ingest/ingest-concept granule-with-no-content-type)]
    (is (= 400 status))
    (is (re-find #"Invalid content-type" (first errors)))))

;;; Verify deleting same granule twice is not an error if ignore conflict is true.
(deftest delete-same-granule-twice-test
  (let [collection (old-ingest/collection-concept "PROV1" 5)
        _ (ingest/ingest-concept collection)
        granule (old-ingest/granule-concept "PROV1" nil 5 "G1-PROV1")
        ingest-result (ingest/ingest-concept granule)
        delete1-result (ingest/delete-concept granule)
        delete2-result (ingest/delete-concept granule)]
    (is (= 200 (:status ingest-result)))
    (is (= 200 (:status delete1-result)))
    (is (= 200 (:status delete2-result)))))

;;; Verify that attempts to ingest a granule whose parent does not exist result in a 404
(deftest ingest-orphan-granule-test
  (let [granule (old-ingest/granule-concept "PROV1" "C1000000000-PROV1" 5 "G1-PROV1")
        {:keys [status]} (ingest/ingest-concept granule)]
    (is (= 404 status))
    (is (not (ingest/concept-exists-in-mdb? "G1-PROV1" 0)))))

;;; Verify that granules with embedded / (%2F) in the native-id are handled correctly
(deftest ingest-granule-with-slash-in-native-id-test
  (let [collection (old-ingest/collection-concept "PROV1" 5)
        _ (ingest/ingest-concept collection)
        granule {:concept-type :granule
                 :native-id "Name/With/Slashes"
                 :provider-id "PROV1"
                 :metadata (old-ingest/granule-xml old-ingest/base-concept-attribs)
                 :format "application/echo10+xml"
                 :deleted false
                 :extra-fields {:parent-collection-id "C1000000000-PROV1"}}
        {:keys [concept-id revision-id] :as response} (ingest/ingest-concept granule)
        ingested-concept (ingest/get-concept concept-id)]
    (is (= 200 (:status response)))
    (is (ingest/concept-exists-in-mdb? concept-id revision-id))
    (is (= 1 revision-id))
    (is (= "Name/With/Slashes" (:native-id ingested-concept)))))


