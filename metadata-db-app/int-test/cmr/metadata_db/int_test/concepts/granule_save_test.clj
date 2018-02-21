(ns cmr.metadata-db.int-test.concepts.granule-save-test
  "Contains integration tests for saving granules. Tests saves with various configurations including
  checking for proper error handling."
  (:require
   [clj-http.client :as client]
   [clj-time.core :as t]
   [clj-time.format :as f]
   [clj-time.local :as l]
   [clojure.test :refer :all]
   [cmr.common-app.test.side-api :as side]
   [cmr.common.log :refer (info)]
   [cmr.metadata-db.int-test.concepts.concept-save-spec :as c-spec]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]
   [cmr.metadata-db.services.concept-constraints :as cc]
   [cmr.metadata-db.services.messages :as msg])
  (:import java.io.FileNotFoundException))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Set up REG_PROV as regular provider and SMAL_PROV1 as a small provider
(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV1" :small true}
                                                 {:provider-id "SMAL_PROV2" :small true}))

(defmethod c-spec/gen-concept :granule
  [_ provider-id uniq-num attributes]
  (let [collection-attributes (or (:parent-attributes attributes) {})
        parent-collection (concepts/create-and-save-concept :collection provider-id uniq-num)
        attributes (dissoc attributes :parent-attributes)]
    (concepts/create-concept :granule provider-id parent-collection uniq-num attributes)))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-granule-test
  (c-spec/general-save-concept-test :granule ["REG_PROV" "SMAL_PROV1"]))

(deftest save-test-with-missing-required-parameters
  (c-spec/save-test-with-missing-required-parameters
    :granule ["REG_PROV" "SMAL_PROV1"] [:concept-type :provider-id :native-id :extra-fields]))

(deftest save-granule-with-same-native-id-test
  (testing "Save granules with the same native-id for two small providers is OK"
    (let [coll1 (concepts/create-and-save-concept :collection "SMAL_PROV1" 1)
          coll2 (concepts/create-and-save-concept :collection "SMAL_PROV2" 2)
          gran1 (concepts/create-and-save-concept :granule "SMAL_PROV1" coll1 1 1
                                                  {:native-id "foo"})
          gran2 (concepts/create-and-save-concept :granule "SMAL_PROV2" coll2 2 1
                                                  {:native-id "foo"})
          [gran1-concept-id gran2-concept-id] (map :concept-id [gran1 gran2])]
      (util/verify-concept-was-saved gran1)
      (util/verify-concept-was-saved gran2)
      (is (not= gran1-concept-id gran2-concept-id)))))

(deftest save-granule-post-commit-constraint-violations
  (testing "duplicate granule URs"
    (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
      ;; Turn on enforcement of duplicate granule UR constraint
      (side/eval-form `(cc/set-enforce-granule-ur-constraint! true))
      (let [parent-collection (concepts/create-and-save-concept :collection provider-id 1)
            existing-gran-concept-id (str "G1-" provider-id)
            existing-granule (concepts/create-concept :granule provider-id parent-collection 1
                                                      {:concept-id existing-gran-concept-id
                                                       :revision-id 1
                                                       :extra-fields {:granule-ur "GR-UR1"}})
            test-gran-concept-id (str "G2-" provider-id)
            test-granule (concepts/create-concept :granule provider-id parent-collection 2
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
                 (map #(dissoc % :created-at :revision-date :transaction-id) (:concepts found-concepts)))))
        (testing "duplicate granule URs are allowed when the constraint is configured as off"
          (try
            (side/eval-form `(cc/set-enforce-granule-ur-constraint! false))
            (is (= {:status 201
                    :revision-id (:revision-id test-granule)
                    :concept-id (:concept-id test-granule)
                    :errors nil}
                   (util/save-concept test-granule)))
            (finally
              (side/eval-form `(cc/set-enforce-granule-ur-constraint! true))))))))

  (testing "duplicate granule urs within multiple small providers is OK"
    (let [coll1 (concepts/create-and-save-concept :collection "SMAL_PROV1" 1)
          coll2 (concepts/create-and-save-concept :collection "SMAL_PROV2" 2)
          gran1 (concepts/create-concept
                 :granule "SMAL_PROV1" coll1 1 {:concept-id "G1-SMAL_PROV1"
                                                :revision-id 1
                                                :extra-fields {:granule-ur "GR-UR1"}})
          gran2 (concepts/create-concept
                 :granule "SMAL_PROV2" coll2 2 {:concept-id "G2-SMAL_PROV2"
                                                :revision-id 1
                                                :extra-fields {:granule-ur "GR-UR1"}})
          _ (util/save-concept gran1)
          {:keys [status]} (util/save-concept gran2)]
      (is (= 201 status))
      (util/verify-concept-was-saved gran1)
      (util/verify-concept-was-saved gran2))))
