(ns cmr.metadata-db.int-test.concepts.concept-spec
  "Defines a common set of tests for concepts."
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [cmr.metadata-db.int-test.utility :as util]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Set up REG_PROV as regular provider and SMAL_PROV1 as a small provider
(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV1" :small true}))

(defn save-concept-test
  "Attempt to save a concept and compare the result to the expected result."
  [concept exp-status exp-revision-id exp-errors]
  (testing "save concept"
    (let [{:keys [status revision-id concept-id errors]} (util/save-concept concept)]
      (is (= exp-status status))
      (is (= exp-revision-id revision-id))
      (is (= exp-errors errors))
      (when (= 201 exp-status)
        (util/verify-concept-was-saved
          (assoc concept :revision-id revision-id :concept-id concept-id)))))
  ;; return `true` so we can use this in an `are` expression
  true)

(defn save-concept-with-revision-id-test
  "Save a concept once then save it again with the provided revision id, validating that the
  repsonse matches the expected response, and possibly that the second save succeeded."
  [concept exp-status new-revision-id exp-errors]
  (testing "save concept with revision id"
    (let [{:keys [concept-id revision-id]} (util/save-concept concept)
          revision-date1 (get-in (util/get-concept-by-id-and-revision concept-id revision-id)
                                 [:concept :revision-date])
          updated-concept (assoc concept :revision-id new-revision-id :concept-id concept-id)
          {:keys [status errors revision-id]} (util/save-concept updated-concept)
          revision-date2 (get-in (util/get-concept-by-id-and-revision concept-id revision-id)
                                 [:concept :revision-date])]
      (is (= exp-status status))
      (is (= exp-errors errors))
      (when (= 201 exp-status)
        (is (= new-revision-id revision-id))
        (is (t/after? revision-date2 revision-date1))
        (util/verify-concept-was-saved updated-concept)))))

(defn save-concept-validate-field-saved-test
  "Save a concept and validate the the saved concept has the same value for the given field."
  [concept field]
  (testing "save concept with given field"
    (let [ {:keys [status revision-id concept-id]} (util/save-concept concept)]
      (is (= 201 status))
      (is (= revision-id 1))
      (let [retrieved-concept (util/get-concept-by-id-and-revision concept-id revision-id)]
        (is (= (get concept field) (get (:concept retrieved-concept) field)))))))

(defn save-distinct-concepts-test
  "Save two different but similar concepts and verify that they get different concept-ids."
  [concept1 concept2]
  (testing "save different concepts"
    (let [{concept-id1 :concept-id revision-id1 :revision-id} (util/save-concept concept1)
          {concept-id2 :concept-id revision-id2 :revision-id} (util/save-concept concept2)]
      (util/verify-concept-was-saved (assoc concept1 :revision-id revision-id1 :concept-id concept-id1))
      (util/verify-concept-was-saved (assoc concept2 :revision-id revision-id2 :concept-id concept-id2))
      (is (not= concept-id1 concept-id2)))))