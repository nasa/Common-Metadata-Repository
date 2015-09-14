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
            [cmr.metadata-db.services.concept-constraints :as cc]))


;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Set up REG_PROV as regular provider and SMAL_PROV1 as a small provider
(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV1" :small true}
                                                 {:provider-id "SMAL_PROV2" :small true}))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-collection-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept (util/collection-concept provider-id 1)
          {:keys [status revision-id concept-id]} (util/save-concept concept)]
      (is (= 201 status))
      (is (= revision-id 1))
      (is (util/verify-concept-was-saved (assoc concept :revision-id revision-id :concept-id concept-id))))))

(deftest save-collection-with-revision-date-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept (util/collection-concept provider-id 1 {:revision-date (t/date-time 2001 1 1 12 12 14)})
          {:keys [status revision-id concept-id]} (util/save-concept concept)]
      (is (= 201 status))
      (is (= revision-id 1))
      (let [retrieved-concept (util/get-concept-by-id-and-revision concept-id revision-id)]
        (is (= (:revision-date concept) (:revision-date (:concept retrieved-concept))))))))

(deftest save-collection-with-bad-revision-date-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept (util/collection-concept provider-id 1 {:revision-date "foo"})
          {:keys [status errors]} (util/save-concept concept)]
      (is (= 422 status))
      (is (= ["[foo] is not a valid datetime"] errors)))))

(deftest save-collection-with-same-native-id-test
  (testing "Save collections with the same native-id for two small providers is OK"
    (let [coll1 (util/create-and-save-collection "SMAL_PROV1" 1 1 {:native-id "foo"})
          coll2 (util/create-and-save-collection "SMAL_PROV2" 2 1 {:native-id "foo"})
          [coll1-concept-id coll2-concept-id] (map :concept-id [coll1 coll2])]
      (is (util/verify-concept-was-saved coll1))
      (is (util/verify-concept-was-saved coll2))
      (is (not= coll1-concept-id coll2-concept-id)))))

(deftest save-collection-without-version-id-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept (util/collection-concept provider-id 1 {:extra-fields {:version-id nil}})
          {:keys [status revision-id concept-id]} (util/save-concept concept)]
      (is (= 201 status))
      (is (= revision-id 1))
      (is (util/verify-concept-was-saved (assoc concept :revision-id revision-id :concept-id concept-id))))))

(deftest save-concept-test-with-proper-revision-id-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept (util/collection-concept provider-id 1)]
      ;; save the concept once
      (let [{:keys [revision-id concept-id]} (util/save-concept concept)
            new-revision-id (inc revision-id)
            revision-date-0 (get-in (util/get-concept-by-id-and-revision concept-id revision-id)
                                    [:concept :revision-date])]
        ;; save it again with a valid revision-id
        (let [updated-concept (assoc concept :revision-id new-revision-id :concept-id concept-id)
              {:keys [status revision-id]} (util/save-concept updated-concept)
              revision-date-1 (get-in (util/get-concept-by-id-and-revision concept-id revision-id)
                                      [:concept :revision-date])]
          (is (= 201 status))
          (is (= revision-id new-revision-id))
          (is (t/after? revision-date-1 revision-date-0))
          (is (util/verify-concept-was-saved updated-concept)))))))

(deftest save-concept-with-skipped-revisions-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept (util/collection-concept provider-id 1)
          {:keys [concept-id]} (util/save-concept concept)
          concept-with-skipped-revisions (assoc concept :concept-id concept-id :revision-id 100)
          {:keys [status revision-id]} (util/save-concept concept-with-skipped-revisions)
          {retrieved-concept :concept} (util/get-concept-by-id concept-id)]
      (is (= 201 status))
      (is (= 100 revision-id (:revision-id retrieved-concept))))))

(deftest auto-increment-of-revision-id-works-with-skipped-revisions-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept (util/collection-concept provider-id 1)
          {:keys [concept-id]} (util/save-concept concept)
          concept-with-concept-id (assoc concept :concept-id concept-id)
          _ (util/save-concept (assoc concept-with-concept-id :revision-id 100))
          {:keys [status revision-id]} (util/save-concept concept-with-concept-id)
          {retrieved-concept :concept} (util/get-concept-by-id concept-id)]
      (is (= 201 status))
      (is (= 101 revision-id (:revision-id retrieved-concept))))))

(deftest save-concept-with-low-revision-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept (util/collection-concept provider-id 1)
          {:keys [concept-id]} (util/save-concept concept)
          concept-with-bad-revision (assoc concept :concept-id concept-id :revision-id 0)
          {:keys [status revision-id]} (util/save-concept concept-with-bad-revision)]
      (is (= 409 status))
      (is (nil? revision-id)))))

(deftest save-concept-with-revision-id-0
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept-with-bad-revision (util/collection-concept provider-id 1 {:revision-id 0})
          {:keys [status]} (util/save-concept concept-with-bad-revision)]
      (is (= 409 status)))))

(deftest save-concept-with-missing-required-parameter
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept (util/collection-concept provider-id 1)]
      (are [field] (let [{:keys [status errors]} (util/save-concept (dissoc concept field))]
                     (and (= 422 status)
                          (re-find (re-pattern (name field)) (first errors))))
           :concept-type
           :provider-id
           :native-id
           :extra-fields))))

(deftest save-concept-after-delete
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept (util/collection-concept provider-id 1)
          {:keys [concept-id]} (util/save-concept concept)]
      (is (= 201 (:status (util/delete-concept concept-id))))
      (let [{:keys [status revision-id]} (util/save-concept concept)]
        (is (= 201 status))
        (is (= revision-id 3))))))

(deftest save-concept-after-delete-invalid-revision-id
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept (util/collection-concept provider-id 1)
          {:keys [concept-id]} (util/save-concept concept)]
      (is (= 201 (:status (util/delete-concept concept-id))))
      (let [{:keys [status revision-id]} (util/save-concept (assoc concept :revision-id 0))]
        (is (= 409 status))))))

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
      (is (util/verify-concept-was-saved coll1))
      (is (util/verify-concept-was-saved coll2)))))

