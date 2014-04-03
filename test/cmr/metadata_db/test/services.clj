(ns cmr.metadata-db.test.services
  "Contains unit tests for service layer methods and associated utility methods."
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [cmr.metadata-db.services.utility :as util]
            [cmr.metadata-db.services.concept-services :as cs]
            [cmr.metadata-db.test.memory-db :as memory]))

(deftest check-concept-revision-id-test
  "Verify that the revision id check works as expected."
  (let [db (memory/create-db nil)
        previous-concept memory/test-concept]
    (testing
      ;; valid
      (let [concept (assoc previous-concept :revision-id 1)]
        (is (= {:status :pass} (cs/check-concept-revision-id db concept previous-concept)))))
    (testing
      ;; invalid - high
      (let [previous-concept memory/test-concept
            concept (assoc previous-concept :revision-id 2)
            result (cs/check-concept-revision-id db concept previous-concept)]
        (is (= (:status result) :fail))
        (is (= (:expected result) 1))))
    (testing
      ;; invalid - low
      (let [previous-concept memory/test-concept
            concept (assoc previous-concept :revision-id 0)
            result (cs/check-concept-revision-id db concept previous-concept)]
        (is (= (:status result) :fail))
        (is (= (:expected result) 1))))))

(deftest validate-concept-revision-id-test
  "Verify that the revision id validation works as expected."
  (let [db (memory/create-db nil)]
    (testing
      ;; valid
      (let [previous-concept memory/test-concept
            concept (assoc previous-concept :revision-id 1)]
        (cs/validate-concept-revision-id db concept previous-concept)))
    (testing
      ;; invalid
      (let [previous-concept memory/test-concept
            concept (assoc previous-concept :revision-id 2)]
        (is (thrown? Exception (cs/validate-concept-revision-id db concept previous-concept)))))))

(deftest try-to-save-test
  "Verify that the try-to-save logic is correct."
  (let [db (memory/create-db nil)]
    (testing
      ;; valid no revision-id
      (let [result (cs/try-to-save db (dissoc memory/test-concept :revision-id) false)]
        (is (= (:revision-id result) 1))))
    (testing
      ;; valid with revision-id
      (let [result (cs/try-to-save db (assoc memory/test-concept :revision-id 1) true)]
        (is (= (:revision-id result) 1))))
    (testing
      ;; invalid with low revision-id
      (is (thrown? Exception (cs/try-to-save db (assoc memory/test-concept :revision-id 0) true))))
    (testing
      ;; invalid with high revision-id
      (is (thrown? Exception (cs/try-to-save db (assoc memory/test-concept :revision-id 10) true))))))