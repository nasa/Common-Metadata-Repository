(ns cmr.metadata-db.int-test.concepts.save-test
  "Contains integration tests for saving concepts. Tests saves with various configurations including
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

(deftest save-granule-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [collection (util/collection-concept provider-id 1)
          parent-collection-id (:concept-id (util/save-concept collection))
          granule (util/granule-concept provider-id parent-collection-id 1)
          {:keys [status revision-id concept-id] :as resp} (util/save-concept granule)]
      (is (= 201 status) (pr-str resp))
      (is (= revision-id 1))
      (is (util/verify-concept-was-saved (assoc granule :revision-id revision-id :concept-id concept-id))))))

(deftest save-granule-with-same-native-id-test
  (testing "Save granules with the same native-id for two small providers is OK"
    (let [coll1 (util/create-and-save-collection "SMAL_PROV1" 1)
          coll2 (util/create-and-save-collection "SMAL_PROV2" 2)
          [coll1-concept-id coll2-concept-id] (map :concept-id [coll1 coll2])
          gran1 (util/create-and-save-granule "SMAL_PROV1" coll1-concept-id 1 1 {:native-id "foo"})
          gran2 (util/create-and-save-granule "SMAL_PROV2" coll2-concept-id 2 1 {:native-id "foo"})
          [gran1-concept-id gran2-concept-id] (map :concept-id [gran1 gran2])]
      (is (util/verify-concept-was-saved gran1))
      (is (util/verify-concept-was-saved gran2))
      (is (not= gran1-concept-id gran2-concept-id)))))

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

(deftest save-granule-with-concept-id
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [collection (util/collection-concept provider-id 1)
          parent-collection-id (:concept-id (util/save-concept collection))
          gran-concept-id (str "G10-" provider-id)
          granule (util/granule-concept
                    provider-id parent-collection-id 1 {:concept-id gran-concept-id})
          {:keys [status revision-id concept-id]} (util/save-concept granule)]
      (is (= 201 status))
      (is (= revision-id 1))
      (is (util/verify-concept-was-saved (assoc granule :revision-id revision-id :concept-id concept-id)))

      (testing "with incorrect native id"
        (let [response (util/save-concept (assoc granule :native-id "foo"))]
          (is (= {:status 409,
                  :errors [(msg/concept-exists-with-different-id
                             gran-concept-id (:native-id granule)
                             gran-concept-id "foo"
                             :granule provider-id) ]}
                 (select-keys response [:status :errors])))))

      (testing "with incorrect concept id"
        (let [other-gran-concept-id (str "G11-" provider-id)
              response (util/save-concept (assoc granule :concept-id other-gran-concept-id))]
          (is (= {:status 409,
                  :errors [(msg/concept-exists-with-different-id
                             gran-concept-id (:native-id granule)
                             other-gran-concept-id (:native-id granule)
                             :granule provider-id) ]}
                 (select-keys response [:status :errors])))))

      (testing "with incorrect concept id matching another concept"
        (let [other-gran-concept-id (str "G11-" provider-id)
              granule2 (util/granule-concept provider-id parent-collection-id 2
                                             {:concept-id other-gran-concept-id
                                              :native-id "native2"})
              _ (is (= 201 (:status (util/save-concept granule2))))
              response (util/save-concept (assoc granule :concept-id other-gran-concept-id))]
          (is (= {:status 409,
                  :errors [(msg/concept-exists-with-different-id
                             gran-concept-id (:native-id granule)
                             other-gran-concept-id (:native-id granule)
                             :granule provider-id) ]}
                 (select-keys response [:status :errors]))))))))

(deftest save-granule-with-nil-required-field
  (testing "nil parent-collection-id"
    (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
      (let [granule (util/granule-concept provider-id nil 1)
            {:keys [status revision-id concept-id]} (util/save-concept granule)]
        (is (= 422 status))
        (is (not (util/verify-concept-was-saved
                   (assoc granule :revision-id revision-id :concept-id concept-id))))))))

(deftest save-concept-with-invalid-json-test
  (let [response (client/post util/concepts-url
                              {:body "some non-json"
                               :body-encoding "UTF-8"
                               :content-type :json
                               :accept :json
                               :throw-exceptions false})]
    (is (= 400 (:status response)))
    (is (= "Malformed JSON in request body." (:body response)))))

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

(deftest save-granule-post-commit-constraint-violations
  (testing "duplicate granule URs"
    (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
      ;; Turn on enforcement of duplicate granule UR constraint
      (cc/set-enforce-granule-ur-constraint! true)
      (let [collection (util/collection-concept provider-id 1)
            parent-collection-id (:concept-id (util/save-concept collection))
            existing-gran-concept-id (str "G1-" provider-id)
            existing-granule (util/granule-concept provider-id parent-collection-id 1
                                                   {:concept-id existing-gran-concept-id
                                                    :revision-id 1
                                                    :extra-fields {:granule-ur "GR-UR1"}})
            test-gran-concept-id (str "G2-" provider-id)
            test-granule (util/granule-concept provider-id parent-collection-id 2
                                               {:concept-id test-gran-concept-id
                                                :revision-id 1
                                                :extra-fields {:granule-ur "GR-UR1"}})
            _ (util/save-concept existing-granule)
            test-granule-response (util/save-concept test-granule)]

        ;; The granule should be rejected due to another granule having the same granule-ur
        (is (= {:status 409,
                :errors [(msg/duplicate-field-msg :granule-ur [existing-granule])]}
               (select-keys test-granule-response [:status :errors])))

        ;; We need to verify that the granule which was inserted and failed the post commit
        ;; constraint checks is cleaned up from the database. We do this by verifying that
        ;; the db only contains the original granule.
        (let [found-concepts (util/find-concepts :granule
                                                 {:granule-ur "GR-UR1" :provider-id provider-id})]
          (is (= [existing-granule]
                 (map #(dissoc % :revision-date) (:concepts found-concepts)))))
        (testing "duplicate granule URs are allowed when the constraint is configured as off"
          (try
            (cc/set-enforce-granule-ur-constraint! false)
            (is (= {:status 201
                    :revision-id (:revision-id test-granule)
                    :concept-id (:concept-id test-granule)
                    :errors nil}
                   (util/save-concept test-granule)))
            (finally
              (cc/set-enforce-granule-ur-constraint! true)))))))
  (testing "duplicate granule urs within multiple small providers is OK"
    (let [coll1 (util/create-and-save-collection "SMAL_PROV1" 1)
          coll2 (util/create-and-save-collection "SMAL_PROV2" 2)
          gran1 (util/granule-concept "SMAL_PROV1" (:concept-id coll1) 1
                                      {:concept-id "G1-SMAL_PROV1"
                                       :revision-id 1
                                       :extra-fields {:granule-ur "GR-UR1"}})
          gran2 (util/granule-concept "SMAL_PROV2" (:concept-id coll2) 2
                                      {:concept-id "G2-SMAL_PROV2"
                                       :revision-id 1
                                       :extra-fields {:granule-ur "GR-UR1"}})
          _ (util/save-concept gran1)
          {:keys [status]} (util/save-concept gran2)]
      (is (= 201 status))
      (is (util/verify-concept-was-saved gran1))
      (is (util/verify-concept-was-saved gran2)))))

