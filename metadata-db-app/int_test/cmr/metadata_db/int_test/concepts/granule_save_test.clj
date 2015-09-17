(ns cmr.metadata-db.int-test.concepts.granule-save-test
  "Contains integration tests for saving granules. Tests saves with various configurations including
  checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.local :as l]
            [cmr.system-int-test.utils.index-util :as index]
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
(deftest save-granule-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [parent-collection (util/create-and-save-collection provider-id 1)
          granule (util/granule-concept provider-id parent-collection 1)
          {:keys [status revision-id concept-id] :as resp} (util/save-concept granule)]
      (is (= 201 status) (pr-str resp))
      (is (= revision-id 1))
      (util/verify-concept-was-saved (assoc granule :revision-id revision-id :concept-id concept-id)))))

(deftest save-granule-with-same-native-id-test
  (testing "Save granules with the same native-id for two small providers is OK"
    (let [coll1 (util/create-and-save-collection "SMAL_PROV1" 1)
          coll2 (util/create-and-save-collection "SMAL_PROV2" 2)
          gran1 (util/create-and-save-granule "SMAL_PROV1" coll1 1 1 {:native-id "foo"})
          gran2 (util/create-and-save-granule "SMAL_PROV2" coll2 2 1 {:native-id "foo"})
          [gran1-concept-id gran2-concept-id] (map :concept-id [gran1 gran2])]
      (util/verify-concept-was-saved gran1)
      (util/verify-concept-was-saved gran2)
      (is (not= gran1-concept-id gran2-concept-id)))))

(deftest save-granule-with-concept-id
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [parent-collection (util/create-and-save-collection provider-id 1)
          gran-concept-id (str "G10-" provider-id)
          granule (util/granule-concept provider-id parent-collection 1 {:concept-id gran-concept-id})
          {:keys [status revision-id concept-id]} (util/save-concept granule)]
      (is (= 201 status))
      (is (= revision-id 1))
      (util/verify-concept-was-saved (assoc granule :revision-id revision-id :concept-id concept-id))

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
              granule2 (util/granule-concept provider-id parent-collection 2
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
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [granule (util/granule-concept provider-id nil 1)
          {:keys [status revision-id concept-id]} (util/save-concept granule)]
      (is (= 422 status)))))

(deftest save-granule-post-commit-constraint-violations
  (testing "duplicate granule URs"
    (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
      ;; Turn on enforcement of duplicate granule UR constraint
      (cc/set-enforce-granule-ur-constraint! true)
      (let [parent-collection (util/create-and-save-collection provider-id 1)
            existing-gran-concept-id (str "G1-" provider-id)
            existing-granule (util/granule-concept provider-id parent-collection 1
                                                   {:concept-id existing-gran-concept-id
                                                    :revision-id 1
                                                    :extra-fields {:granule-ur "GR-UR1"}})
            test-gran-concept-id (str "G2-" provider-id)
            test-granule (util/granule-concept provider-id parent-collection 2
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
          (is (= [(update-in existing-granule [:extra-fields] dissoc :parent-entry-title)]
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
          gran1 (util/granule-concept "SMAL_PROV1" coll1 1
                                      {:concept-id "G1-SMAL_PROV1"
                                       :revision-id 1
                                       :extra-fields {:granule-ur "GR-UR1"}})
          gran2 (util/granule-concept "SMAL_PROV2" coll2 2
                                      {:concept-id "G2-SMAL_PROV2"
                                       :revision-id 1
                                       :extra-fields {:granule-ur "GR-UR1"}})
          _ (util/save-concept gran1)
          {:keys [status]} (util/save-concept gran2)]
      (is (= 201 status))
      (util/verify-concept-was-saved gran1)
      (util/verify-concept-was-saved gran2))))

