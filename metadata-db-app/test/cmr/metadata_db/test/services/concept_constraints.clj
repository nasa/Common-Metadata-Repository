(ns cmr.metadata-db.test.services.concept-constraints
  "Unit tests to verify post-commit concept constraints are enforced."
  (:require [clojure.test :refer :all]
            [cmr.metadata-db.services.concept-constraints :as cc]
            [cmr.metadata-db.data.memory-db :as mem-db]
            [cmr.metadata-db.data.concepts :as dc]
            [cmr.metadata-db.services.messages :as msg]))

(defn- make-coll-concept
  "Returns a collection concept based on the provided parameters."
  ([provider-id concept-id revision-id]
   (make-coll-concept provider-id concept-id revision-id {}))
  ([provider-id concept-id revision-id extra-fields]
   {:provider-id provider-id
    :concept-type :collection
    :concept-id concept-id
    :revision-id revision-id
    :deleted false
    :extra-fields extra-fields}))

(defn- make-coll-tombstone
  "Returns a collection tombstone based on the provided parameters."
  ([provider-id concept-id revision-id]
   (make-coll-tombstone provider-id concept-id revision-id {}))
  ([provider-id concept-id revision-id extra-fields]
   (assoc (make-coll-concept provider-id concept-id revision-id extra-fields)
          :deleted true)))

(defn- run-constraint
  "Populates the database with the provided existing-concepts then runs the provided constraint
  function against the concept that is passed in."
  [constraint test-concept & existing-concepts]
  (let [db (mem-db/create-db (cons test-concept existing-concepts))]
    (constraint db test-concept)))

(defn- assert-invalid
  "Runs the given constraint function against a concept with a list of existing concepts in the
  database and verifies that the expected error is returned."
  [error-msg constraint test-concept & existing-concepts]
  (is (= [error-msg]
         (apply run-constraint constraint test-concept existing-concepts))))

(defn- assert-valid
  "Runs the given constraint function against a concept with a list of existing concepts in the
  database and verifies that no error is returned."
  [constraint test-concept & existing-concepts]
  (is (nil? (apply run-constraint constraint test-concept existing-concepts))))

(deftest entry-title-unique-constraint-test
  (let [test-concept (make-coll-concept "PROV1" "C1-PROV1" 5 {:entry-title "ET1"})
        is-valid (partial assert-valid cc/entry-title-unique-constraint)
        not-valid #(apply assert-invalid %1 cc/entry-title-unique-constraint test-concept %2)]

    (testing "valid cases"
      (testing "with empty database"
        (is-valid test-concept))
      (testing "another collection with entry title that is deleted is valid"
        (let [other-tombstone (make-coll-tombstone "PROV1" "C2-PROV1" 2 {:entry-title "ET1"})]
          (is-valid test-concept other-tombstone)))
      (testing "another provider with the same entry title is valid "
        (let [other-concept (make-coll-concept "PROV2" "C1-PROV1" 5 {:entry-title "ET1"})]
          (is-valid test-concept other-concept)))
      (testing "same concept id but earlier revision id is valid"
        (let [other-concept (make-coll-concept "PROV1" "C1-PROV1" 4 {:entry-title "ET1"})]
          (is-valid test-concept other-concept)))
      (testing "different entry titles are valid"
        (let [other-concept (make-coll-concept "PROV1" "C1-PROV1" 5 {:entry-title "ET2"})]
          (is-valid test-concept other-concept)))
      (testing "multiple valid concepts are still valid"
        (is-valid test-concept
                  (make-coll-concept "PROV1" "C2-PROV1" 1 {:entry-title "ET1"})
                  (make-coll-tombstone "PROV1" "C2-PROV1" 2 {:entry-title "ET1"})
                  (make-coll-concept "PROV2" "C1-PROV1" 5 {:entry-title "ET1"})
                  (make-coll-concept "PROV1" "C1-PROV1" 4 {:entry-title "ET1"})
                  (make-coll-concept "PROV1" "C1-PROV1" 5 {:entry-title "ET2"}))))

    (testing "invalid cases"
      (testing "same entry title"
        (let [other-concept (make-coll-concept "PROV1" "C2-PROV1" 1 {:entry-title "ET1"})]
          (not-valid
            (msg/duplicate-entry-titles [test-concept other-concept])
            [other-concept])))
      (testing "cannot find saved concept returns internal error"
        (let [db (mem-db/create-db)]
          (is (thrown-with-msg?
                java.lang.Exception
                #"Unable to find saved concept by entry-title \[ET1\]"
                (cc/entry-title-unique-constraint db test-concept))))))))
