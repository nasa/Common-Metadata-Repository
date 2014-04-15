(ns cmr.metadata-db.int-test.concepts.delete-test
  "Contains integration tests for deleting concepts. Tests delete with various configurations including
  checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.metadata-db.services.messages :as messages]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def num-revisions 3) ; number of times the first concept will be saved

(def concept1-id "C1000000000-PROV1")

(defn setup-database-fixture
  "Load the database with test data."
  [f]
  ;; setup database
  (util/save-provider "PROV1")
  (let [concept1 (util/collection-concept "PROV1" 1)
        concept2 (assoc (util/collection-concept "PROV1" 2) :concept-id "C2-PROV1")]
    (dorun (repeatedly num-revisions #(util/save-concept concept1)))

    (util/save-concept concept2))

  (f)

  ;; clear out the database
  (util/reset-database))

(use-fixtures :each setup-database-fixture)

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest delete-concept-test
  (let [{:keys [status revision-id]} (util/delete-concept concept1-id)]
    (is (= status 200))
    (is (= revision-id num-revisions))))

(deftest delete-concept-with-valid-revision
  (let [{:keys [status revision-id]} (util/delete-concept concept1-id num-revisions)]
    (is (= status 200))
    (is (= revision-id num-revisions))))

(deftest delete-concept-with-invalid-revision
  (let [{:keys [status]} (util/delete-concept concept1-id (+ num-revisions 10))]
    (is (= status 409))))

(deftest fail-to-delete-missing-concept
  (let [{:keys [status revision-id error-messages]} (util/delete-concept "C100-PROV1")]
    (is (= status 404))
    (is (= error-messages [(messages/concept-does-not-exist "C100-PROV1")]))))

(deftest fail-to-delete-missing-concept-for-missing-provider
  (let [{:keys [status revision-id error-messages]} (util/delete-concept "C100-NONEXIST")]
    (is (= status 404))
    (is (= error-messages [(messages/providers-do-not-exist ["NONEXIST"])]))))

(deftest repeated-calls-to-delete-get-same-revision
  (let [concept-id concept1-id
        tombstone-revision-id (:revision-id (util/delete-concept concept-id))]
    (dorun (repeatedly 3 #(util/delete-concept concept-id)))
    (let [final-revision-id (:revision-id (util/delete-concept concept-id))]
      (is (= tombstone-revision-id final-revision-id)))))
