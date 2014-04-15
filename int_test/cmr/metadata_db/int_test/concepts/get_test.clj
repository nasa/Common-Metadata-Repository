(ns cmr.metadata-db.int-test.concepts.get-test
  "Contains integration tests for getting concepts. Tests gets with various
  configurations including checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.metadata-db.int-test.utility :as util]))

(def concept1 (util/collection-concept "PROV1" 1))
(def concept2 (assoc (util/collection-concept "PROV1" 2) :concept-id "C2-PROV1"))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn setup-database-fixture
  "Load the database with test data."
  [f]

  ;; clear out the database
  (util/reset-database)
  ;; setup database
  (util/save-provider "PROV1")
  (let [verify #(when-not (= 201 (:status %))
                  (throw (ex-info "Failed to create concept" %)))]
    ;; save a concept
    (verify (util/save-concept concept1))
    ;; save a revision
    (verify (util/save-concept concept1))
    ;; save it a third time
    (verify (util/save-concept concept1))
   	;; save another concept
    (verify (util/save-concept concept2)))

  (f)

  ;; clear out the database
  (util/reset-database))


(use-fixtures :once setup-database-fixture)

;; TODO this needs more tests for granules
;; This also needs a test where it retrieves granules and collections at the same time.
;; It should also try a concept-id prefix that's not valid and a provider id that's invalid

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest get-concept-test
  (testing "latest version"
    (let [{:keys [status concept]} (util/get-concept-by-id "C1000000000-PROV1")]
      (is (= 200 status))
      (is (= 2 (:revision-id concept))))))

(deftest get-non-existant-concept-test
  (testing "Non existant collection id"
    (is (= 404 (:status (util/get-concept-by-id "C123-PROV1")))))
  (testing "Non existant provider id"
    (is (= 404 (:status (util/get-concept-by-id "C1000000000-PROV12"))))))

(deftest get-concept-with-version-test
  "Get a concept by concept-id and version-id."
  (let [{:keys [status concept]} (util/get-concept-by-id-and-revision "C1000000000-PROV1" 1)]
    (is (= status 200))
    (is (= (:revision-id concept) 1))))

(deftest get-concept-invalid-concept-id-or-revision-test
  "Expect a status 4XX if we try to get a concept that doesn't exist or use an improper concept-id."
  (testing "invalid concept-id"
    (let [{:keys [status]} (util/get-concept-by-id "bad id")]
      (is (= 400 status))))
  (testing "out of range revision-id"
    (let [concept (util/collection-concept "PROV1" 1)
          {:keys [status]} (util/get-concept-by-id-and-revision "C1000000000-PROV1" 10)]
      (is (= 404 status))))
  (testing "non-integer revision-id"
    (let [{:keys [status]}(util/get-concept-by-id-and-revision "C1000000000-PROV1" "NON-INTEGER")]
      (is (= 422 status)))))

(deftest get-concepts-test
  "Get concepts by specifying tuples of concept-ids and revision-ids."
  (let [tuples [["C1000000000-PROV1" 1] ["C2-PROV1" 0]]
        results (util/get-concepts tuples)
        returned-concepts (:concepts results)
        status (:status results)]
    (is (util/concepts-and-concept-id-revisions-equal? returned-concepts tuples))
    (is (= status 200))))

(deftest get-concepts-with-one-invalid-revision-id-test
  "Get concepts by specifying tuples of concept-ids and revision-ids with one invalid revision id
  and only get back existing concepts."
  (let [tuples [["C1000000000-PROV1" 1] ["C2-PROV1" 10]]
        results (util/get-concepts tuples)
        returned-concepts (:concepts results)
        status (:status results)
        expected [["C1000000000-PROV1" 1]]]
    (is (util/concepts-and-concept-id-revisions-equal? returned-concepts expected))
    (is (= status 200))))
