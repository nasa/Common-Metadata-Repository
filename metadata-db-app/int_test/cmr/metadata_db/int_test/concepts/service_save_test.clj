(ns cmr.metadata-db.int-test.concepts.service-save-test
  "Contains integration tests for saving services. Tests saves with various
  configurations including checking for proper error handling."
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

(defmethod c-spec/gen-concept :service
  [_ _ uniq-num attributes]
  (util/service-concept "PROV1" uniq-num attributes))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

 (deftest save-service
   (c-spec/general-save-concept-test :service ["PROV1"]))

(deftest save-service-with-missing-required-parameters
  (c-spec/save-test-with-missing-required-parameters
   :service ["PROV1"] [:concept-type :provider-id :native-id :extra-fields]))

(deftest save-service-created-at
  (testing "Save service multiple times gets same created-at"
    (let [initial-svc (util/service-concept "PROV1" 2)
          ;; 1) Save a service
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
           initial-revision-id :revision-id} (util/save-concept initial-svc)
          _ (Thread/sleep 100)
          {second-revision-id :revision-id} (util/save-concept initial-svc)
          _ (Thread/sleep 100)
          {tombstone-revision-id :revision-id} (util/delete-concept
                                                concept-id)
          _ (Thread/sleep 100)
          {final-revision-id :revision-id} (util/save-concept initial-svc)
          revisions (util/get-revisions concept-id
                                        initial-revision-id
                                        second-revision-id
                                        tombstone-revision-id
                                        final-revision-id)]
      (is (apply util/created-at-same? revisions)))))

(deftest save-service-with-conflicting-native-id
  (testing "Save a service"
    (let [svc1 (util/service-concept "PROV1" 1)
          {:keys [status revision-id concept-id]} (util/save-concept svc1)]
      (is (= status 201))
      (is (= 1 revision-id))
      (testing "and another with all the same data"
        (let [svc2 svc1
              {:keys [status revision-id]} (util/save-concept svc1)]
          (is (= 201 status))
          (is (= 2 revision-id))))
      (testing "and another with same data but different provider"
        (let [svc3 (assoc svc1 :provider-id "PROV2")
              {:keys [status revision-id]} (util/save-concept svc3)]
          (is (= status 201))
          (is (= 1 revision-id))))
      (testing "and another the same data but with a different native-id"
        (let [svc4 (assoc svc1 :native-id "svc-native-different")
              response (util/save-concept svc4)
              ;; after the saving of svc2, the revision id was 2; the
              ;; saving of svc3 was for a different provider, so it
              ;; has a revision-id of 1; if the call above with svc4
              ;; passed the constaint checks (which is shouldn't) the
              ;; revision-id would now be 3 (but it shouldn't be)
              failed-revision-id 3
              find-response (util/get-concept-by-id-and-revision
                             concept-id failed-revision-id)]
          (is (= nil (:revision-id response)))
          (is (= 409 (:status response)))
          (is (= 404 (:status find-response)))
          (is (= [(format (str "The provider id [%s] and :service-name [%s] "
                               "combined must be unique for a given native-id "
                               "[%s]. The following concept with the same "
                               "provider id, :service-name, and native-id was "
                               "found: [%s].")
                          "PROV1"
                          (get-in svc1 [:extra-fields :service-name])
                          (:native-id svc1)
                          concept-id)]
                      (:errors response))))))))
