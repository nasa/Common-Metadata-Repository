(ns cmr.metadata-db.int-test.concepts.variable-save-test
  "Contains integration tests for saving variables. Tests saves with various configurations including
  checking for proper error handling."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer (are3)]
   [cmr.metadata-db.int-test.concepts.concept-save-spec :as c-spec]
   [cmr.metadata-db.int-test.utility :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Fixtures & one-off utility functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (util/reset-database-fixture
                     {:provider-id "PROV1" :small false}
                     {:provider-id "PROV2" :small false}))

(defmethod c-spec/gen-concept :variable
  [_ provider-id uniq-num attributes]
  (util/variable-concept "PROV1" uniq-num attributes))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest save-variable
  (c-spec/general-save-concept-test :variable ["PROV1"]))

(deftest save-variable-with-missing-required-parameters
  (c-spec/save-test-with-missing-required-parameters
    :variable ["PROV1"] [:concept-type :provider-id :native-id :extra-fields]))

(deftest save-variable-created-at
  (testing "Save variable multiple times gets same created-at"
    (let [initial-var (util/variable-concept "PROV1" 2)
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
          revisions (util/get-revisions concept-id
                                        initial-revision-id
                                        second-revision-id
                                        tombstone-revision-id
                                        final-revision-id)]
      (is (apply util/created-at-same? revisions)))))

(deftest save-variable-with-conflicting-native-id
  (testing "Save a variable"
    (let [var1 (util/variable-concept "PROV1" 1)
          {:keys [status revision-id concept-id]} (util/save-concept var1)]
      (is (= status 201))
      (is (= 1 revision-id))
      (testing "and another with all the same data"
        (let [var2 var1
              {:keys [status revision-id]} (util/save-concept var1)]
          (is (= 201 status))
          (is (= 2 revision-id))))
      (testing "and another with same data but different provider"
        (let [var3 (assoc var1 :provider-id "PROV2")
              {:keys [status revision-id]} (util/save-concept var3)]
          (is (= status 201))
          (is (= 1 revision-id))))
      (testing "and another the same data but with a different native-id"
        (let [var4 (assoc var1 :native-id "var-native-different")
              response (util/save-concept var4)
              ;; after the saving of var2, the revision id was 2; the
              ;; saving of var3 was for a different provider, so it
              ;; has a revision-id of 1; if the call above with var4
              ;; passed the constaint checks (which is shouldn't) the
              ;; revision-id would now be 3 (but it shouldn't be)
              failed-revision-id 3
              find-response (util/get-concept-by-id-and-revision
                             concept-id failed-revision-id)]
          (is (= nil (:revision-id response)))
          (is (= 409 (:status response)))
          (is (= 404 (:status find-response)))
          (is (= [(format (str "The provider id [%s] and :variable-name [%s] "
                               "combined must be unique for a given native-id "
                               "[%s]. The following concept with the same "
                               "provider id, :variable-name, and native-id was "
                               "found: [%s].")
                          "PROV1"
                          (get-in var1 [:extra-fields :variable-name])
                          (:native-id var1)
                          concept-id)]
                      (:errors response))))))))
