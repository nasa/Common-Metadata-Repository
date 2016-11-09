(ns cmr.metadata-db.test.services.concept-constraints
  "Unit tests to verify post-commit concept constraints are enforced."
  (:require [clojure.test :refer :all]
            [cmr.metadata-db.services.concept-constraints :as cc]
            [cmr.metadata-db.data.memory-db :as mem-db]
            [cmr.metadata-db.services.messages :as msg]))

(defn- make-concept
  "Returns a concept based on the provided parameters."
  ([concept-type provider-id concept-id revision-id extra-fields]
   (make-concept concept-type provider-id concept-id revision-id nil extra-fields))
  ([concept-type provider-id concept-id revision-id native-id extra-fields]
   {:provider-id provider-id
    :concept-type concept-type
    :concept-id concept-id
    :revision-id revision-id
    :native-id native-id
    :deleted false
    :extra-fields extra-fields}))

(defn- make-tombstone
  "Returns a collection tombstone based on the provided parameters."
  [concept-type provider-id concept-id revision-id extra-fields]
  (assoc (make-concept concept-type provider-id concept-id revision-id extra-fields)
         :deleted true))

(defn- run-constraint
  "Populates the database with the provided existing-concepts then runs the provided constraint
  function against the concept that is passed in."
  [constraint test-concept & existing-concepts]
  (let [db (mem-db/create-db (cons test-concept existing-concepts))]
    (constraint db {:provider-id "PROV1"} test-concept)))

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

(defn- unique-constraint-test
  "Runs the given unique constraint test. Takes the following parameters:
  concept-type - :collection or :granule
  constraint-fn - uniqueness constraint function being tested
  field - name of the field which must be unique (e.g. :entry-title, :entry-id, or :granule-ur)"
  [concept-type constraint-fn field]
  (let [extra-fields {field "unique-field1"}
        test-concept (make-concept concept-type "PROV1" "C1-PROV1" 5 extra-fields)
        is-valid (partial assert-valid constraint-fn)
        not-valid #(apply assert-invalid %1 constraint-fn test-concept %2)]

    (testing "valid cases"
      (testing "with empty database"
        (is-valid test-concept))
      (testing (format "another collection with %s that is deleted is valid" field)
        (let [other-tombstone (make-tombstone concept-type "PROV1" "C2-PROV1" 2 extra-fields)]
          (is-valid test-concept other-tombstone)))
      (testing (format "another provider with the same %s is valid " field)
        (let [other-concept (make-concept concept-type "PROV2" "C1-PROV1" 5 extra-fields)]
          (is-valid test-concept other-concept)))
      (testing "same concept id but earlier revision id is valid"
        (let [other-concept (make-concept concept-type "PROV1" "C1-PROV1" 4 extra-fields)]
          (is-valid test-concept other-concept)))
      (testing (format "different values for %s are valid" field)
        (let [other-concept (make-concept concept-type "PROV1" "C1-PROV1" 5 {field "unique-field2"})]
          (is-valid test-concept other-concept)))
      (testing "multiple valid concepts are still valid"
        (is-valid test-concept
                  (make-concept concept-type "PROV1" "C2-PROV1" 1 extra-fields)
                  (make-tombstone concept-type "PROV1" "C2-PROV1" 2 extra-fields)
                  (make-concept concept-type "PROV2" "C1-PROV1" 5 extra-fields)
                  (make-concept concept-type "PROV1" "C1-PROV1" 4 extra-fields)
                  (make-concept concept-type "PROV1" "C1-PROV1" 5 {field "unique-field2"}))))

    (testing "invalid cases"
      (testing (format "same %s" field)
        (let [other-concept (make-concept concept-type "PROV1" "C2-PROV1" 1 extra-fields)]
          (not-valid
            (msg/duplicate-field-msg field [other-concept])
            [other-concept]))))))

(deftest unique-constraint-tests
  (unique-constraint-test :collection (cc/unique-field-constraint :entry-title) :entry-title)
  (unique-constraint-test :collection (cc/unique-field-constraint :entry-id) :entry-id)
  (unique-constraint-test :granule (cc/unique-field-constraint :granule-ur) :granule-ur)
  (unique-constraint-test :granule cc/granule-ur-unique-constraint :granule-ur))

(deftest granule-ur-unique-constraint-test
  (testing "native-id is checked for uniqueness when granule-ur is null"
    (let [test-concept (make-concept :granule "PROV1" "C1-PROV1" 5 {:granule-ur "G_UR-1"})
          not-valid #(apply assert-invalid %1 cc/granule-ur-unique-constraint test-concept %2)
          other-concept (make-concept :granule "PROV1" "C2-PROV1" 1 "G_UR-1" {})]
      (not-valid
        (msg/duplicate-field-msg :granule-ur
                                 [(assoc-in other-concept [:extra-fields :granule-ur]
                                            (get-in test-concept [:extra-fields :granule-ur]))])
        [other-concept]))))


(comment
  (cmr.metadata-db.int-test.utility/find-concepts :collection
                                                  {:entry-id "EID-1" :provider-id "PROV1"}))
