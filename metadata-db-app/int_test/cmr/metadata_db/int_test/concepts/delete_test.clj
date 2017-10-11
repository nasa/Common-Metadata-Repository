(ns cmr.metadata-db.int-test.concepts.delete-test
  "Contains integration tests for deleting concepts. Tests delete with various configurations including
  checking for proper error handling."
  (:require
   [cheshire.core :as cheshire]
   [clj-http.client :as client]
   [clj-time.core :as t]
   [clojure.test :refer :all]
   [cmr.common.util :as u]
   [cmr.metadata-db.int-test.concepts.concept-delete-spec :as cd-spec]
   [cmr.metadata-db.int-test.utility :as util]
   [cmr.metadata-db.services.messages :as messages]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV" :small true}))

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest delete-concepts-test
  (doseq [concept-type [:collection :granule :variable :variable-association :service
                        :service-association]]
    (cd-spec/general-delete-test concept-type ["REG_PROV" "SMAL_PROV"])))

(deftest delete-tag-general
  (cd-spec/general-delete-test :tag ["CMR"]))

(deftest delete-tag-association-general
  (cd-spec/general-delete-test :tag-association ["CMR"]))

(deftest delete-group-general
  (cd-spec/general-delete-test :access-group ["REG_PROV" "SMAL_PROV" "CMR"]))

(deftest tag-delete-cascades-associations
  (testing "delete cascades to tag associations"
    (let [tag-collection (util/create-and-save-collection "REG_PROV" 1)
          tag (util/create-and-save-tag 1)
          tag-association (util/create-and-save-tag-association tag-collection tag 1 1)
          tag-association-id (:concept-id tag-association)
          {status :status tag-association-concept :concept} (util/get-concept-by-id tag-association-id)
          _ (util/delete-concept (:concept-id tag))
          {tombstone-status :status tombstone :concept} (util/get-concept-by-id tag-association-id)]
      (testing "tag association was saved and is not a tombstone"
        (is (= 200 status))
        (is (not (:deleted tag-association-concept))))
      (testing "tag association is tombstoned after tag is deleted"
        (is (= 200 tombstone-status))
        (is (:deleted tombstone))))))

(deftest variable-delete-cascades-associations
  (testing "delete cascades to variable associations"
    (let [variable-collection (util/create-and-save-collection "REG_PROV" 1)
          variable (util/create-and-save-variable "REG_PROV" 1)
          variable-association (util/create-and-save-variable-association
                                variable-collection variable 1 1)
          variable-association-id (:concept-id variable-association)
          {status :status variable-association-concept :concept} (util/get-concept-by-id
                                                                  variable-association-id)
          _ (util/delete-concept (:concept-id variable))
          {tombstone-status :status tombstone :concept} (util/get-concept-by-id
                                                         variable-association-id)]
      (testing "variable association was saved and is not a tombstone"
        (is (= 200 status))
        (is (not (:deleted variable-association-concept))))
      (testing "variable association is tombstoned after variable is deleted"
        (is (= 200 tombstone-status))
        (is (:deleted tombstone))))))

;; collections must be tested separately to make sure granules are deleted as well
(deftest delete-collection-using-delete-end-point-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [coll1 (util/create-and-save-collection provider-id 1 3)
          gran1 (util/create-and-save-granule provider-id coll1 1 2)
          coll2 (util/create-and-save-collection provider-id 2)
          gran3 (util/create-and-save-granule provider-id coll2 2)
          {:keys [status revision-id] :as tombstone} (util/delete-concept (:concept-id coll1))
          deleted-coll1 (:concept (util/get-concept-by-id-and-revision (:concept-id coll1) revision-id))
          saved-coll1 (:concept (util/get-concept-by-id-and-revision (:concept-id coll1) (dec revision-id)))]
      (is (= {:status 201
              :revision-id 4}
             {:status status
              :revision-id revision-id}))

      (is (= (dissoc (assoc saved-coll1
                            :deleted true
                            :metadata ""
                            :revision-id revision-id
                            :user-id nil)
                     :revision-date :transaction-id)
             (dissoc deleted-coll1 :revision-date :transaction-id)))

      ;; Make sure that a deleted collection gets it's own unique revision date
      (is (t/after? (:revision-date deleted-coll1) (:revision-date saved-coll1))
          "The deleted collection revision date should be after the previous revisions revision date.")

      ;; Verify granule was deleted
      (is (= {:status 404} (util/get-concept-by-id-and-revision (:concept-id gran1) 1)))
      (is (= {:status 404} (util/get-concept-by-id-and-revision (:concept-id gran1) 2)))

      ;; Other data left in database
      (util/verify-concept-was-saved coll2)
      (util/verify-concept-was-saved gran3))))

(deftest delete-collection-using-save-end-point-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [coll1 (util/create-and-save-collection provider-id 1 3)
          gran1 (util/create-and-save-granule provider-id coll1 1 2)
          coll2 (util/create-and-save-collection provider-id 2)
          gran3 (util/create-and-save-granule provider-id coll2 2)

          {:keys [status revision-id]} (util/save-concept {:concept-id (:concept-id coll1)
                                                           :deleted true
                                                           :user-id "user101"})
          deleted-coll1 (:concept (util/get-concept-by-id-and-revision (:concept-id coll1) revision-id))
          saved-coll1 (:concept (util/get-concept-by-id-and-revision (:concept-id coll1) (dec revision-id)))]
      (is (= {:status 201
              :revision-id 4}
             {:status status
              :revision-id revision-id}))

      ;; Make sure that the saved tombstone has expected concept-id, revision-id, empty metadata,
      ;; and deleted = true.
      (is (= (dissoc (assoc saved-coll1
                            :deleted true
                            :metadata ""
                            :revision-id revision-id
                            :user-id "user101")
                     :revision-date :transaction-id)
             (dissoc deleted-coll1 :revision-date :transaction-id)))

      ;; make sure that a deleted collection gets it's own unique transaction-id
      (is (> (:transaction-id deleted-coll1) (:transaction-id saved-coll1)))

      ;; Make sure that a deleted collection gets it's own unique revision date
      (is (t/after? (:revision-date deleted-coll1) (:revision-date saved-coll1))
          "The deleted collection revision date should be after the previous revisions revision date.")

      ;; Verify granule was deleted
      (is (= {:status 404} (util/get-concept-by-id-and-revision (:concept-id gran1) 1)))
      (is (= {:status 404} (util/get-concept-by-id-and-revision (:concept-id gran1) 2)))

      ;; Other data left in database
      (util/verify-concept-was-saved coll2)
      (util/verify-concept-was-saved gran3))))

(deftest delete-concept-failure-cases
  (let [coll-reg-prov (util/create-and-save-collection "REG_PROV" 1)
        coll-small-prov (util/create-and-save-collection "SMAL_PROV" 1)]
    (testing "Using delete concept end-point"
      (u/are2 [concept-id revision-id expected-status error-messages]
              (let [{:keys [status errors]} (util/delete-concept concept-id revision-id)]
                (and (= expected-status status)
                     (= error-messages errors)))

              "Invalid revision-id: Regular provider"
              (:concept-id coll-reg-prov) 1 409
              ["Expected revision-id of [2] got [1] for [C1200000000-REG_PROV]"]

              "Invalid revision-id: Small provider"
              (:concept-id coll-small-prov) 0 409
              ["Expected revision-id of [2] got [0] for [C1200000001-SMAL_PROV]"]

              "Missing concept: Regular provider"
              "C100-REG_PROV" nil 404
              ["Concept with concept-id [C100-REG_PROV] does not exist."]

              "Missing concept: Small provider"
              "C100-SMAL_PROV" nil 404
              ["Concept with concept-id [C100-SMAL_PROV] does not exist."]

              "Missing concept for missing provider"
              "C100-NONEXIST" nil 404
              ["Provider with provider-id [NONEXIST] does not exist."]))
    (testing "Using save concept end-point"
      (u/are2 [concept expected-status error-messages]
              (let [{:keys [status errors]} (util/save-concept concept)]
                (and (= expected-status status)
                     (= error-messages errors)))

              "Invalid revision-id: Regular provider"
              {:concept-id (:concept-id coll-reg-prov) :revision-id 1 :deleted true} 409
              ["Expected revision-id of [2] got [1] for [C1200000000-REG_PROV]"]

              "Invalid revision-id: Small provider"
              {:concept-id (:concept-id coll-small-prov) :revision-id 0 :deleted true} 409
              ["Expected revision-id of [2] got [0] for [C1200000001-SMAL_PROV]"]

              "Missing concept: Regular provider"
              {:concept-id "C100-REG_PROV" :deleted true} 404
              ["Concept with concept-id [C100-REG_PROV] does not exist."]

              "Missing concept: Small provider"
              {:concept-id "C100-SMAL_PROV" :deleted true} 404
              ["Concept with concept-id [C100-SMAL_PROV] does not exist."]

              "Missing concept for missing provider"
              {:concept-id "C100-NONEXIST" :deleted true} 404
              ["Provider with provider-id [NONEXIST] does not exist."]))))
