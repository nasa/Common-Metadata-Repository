(ns cmr.metadata-db.test.services.concept-constraints
  "Unit tests to verify post-commit concept constraints are enforced."
  (:require [clojure.test :refer :all]
            [cmr.metadata-db.services.concept-constraints :as cc]
            [cmr.metadata-db.data.memory-db :as mem-db]
            [cmr.metadata-db.data.concepts :as dc]
            [cmr.metadata-db.services.messages :as msg]))

(defn make-coll-concept
  ([provider-id concept-id revision-id]
   (make-coll-concept provider-id concept-id revision-id {}))
  ([provider-id concept-id revision-id extra-fields]
   {:provider-id provider-id
    :concept-type :collection
    :concept-id concept-id
    :revision-id revision-id
    :deleted false
    :extra-fields extra-fields}))

(defn make-coll-tombstone
  ([provider-id concept-id revision-id]
   (make-coll-tombstone provider-id concept-id revision-id {}))
  ([provider-id concept-id revision-id extra-fields]
   (assoc (make-coll-concept provider-id concept-id revision-id extra-fields)
          :deleted true)))


;; TODO these example concepts are wrong. The entry title is in extra-fields map
(def invalid-concepts
  [{:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C1" :revision-id 1 :entry-title "E1" :deleted false}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C1" :revision-id 2 :entry-title "E1" :deleted false}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C2" :revision-id 1 :entry-title "E1" :deleted false}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C2" :revision-id 2 :entry-title "E1" :deleted true}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C3" :revision-id 1 :entry-title "E1" :deleted false}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C4" :revision-id 1 :entry-title "E1" :deleted false}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C4" :revision-id 2 :entry-title "E1" :deleted true}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C4" :revision-id 20 :entry-title "E1" :deleted false}])

(def valid-concepts
  [{:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C1" :revision-id 1 :entry-title "E1" :deleted false}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C1" :revision-id 2 :entry-title "E1" :deleted true}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C2" :revision-id 1 :entry-title "E1" :deleted false}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C2" :revision-id 2 :entry-title "E1" :deleted true}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C3" :revision-id 1 :entry-title "E1" :deleted false}
   ;; Use this in integration test to make sure entry-title is successfully filtered on
   ; {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
   ; :concept-id "C4" :revision-id 1 :entry-title "E2" :deleted 0}
   ])

;; TODO change this test to check the specific list of concepts that comes back from keep-latest
(deftest verify-keep-latest-non-deleted-concepts
  ;; Make sure that filtering out old revisions and tombstones works as expected.
  (testing "Verifying keep-latest-non-deleted-concepts returns the correct concepts"
    (is (= 3 (count (cc/keep-latest-non-deleted-concepts invalid-concepts))))
    (is (= 1 (count (cc/keep-latest-non-deleted-concepts valid-concepts))))))

(defn run-constraint
  "TODO"
  [constraint saved-concept & existing-concepts]
  (let [db (mem-db/create-db (cons saved-concept existing-concepts))]
    (constraint db saved-concept)))

(defn assert-invalid
  "TODO"
  [error-msg constraint saved-concept & existing-concepts]
  (is (= error-msg
         (apply run-constraint constraint saved-concept existing-concepts))))

(defn assert-valid
  "TODO"
  [constraint saved-concept & existing-concepts]
  (is (nil? (apply run-constraint constraint saved-concept existing-concepts))))


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
            [other-concept]))))))


(comment

  (let [c1 (make-coll-concept "PROV1" "C1-PROV1" 1 {:entry-title "ET1"})
        c2 (make-coll-concept "PROV1" "C2-PROV1" 1 {:entry-title "ET1"})
        db (mem-db/create-db [c1 c2])]


    (cc/entry-title-unique-constraint
      db c1)
    )



  (dc/find-latest-concepts
    (mem-db/create-db [(make-coll-concept "PROV1" "C1-PROV1" 1 {:entry-title "ET1"})
                       (make-coll-concept "PROV1" "C2-PROV1" 1 {:entry-title "ET1"})])
    {:entry-title "ET1"
     :concept-type :collection
     :provider-id "PROV1"})

  )












