(ns cmr.metadata-db.test.services
  "Contains unit tests for service layer methods and associated utility methods."
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [cmr.metadata-db.services.utility :as util]
            [cmr.metadata-db.services.concept-services :as cs]
            [cmr.metadata-db.test.memory-db :as memory]
            [cmr.metadata-db.services.messages :as messages])
  (import clojure.lang.ExceptionInfo))


(def db nil) ; in-memory db

;;; fixtures

(defn setup-database-fixture
  "Create the in-memory db for tests."
  [f]
  ;; setup database
  (def db (memory/create-db nil))
  
  (f))

(use-fixtures :once setup-database-fixture)

;;; Verify that the revision id check works as expected.
(deftest check-concept-revision-id-test
  (let [previous-concept memory/test-concept]
    (testing "valid revision-id"
      (let [concept (assoc previous-concept :revision-id 1)]
        (is (= {:status :pass} (cs/check-concept-revision-id db concept previous-concept)))))
    (testing "invalid revision-id - high"
      (let [concept (assoc previous-concept :revision-id 2)
            result (cs/check-concept-revision-id db concept previous-concept)]
        (is (= (:status result) :fail))
        (is (= (:expected result) 1))))
    (testing "invalid revision-id - low"
      (let [concept (assoc previous-concept :revision-id 0)
            result (cs/check-concept-revision-id db concept previous-concept)]
        (is (= (:status result) :fail))
        (is (= (:expected result) 1))))))

;;; Verify that the revision id validation works as expected.
(deftest validate-concept-revision-id-test
  (let [previous-concept memory/test-concept]
    (testing "valid concept revision-id"
      (let [concept (assoc previous-concept :revision-id 1)]
        (cs/validate-concept-revision-id db concept previous-concept)))
    (testing "invalid concept revision-id"
      (let [concept (assoc previous-concept :revision-id 2)]
        (is (thrown-with-msg? ExceptionInfo (re-pattern (messages/invalid-revision-id-msg 1 2))
                              (cs/validate-concept-revision-id db concept previous-concept)))))
    (testing "missing concept-id no revision-id"
      (let [concept (dissoc previous-concept :concept-id)]
        (cs/validate-concept-revision-id db concept previous-concept)))
    (testing "missing concept-id valid revision-id"
      (let [concept (-> previous-concept (dissoc :concept-id) (assoc :revision-id 0))]
        (cs/validate-concept-revision-id db concept previous-concept)))
    (testing "missing concept-id invalid revision-id"
      (let [concept (-> previous-concept (dissoc :concept-id) (assoc :revision-id 1))]
        (is (thrown-with-msg? ExceptionInfo (re-pattern (messages/invalid-revision-id-msg 0 1))
                              (cs/validate-concept-revision-id db concept previous-concept)))))))

;;; Verify that the try-to-save logic is correct.
(deftest try-to-save-test
  (testing "valid no revision-id"
    (let [result (cs/try-to-save db (dissoc memory/test-concept :revision-id) nil)]
      (is (= (:revision-id result) 1))))
  (testing "valid with revision-id"
    (let [result (cs/try-to-save db (assoc memory/test-concept :revision-id 1) 1)]
      (is (= (:revision-id result) 1))))
  (testing "invalid with low revision-id"
    (is (thrown-with-msg? ExceptionInfo (re-pattern (messages/invalid-revision-id-unknown-expected-msg 0)) 
                          (cs/try-to-save db (assoc memory/test-concept :revision-id 0) 0))))
  (testing "invalid with high revision-id"
    (is (thrown-with-msg? ExceptionInfo (re-pattern (messages/invalid-revision-id-unknown-expected-msg 10)) 
                          (cs/try-to-save db (assoc memory/test-concept :revision-id 10) 10)))))



