(ns cmr.metadata-db.int-test.concepts.collection-save-test
  "Contains integration tests for saving collections. Tests saves with various configurations including
  checking for proper error handling."
  (:require 
   [clj-time.format :as time-format]
   [clojure.test :refer :all]
   [cmr.common.time-keeper :as time-keeper]
   [cmr.metadata-db.int-test.concepts.concept-save-spec :as c-spec]
   [cmr.metadata-db.int-test.utility :as util]
   [cmr.metadata-db.services.concept-constraints :as cc]
   [cmr.metadata-db.services.messages :as msg]))


;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Set up REG_PROV as regular provider and SMAL_PROV1 as a small provider
(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV1" :small true}
                                                 {:provider-id "SMAL_PROV2" :small true}))

(defmethod c-spec/gen-concept :collection
  [_ provider-id uniq-num attributes]
  (util/collection-concept provider-id uniq-num attributes))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest save-collection-test
  (c-spec/general-save-concept-test :collection ["REG_PROV" "SMAL_PROV1"]))

(deftest save-test-with-missing-required-parameters
  (c-spec/save-test-with-missing-required-parameters
    :collection ["REG_PROV" "SMAL_PROV1"] [:concept-type :provider-id :native-id :extra-fields]))

(deftest save-collection-with-same-native-id-test
  (testing "Save collections with the same native-id for two small providers is OK"
    (let [coll1 (util/collection-concept "SMAL_PROV1" 1 {:native-id "foo"})
          coll2 (util/collection-concept "SMAL_PROV2" 2 {:native-id "foo"})]
      (c-spec/save-distinct-concepts-test coll1 coll2))))

(defn- created-at-same?
  "Returns true if the `created-at` for the given concept revisions are the same"
  [& concepts]
  (let [created-ats (map :created-at concepts)]
    (apply = created-ats)))

(deftest save-collection-created-at-test
  (testing "Saved collection gets current time for created-at"
    (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
      ;; Freeze time so we know what `created-at` time to expect.
      (time-keeper/freeze-time!)
      (let [collection (util/collection-concept provider-id 1)
            {:keys [concept-id revision-id]} (util/save-concept collection)
            collection-revision (:concept (util/get-concept-by-id-and-revision concept-id revision-id))]
        (is (= (time-keeper/now) 
               (time-format/parse (time-format/formatters :date-time) (:created-at collection-revision)))))
      (time-keeper/clear-current-time!)))
  (testing "Save collection multiple times gets same created-at" 
    (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
      (let [initial-collection (util/collection-concept provider-id 2)
            ;; Use time-keeper to freeze the clock, then save the collection.
            ;; Then use time-keeper to force the clock to advance by 1 hour
            ;; before saving it a second time. Then advance the clock 1/2 hour and save a tombstone.
            ;; Finally, advance the clock 1/2 hour and save a new (non-tombstone) revision.
            ;; All should have the same `created-at` value.
            _ (time-keeper/freeze-time!)
            {concept-id :concept-id initial-revision-id :revision-id} (util/save-concept initial-collection)
            _ (time-keeper/advance-time! 3600)
            {second-revision-id :revision-id} (util/save-concept initial-collection)
            _ (time-keeper/advance-time! 1800)
            {tombstone-revision-id :revision-id} (util/save-concept {:deleted true :concept-id concept-id})
            _ (time-keeper/advance-time! 1800)
            {final-revision-id :revision-id} (util/save-concept initial-collection)
            ;; Set the time back to normal
            _ (time-keeper/clear-current-time!)
            [initial-revision 
             second-revision 
             tombstone 
             final-revision] (mapv #(:concept (util/get-concept-by-id-and-revision concept-id %))
                                   [initial-revision-id 
                                    second-revision-id 
                                    tombstone-revision-id 
                                    final-revision-id])]
        (is (created-at-same? initial-revision second-revision tombstone final-revision))))))
               
(deftest save-collection-post-commit-constraint-violations
  (testing "duplicate entry titles"
    (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
      (let [existing-concept-id (str "C1-" provider-id)
            existing-collection (util/collection-concept provider-id 1
                                                         {:concept-id existing-concept-id
                                                          :revision-id 1
                                                          :extra-fields {:entry-title "ET-1"}})
            test-concept-id (str "C2-" provider-id)
            test-collection (util/collection-concept provider-id 2
                                                     {:concept-id test-concept-id
                                                      :revision-id 1
                                                      :extra-fields {:entry-title "ET-1"}})
            _ (util/save-concept existing-collection)
            test-collection-response (util/save-concept test-collection)]

        ;; The collection should be rejected due to another collection having the same entry-title
        (is (= {:status 409,
                :errors [(msg/duplicate-field-msg :entry-title [existing-collection])]}
               (select-keys test-collection-response [:status :errors])))

        ;; We need to verify that the collection which was inserted and failed the post commit
        ;; constraint checks is cleaned up from the database. We do this by verifying that
        ;; the db only contains the original collection.
        (let [found-concepts (util/find-concepts :collection
                                                 {:entry-title "ET-1" :provider-id provider-id})]
          (is (= [existing-collection]
                 (map #(dissoc % :revision-date :transaction-id :created-at) (:concepts found-concepts))))))))
  (testing "duplicate entry titles within multiple small providers is OK"
    (let [coll1 (util/collection-concept "SMAL_PROV1" 1
                                         {:concept-id "C1-SMAL_PROV1"
                                          :revision-id 1
                                          :extra-fields {:entry-title "ET-1"}})
          coll2 (util/collection-concept "SMAL_PROV2" 2
                                         {:concept-id "C2-SMAL_PROV2"
                                          :revision-id 1
                                          :extra-fields {:entry-title "ET-1"}})
          _ (util/save-concept coll1)
          {:keys [status]} (util/save-concept coll2)]
      (is (= 201 status))
      (util/verify-concept-was-saved coll1)
      (util/verify-concept-was-saved coll2))))
