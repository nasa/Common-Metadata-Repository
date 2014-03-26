(ns ^{:doc "CMR Ingest integration tests"}
  cmr-system-int-test.concept-ingest-test
  (:require [clojure.test :refer :all]
            [cmr-system-int-test.ingest-util :as ingest]
            [cmr-system-int-test.index-util :as index]
            [ring.util.io :as io]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [clojure.string :as string]
            [cmr-system-int-test.ingest-util :as util]))


(def test-concept-list (doall (map util/distinct-concept (range 886700 886705))))
(def test-concept-w-concept-id (util/distinct-concept-w-concept-id 886706))
(def test-concept-w-concept-id-rev-id (assoc (util/distinct-concept-w-concept-id 886707)
                                             :revision-id 33))
(def test-concept-w-rev-id (assoc (util/distinct-concept 886708)
                                  :revision-id 34))

;;; tests
;;; ensure metadata, indexer and ingest apps are accessable on ports 3001, 3004 and 3002 resp; 
;;; also ensure zipkip is in place.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Verify a new concept is ingested successfully.
(deftest concept-ingest-test
  (let [concept (nth test-concept-list 0)
        {:keys [status concept-id revision-id]} (util/ingest-concept concept)
        concept-exists-in-mdb (util/concept-exists? concept-id revision-id)]
    ;; need to verify these three conditions and existence in oracle/ elastic
    ;; to be confident about new concept creation.   
    (is (and concept-exists-in-mdb (= status 200) (= revision-id 0)))))

;; Verify a new concept with concept-id is ingested successfully.
(deftest concept-w-concept-id-ingest-test
  (let [concept test-concept-w-concept-id 
        {:keys [status concept-id revision-id]} (util/ingest-concept concept)
        concept-exists-in-mdb (util/concept-exists? concept-id revision-id)]  
    (is (and concept-exists-in-mdb (= status 200) (= revision-id 0)))))

;; Verify a new concept with concept-id and revision id is ingested successfully.
(deftest concept-w-ids-ingest-test
  (let [concept test-concept-w-concept-id-rev-id
        {:keys [status concept-id revision-id]} (util/ingest-concept concept)
        concept-exists-in-mdb (util/concept-exists? concept-id revision-id)]  
    (is (and concept-exists-in-mdb (= status 200) (= revision-id 0)))))


;; Ingest same concept N times and verify same concept-id is returned and
;; revision id is 1 greater on each subsequent ingest
(deftest repeat-same-concept-ingest-test
  (let [n 4
        concept (nth test-concept-list 1)
        created-concepts (take n (repeatedly n #(util/ingest-concept concept)))]
    (is (and (apply = (map :concept-id created-concepts)) 
             (= (range 0 n) (map :revision-id created-concepts))))))

;; Verify ingest behaves properly if empty body is presented in the request.
(deftest empty-concept-ingest-test
  (let [concept-with-empty-body  (assoc (last test-concept-list) :metadata "aa") 
        {:keys [status concept-id revision-id]} (util/ingest-concept concept-with-empty-body)]
    (is (= status 400))))

;; Verify non-existent concept deletion results in not found / 404 error.
(deftest delete-non-existent-concept-test
  (let [fake-provider-id (clojure.string/join (map :provider-id test-concept-list))
        non-existent-concept (assoc (last test-concept-list) :provider-id fake-provider-id)
        {:keys [status]} (util/delete-concept non-existent-concept)]
    (is (= status 404))))

;; Verify existing concept can be deleted and operation results in revision id 1 greater than 
;; max revision id of the concept prior to the delete
(deftest delete-concept-test
  (let [concept (nth test-concept-list 2)
        ingest-result (util/ingest-concept concept)
        delete-result (util/delete-concept concept)
        ingest-revision-id (:revision-id ingest-result)
        delete-revision-id (:revision-id delete-result)]
    (is (= 1 (- delete-revision-id ingest-revision-id)))))


;; Verify ingest behaves properly if request is missing content type.
(deftest missing-content-type-ingest-test
  (let [concept-with-no-content-type  (assoc (last test-concept-list) :format "") 
        {:keys [status concept-id revision-id]} (util/ingest-concept concept-with-no-content-type)]
    (is (= status 400))))

;; Verify ingest behaves properly if request contains invalid  content type.
(deftest invalid-content-type-ingest-test
  (let [concept-with-no-content-type (assoc (last test-concept-list) :format "blah") 
        {:keys [status concept-id revision-id]} (util/ingest-concept concept-with-no-content-type)]
    (is (= status 400))))

;; Verify deleting same concept twice results in error.
;; why 409 error suppressed by indexer ?? Verify this later.
(deftest delete-same-concept-twice-test
    (let [concept (nth test-concept-list 3)
          ingest-result (util/ingest-concept concept)
          delete1-result (util/delete-concept concept)
          delete2-result (util/delete-concept concept)]
      (is (and (= 200 (:status ingest-result))  
               (= 200 (:status delete1-result)) 
               (some #{409 500} [(:status delete2-result)])))))

;;; fixture - each test to call this fixture
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn setup [] (util/reset-database))  
(defn teardown [] (util/reset-database) (util/reset-es-indexes)) 

(defn each-fixture [f]
  (setup)
  (f)
  (teardown))

(use-fixtures :each each-fixture)


