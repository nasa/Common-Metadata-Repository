(ns cmr.metadata-db.int-test.force-delete-test
  "Contains integration test for emptying database via force-delete."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.metadata-db.int-test.utility :as util]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def num-revisions 3) ; number of times the first concept will be saved

(def concept1-id "C1000000000-PROV1")

(defn setup-database-fixture
  "Load the database with test data."
  [f]
  ;; setup database
  (let [concept1 (util/concept)]
    (dorun (repeatedly num-revisions #(util/save-concept concept1))))
  
  (f)
  
  ;; clear out the database
  (util/reset-database))

(use-fixtures :each setup-database-fixture)

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest mdb-force-delete-concept-test
  "Delete a concept and check the revision id of the return message."
  (let [{:keys [status revision-id]} (util/force-delete-concept concept1-id 1)]
    (is (= status 200))
    (is (= revision-id 1))))

(deftest mdb-force-delete-verify-gone
  "Delete a concept and make sure it is no longer available."
  (util/force-delete-concept concept1-id 1)
  (let [{:keys [status concept]} (util/get-concept-by-id-and-revision concept1-id 1)]
    (is (= status 404))))

(deftest mdb-force-delete-fail-to-delete-nonexistent-concept-revision
  "Verify we get a 404 when whe try to delete a concept revision that 
  does not exist."
  (let [{:keys [status revision-id]} (util/force-delete-concept concept1-id 10)]
    (is (= status 404))))

(deftest mdb-force-delete-fail-to-delete-nonexistent-concept-id
  "Verify we get a 404 when whe try to delete a concept with id that 
  does not exist."
  (let [{:keys [status revision-id]} (util/force-delete-concept "SOME-ID" 0)]
    (is (= status 404))))
  