(ns cmr.metadata-db.int-test.concepts.collection-save-test
  "Contains integration tests for saving collections. Tests saves with various configurations including
  checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.local :as l]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.metadata-db.services.messages :as msg]
            [cmr.metadata-db.services.concept-constraints :as cc]
            [cmr.metadata-db.int-test.concepts.concept-spec :as c-spec]))


;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Set up REG_PROV as regular provider and SMAL_PROV1 as a small provider
(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV1" :small true}
                                                 {:provider-id "SMAL_PROV2" :small true}))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest save-collection-tests
  (doseq [[idx provider-id] (map-indexed vector ["REG_PROV" "SMAL_PROV1"])]
    (testing "basic save"
      (let [concept (util/collection-concept provider-id 1)]
        (c-spec/save-concept-test concept 201 1 nil)))

    (testing "save with revision-date"
      (let [concept (util/collection-concept provider-id 2 {:revision-date (t/date-time 2001 1 1 12 12 14)})]
        (c-spec/save-concept-validate-field-saved-test concept :revision-date)))

    (testing "save with bad revision-date"
      (let [concept (util/collection-concept provider-id 3 {:revision-date "foo"})]
        (c-spec/save-concept-test concept 422 nil ["[foo] is not a valid datetime"])))

    (testing "save collection without version-id"
      (let [concept (util/collection-concept provider-id 4 {:extra-fields {:version-id nil}})]
        (c-spec/save-concept-test concept 201 1 nil)))

    (testing "save with proper revision-id"
      (let [concept (util/collection-concept provider-id 5)]
        (c-spec/save-concept-with-revision-id-test concept 201 2 nil)))

    (testing "save with skipped revisions"
      (let [concept (util/collection-concept provider-id 6)]
        (c-spec/save-concept-with-revision-id-test concept 201 100 nil)))

    (testing "save with low revision fails"
      (let [concept (util/collection-concept provider-id 7)]
        (c-spec/save-concept-with-revision-id-test
          concept 409 1 [(format "Expected revision-id of [2] got [1] for [C%d-%s]"
                                 (+ 1200000005 (* idx 9)) provider-id)])))

    (testing "save concept with revision-id 0 fails"
      (let [concept-with-bad-revision (util/collection-concept provider-id 18 {:revision-id 0})]
        (c-spec/save-concept-test
          concept-with-bad-revision 409 nil ["Expected revision-id of [1] got [0] for [null]"])))

    (testing "save concept with missing required parameters fails"
      (let [concept (util/collection-concept provider-id 8)]
        (are [field errors] (c-spec/save-concept-test (dissoc concept field) 422 nil errors)
             :concept-type ["Concept must include concept-type."
                            "Concept field [concept-type] cannot be nil."]
             :provider-id ["Concept must include provider-id."]
             :native-id ["Concept must include native-id."]
             :extra-fields ["Concept must include extra-fields"])))

    (testing "save after delete"
      (let [concept (util/collection-concept provider-id 9)
            {:keys [concept-id]} (util/save-concept concept)]
        (is (= 201 (:status (util/delete-concept concept-id))))
        (c-spec/save-concept-test concept 201 3 nil)))

    (testing "save after delete with invalid revision fails"
      (let [concept (util/collection-concept provider-id 10)
            {:keys [concept-id]} (util/save-concept concept)]
        (is (= 201 (:status (util/delete-concept concept-id))))
        (c-spec/save-concept-test
          (assoc concept :revision-id 1)
          409
          nil
          [(format "Expected revision-id of [3] got [1] for [C%d-%s]"
                   (+ 1200000008 (* idx 9)) provider-id)])))

    (testing "auto-increment of revision-id with skpped revisions"
      (let [concept (util/collection-concept provider-id 1)
            {:keys [concept-id]} (util/save-concept concept)
            concept-with-concept-id (assoc concept :concept-id concept-id)
            _ (util/save-concept (assoc concept-with-concept-id :revision-id 100))
            {:keys [status revision-id]} (util/save-concept concept-with-concept-id)
            {retrieved-concept :concept} (util/get-concept-by-id concept-id)]
        (is (= 201 status))
        (is (= 101 revision-id (:revision-id retrieved-concept)))))))

(deftest save-collection-with-same-native-id-test
  (testing "Save collections with the same native-id for two small providers is OK"
    (let [coll1 (util/collection-concept "SMAL_PROV1" 1 {:native-id "foo"})
          coll2 (util/collection-concept "SMAL_PROV2" 2 {:native-id "foo"})]
      (c-spec/save-distinct-concepts-test coll1 coll2))))

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
                 (map #(dissoc % :revision-date) (:concepts found-concepts))))))))
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

