(ns cmr.metadata-db.int-test.concepts.variable-save-test
  "Contains integration tests for saving variables. Tests saves with various configurations including
  checking for proper error handling."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer (are3)]
   [cmr.metadata-db.int-test.concepts.concept-save-spec :as c-spec]
   [cmr.metadata-db.int-test.utility :as util]))

(def provider-id "REG_PROV")

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (util/reset-database-fixture
                     {:provider-id provider-id :small false}))

(defmethod c-spec/gen-concept :variable
  [_ provider-id uniq-num attributes]
  (util/variable-concept provider-id uniq-num attributes))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-variable-test
  (c-spec/general-save-concept-test :variable [provider-id]))

(deftest save-variable-with-missing-required-parameters-test
  (c-spec/save-test-with-missing-required-parameters
    :variable [provider-id] [:concept-type :provider-id :native-id :extra-fields]))

(deftest save-variable-created-at-test
  (testing "Save variable multiple times gets same created-at"
    (let [initial-var (util/variable-concept provider-id 2)
          ;; 1) Save a variable
          ;; 2) Then wait for a small period of time before saving it again
          ;; 3) Then wait again and save a tombstone.
          ;; 4) Finally, wait a bit and save a new (non-tombstone) revision.
          ;;
          ;; All should have the same `created-at` value.
          ;;
          ;; Note - Originally planned to use the time-keeper functionality
          ;; for this, but metdata-db tests don't have access to the control
          ;; api that would allow this to work in CI.
          {concept-id :concept-id
           initial-revision-id :revision-id} (util/save-concept initial-var)
          _ (Thread/sleep 100)
          {second-revision-id :revision-id} (util/save-concept initial-var)
          _ (Thread/sleep 100)
          {tombstone-revision-id :revision-id} (util/delete-concept
                                                concept-id)
          _ (Thread/sleep 100)
          {final-revision-id :revision-id} (util/save-concept initial-var)
          [initial-revision
           second-revision
           tombstone
           final-revision] (mapv #(:concept (util/get-concept-by-id-and-revision concept-id %))
                                 [initial-revision-id
                                  second-revision-id
                                  tombstone-revision-id
                                  final-revision-id])]
      (is (util/created-at-same? initial-revision
                                 second-revision
                                 tombstone
                                 final-revision)))))
