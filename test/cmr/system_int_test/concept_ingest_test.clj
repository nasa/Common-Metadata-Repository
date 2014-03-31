(ns ^{:doc "CMR Ingest integration tests"}
  cmr.system-int-test.concept-ingest-test
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

;; Verify a new concept is ingested successfully.
(deftest concept-ingest-test
  (testing "ingest of a new concept"
    (let [concept (util/distinct-concept 0)
          {:keys [concept-id revision-id]} (util/ingest-concept concept)]
      (is (util/concept-exists-in-mdb? concept-id revision-id))
      (is (= revision-id 0)))))

;; Verify a new concept with concept-id is ingested successfully.
(deftest concept-w-concept-id-ingest-test
  (testing "ingest of a new concept with concept-id present"
    (let [concept (util/distinct-concept-w-concept-id 7)
          supplied-concept-id (:concept-id concept)
          {:keys [concept-id revision-id]} (util/ingest-concept concept)]
      (is (util/concept-exists-in-mdb? concept-id revision-id))
      (is (= supplied-concept-id concept-id))
      (is (= revision-id 0)))))

;; Ingest same concept N times and verify same concept-id is returned and
;; revision id is 1 greater on each subsequent ingest
(deftest repeat-same-concept-ingest-test
  (testing "ingest same concept n times ..."
    (let [n 4
          concept (util/distinct-concept 1)
          created-concepts (take n (repeatedly n #(util/ingest-concept concept)))]
      (is (apply = (map :concept-id created-concepts)))
      (is (= (range 0 n) (map :revision-id created-concepts))))))

;; Verify ingest behaves properly if empty body is presented in the request.
(deftest empty-concept-ingest-test
  (let [concept-with-empty-body  (assoc (util/distinct-concept 2) :metadata "")
        {:keys [status errors-str]} (util/ingest-concept concept-with-empty-body)]
    (is (= status 400))
    (is (re-find #"Invalid XML file." errors-str))))

;; Verify non-existent concept deletion results in not found / 404 error.
(deftest delete-non-existent-concept-test
  (let [concept (util/distinct-concept 3)
        fake-provider-id (str (:provider-id concept) (:native-id concept))
        non-existent-concept (assoc concept :provider-id fake-provider-id)
        {:keys [status]} (util/delete-concept non-existent-concept)]
    (is (= status 404))))

;; Verify existing concept can be deleted and operation results in revision id 1 greater than
;; max revision id of the concept prior to the delete
(deftest delete-concept-test
  (let [concept (util/distinct-concept 3)
        ingest-result (util/ingest-concept concept)
        delete-result (util/delete-concept concept)
        ingest-revision-id (:revision-id ingest-result)
        delete-revision-id (:revision-id delete-result)]
    (is (= 1 (- delete-revision-id ingest-revision-id)))))

;; Verify ingest behaves properly if request is missing content type.
(deftest missing-content-type-ingest-test
  (let [concept-with-no-content-type  (assoc (util/distinct-concept 4) :content-type "")
        {:keys [status errors-str]} (util/ingest-concept concept-with-no-content-type)]
    (is (= status 400))
    (is (re-find #"Invalid content-type" errors-str))))

;; Verify ingest behaves properly if request contains invalid  content type.
(deftest invalid-content-type-ingest-test
  (let [concept-with-no-content-type (assoc (util/distinct-concept 4) :content-type "blah")
        {:keys [status errors-str]} (util/ingest-concept concept-with-no-content-type)]
    (is (= status 400))
    (is (re-find #"Invalid content-type" errors-str))))

;; Verify deleting same concept twice is not an error if ignore conflict is true.
(deftest delete-same-concept-twice-test
  (let [concept (util/distinct-concept 5)
        ingest-result (util/ingest-concept concept)
        delete1-result (util/delete-concept concept)
        delete2-result (util/delete-concept concept)]
    (is (= 200 (:status ingest-result)))
    (is (= 200 (:status delete1-result)))
    (is (= 200 (:status delete2-result)))))

;;; fixture - each test to call this fixture
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn setup [] (util/reset-database) (util/reset-es-indexes))
(defn teardown [] (util/reset-database))

(defn each-fixture [f]
  (setup)
  (f)
  (teardown))

(use-fixtures :each each-fixture)



