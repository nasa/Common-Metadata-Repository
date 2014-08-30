(ns cmr.metadata-db.int-test.concepts.get-test
  "Contains integration tests for getting concepts. Tests gets with various
  configurations including checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.metadata-db.int-test.utility :as util]))

(use-fixtures :each (util/reset-database-fixture "PROV1"))

(defn verify
  [result]
  (if (= 201 (:status result))
    result
    (throw (ex-info "Failed to create concept" result))))

;; This needs more tests for granules
;; This also needs a test where it retrieves granules and collections at the same time.
;; It should also try a concept-id prefix that's not valid and a provider id that's invalid

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest get-test
  (let [concept1 (util/collection-concept "PROV1" 1)
        concept2 (assoc (util/collection-concept "PROV1" 2) :concept-id "C2-PROV1")
        {:keys [concept-id]} (last (for [n (range 3)]
                                     (verify (util/save-concept concept1))))
        concept-id2 (:concept-id (verify (util/save-concept concept2)))]

    (testing "get-concept-test"
      (testing "latest version"
        (let [{:keys [status concept]} (util/get-concept-by-id concept-id)]
          (is (= 200 status))
          (is (= 3 (:revision-id concept))))))

    (testing "get-non-existant-concept-test"
      (testing "Non existant collection id"
        (is (= 404 (:status (util/get-concept-by-id "C123-PROV1")))))
      (testing "Non existant provider id"
        (is (= 404 (:status (util/get-concept-by-id "C1000000000-PROV12"))))))

    (testing "get-concept-with-version-test"
      "Get a concept by concept-id and version-id."
      (let [{:keys [status concept]} (util/get-concept-by-id-and-revision concept-id 3)]
        (is (= status 200))
        (is (= (:revision-id concept) 3))))

    (testing "get-concept-invalid-concept-id-or-revision-test"
      "Expect a status 4XX if we try to get a concept that doesn't exist or use an improper concept-id."
      (testing "invalid concept-id"
        (let [{:keys [status]} (util/get-concept-by-id "bad id")]
          (is (= 400 status))))
      (testing "out of range revision-id"
        (let [concept (util/collection-concept "PROV1" 1)
              {:keys [status]} (util/get-concept-by-id-and-revision concept-id 10)]
          (is (= 404 status))))
      (testing "non-integer revision-id"
        (let [{:keys [status]}(util/get-concept-by-id-and-revision concept-id "NON-INTEGER")]
          (is (= 422 status)))))))