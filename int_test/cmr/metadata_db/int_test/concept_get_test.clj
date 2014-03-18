(ns cmr.metadata-db.int-test.concept-get-test
  "Contains integration tests for getting concepts. Tests gets with various 
  configurations including checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.metadata-db.int-test.utility :as util]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn setup-database-fixture
  "Load the database with test data."
  [f]
  ;; setup database
  (let [concept1 (util/concept)
        concept2 (assoc concept1 :concept-id "C2-PROV1")]
    ;; save a concept
    (util/save-concept concept1)
    ;; save a revision
    (util/save-concept concept1)
    ;; save it a third time
    (util/save-concept concept1)
   	;; save another concept
    (util/save-concept concept2))
  
  (f)
  
  ;; clear out the database
  (util/reset-database))


(use-fixtures :once setup-database-fixture)

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest mdb-get-concept-test
  "Get the latest version of a concept by concept-id."
  (let [{:keys [status concept]} (util/get-concept-by-id (:concept-id (util/concept)))]
    (is (and (= status 200) (= (:revision-id concept) 2)))))

(deftest mdb-get-concept-with-version-test
  "Get a concept by concept-id and version-id."
  (let [{:keys [status concept]} (util/get-concept-by-id-and-revision (:concept-id (util/concept)) 1)]
    (is (and (= status 200) (= (:revision-id concept) 1)))))

(deftest mdb-get-concept-invalid-concept-id-or-revision-test
  "Expect a status 4XX if we try to get a concept that doesn't exist or use an improper concept-id."
  (testing "invalid concept-id"
    (let [{:keys [status]} (util/get-concept-by-id "bad id")]
      (is (= 404 status))))
  (testing "out of range revision-id"
    (let [concept (util/concept)
          {:keys [status]} (util/get-concept-by-id-and-revision (:concept-id concept) 10)]
      (is (= 404 status))))
  (testing "non-integer revision-id"
    (let [concept (util/concept)
          {:keys [status]}(util/get-concept-by-id-and-revision (:concept-id concept) "NON-INTEGER")]
      (is (= 422 status)))))

(deftest mdb-get-concepts-test
  "Get concepts by specifying tuples of concept-ids and revision-ids."
  (let [concept1 (util/concept)
        concept2 (assoc concept1 :concept-id "C2-PROV1")
        tuples [[(:concept-id concept1) 1] [(:concept-id concept2) 0]]
        results (util/get-concepts tuples)
        returned-concepts (:concepts results)
        status (:status results)
        expected [(:concept-id concept1) (:concept-id concept2)]]
    (is (util/concepts-and-ids-equal? returned-concepts expected))
    (is (and (= status 200) 
             (util/concepts-and-ids-equal? returned-concepts expected)))))


    