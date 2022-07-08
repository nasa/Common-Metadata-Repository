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
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]
   [cmr.metadata-db.services.messages :as messages]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV" :small true}))

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest delete-concepts-test
  (doseq [concept-type [:collection :granule :service
                        :service-association :tool :tool-association]]
   (cd-spec/general-delete-test concept-type ["REG_PROV" "SMAL_PROV"])))

;; subscription does not allow the same native-id across different providers, so we separate the tests
(deftest subscription-delete-concepts-reg-test
  (cd-spec/general-delete-test :subscription ["REG_PROV"]))

(deftest subscription-delete-concepts-small-test
  (cd-spec/general-delete-test :subscription ["SMAL_PROV"]))

(deftest delete-variable-and-association-concepts-test
  (cd-spec/general-delete-variable-and-association-test ["REG_PROV" "SMAL_PROV"]))

(deftest delete-tag-general
  (cd-spec/general-delete-test :tag ["CMR"]))

(deftest delete-tag-association-general
  (cd-spec/general-delete-test :tag-association ["CMR"]))

(deftest delete-group-general
  (cd-spec/general-delete-test :access-group ["REG_PROV" "SMAL_PROV" "CMR"]))

(defn- is-association-tombstone?
  "Returns true if the latest revision of the given association is a tombstone."
  [association]
  (let [{:keys [status concept]} (util/get-concept-by-id (:concept-id association))]
    ;; make sure the call succeeded
    (is (= 200 status))
    (:deleted concept)))

(deftest tag-delete-cascades-associations
  (testing "delete cascades to tag associations"
    (let [tag-collection (concepts/create-and-save-concept :collection "REG_PROV" 1)
          tag (concepts/create-and-save-concept :tag "CMR" 1)
          tag-association (concepts/create-and-save-concept :tag-association tag-collection tag 1 1)]
      (testing "tag association was saved and is not a tombstone"
        (is (= false (is-association-tombstone? tag-association))))
      (testing "tag association is tombstoned after tag is deleted"
        (util/delete-concept (:concept-id tag))
        (is (= true (is-association-tombstone? tag-association)))))))

(deftest variable-delete-cascades-associations
  (testing "delete cascades to variable associations"
    (let [coll (concepts/create-and-save-concept :collection "REG_PROV" 1)
          coll-concept-id (:concept-id coll)
          variable (concepts/create-and-save-concept :variable "REG_PROV" 1 1 {:coll-concept-id coll-concept-id})
          var-assn-concept-id (get-in variable [:variable-association :concept-id])
          variable-association (:concept (util/get-concept-by-id var-assn-concept-id))]
      (testing "variable association was saved and is not a tombstone"
        (is (= false (is-association-tombstone? variable-association))))
      (testing "variable association is tombstoned after variable is deleted"
        (util/delete-concept (:concept-id variable))
        (is (= true (is-association-tombstone? variable-association)))))))

(deftest service-delete-cascades-associations
  (testing "delete cascades to service associations"
    (let [coll (concepts/create-and-save-concept :collection "REG_PROV" 1)
          service (concepts/create-and-save-concept :service "REG_PROV" 1)
          service-association (concepts/create-and-save-concept :service-association coll service 1 1)]
      (testing "service association was saved and is not a tombstone"
        (is (= false (is-association-tombstone? service-association))))
      (testing "service association is tombstoned after service is deleted"
        (util/delete-concept (:concept-id service))
        (is (= true (is-association-tombstone? service-association)))))))

(deftest tool-delete-cascades-associations
  (testing "delete cascades to tool associations"
    (let [coll (concepts/create-and-save-concept :collection "REG_PROV" 1)
          tool (concepts/create-and-save-concept :tool "REG_PROV" 1)
          tool-association (concepts/create-and-save-concept :tool-association coll tool 1 1)]
      (testing "tool association was saved and is not a tombstone"
        (is (= false (is-association-tombstone? tool-association))))
      (testing "tool association is tombstoned after tool is deleted"
        (util/delete-concept (:concept-id tool))
        (is (= true (is-association-tombstone? tool-association)))))))

(defn- assert-collection-tombstone
  "Make sure that the saved tombstone has expected concept-id, revision-id, user-id,
  empty metadata, and deleted = true."
  [origin-coll deleted-coll revision-id user-id]
  (is (= (dissoc (assoc origin-coll
                        :deleted true
                        :metadata ""
                        :revision-id revision-id
                        :user-id user-id)
                 :revision-date :transaction-id)
         (dissoc deleted-coll :revision-date :transaction-id))))

;; collections must be tested separately to make sure granules are deleted as well
(deftest delete-collection-using-delete-end-point-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [coll1 (concepts/create-and-save-concept :collection provider-id 1 3)
          coll1-concept-id (:concept-id coll1)
          gran1 (concepts/create-and-save-concept :granule provider-id coll1 1 2)
          coll2 (concepts/create-and-save-concept :collection provider-id 2)
          gran3 (concepts/create-and-save-concept :granule provider-id coll2 2)
          variable (concepts/create-and-save-concept :variable provider-id 1 1 {:coll-concept-id coll1-concept-id})
          var-assn-concept-id (get-in variable [:variable-association :concept-id])
          variable-association (:concept (util/get-concept-by-id var-assn-concept-id))
          service (concepts/create-and-save-concept :service provider-id 1)
          service-association (concepts/create-and-save-concept :service-association coll1 service 1 1)
          tool (concepts/create-and-save-concept :tool provider-id 1)
          tool-association (concepts/create-and-save-concept :tool-association coll1 tool 1 1)]

      ;; variable association is not a tombstone before collection is deleted
      (is (= false (is-association-tombstone? variable-association)))
      ;; service association is not a tombstone before collection is deleted
      (is (= false (is-association-tombstone? service-association)))
      ;; tool association is not a tombstone before collection is deleted
      (is (= false (is-association-tombstone? tool-association)))

      ;; now delete coll1
      (let [{:keys [status revision-id]} (util/delete-concept (:concept-id coll1))
            deleted-coll1 (:concept (util/get-concept-by-id-and-revision
                                     (:concept-id coll1) revision-id))
            saved-coll1 (:concept (util/get-concept-by-id-and-revision
                                   (:concept-id coll1) (dec revision-id)))]

        (is (= {:status 201
                :revision-id 4}
               {:status status
                :revision-id revision-id}))

        ;; Make sure that the saved tombstone has expected concept-id, revision-id,
        ;; user-id, empty metadata, and deleted = true.
        (assert-collection-tombstone saved-coll1 deleted-coll1 revision-id nil)

        ;; Make sure that a deleted collection gets it's own unique revision date
        (is (t/after? (:revision-date deleted-coll1) (:revision-date saved-coll1))
            "The deleted collection revision date should be after the previous revisions revision date.")

        ;; Verify granule was deleted
        (is (= {:status 404} (util/get-concept-by-id-and-revision (:concept-id gran1) 1)))
        (is (= {:status 404} (util/get-concept-by-id-and-revision (:concept-id gran1) 2)))

        ;; verify variable association is deleted
        (is (= true (is-association-tombstone? variable-association)))
        ;; verify service association is deleted
        (is (= true (is-association-tombstone? service-association)))
        ;; verify tool association is deleted
        (is (= true (is-association-tombstone? tool-association)))

        ;; Other data left in database
        (util/verify-concept-was-saved coll2)
        (util/verify-concept-was-saved gran3)))))

(deftest delete-collection-using-save-end-point-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [coll1 (concepts/create-and-save-concept :collection provider-id 1 3)
          coll1-concept-id (:concept-id coll1)
          gran1 (concepts/create-and-save-concept :granule provider-id coll1 1 2)
          coll2 (concepts/create-and-save-concept :collection provider-id 2)
          gran3 (concepts/create-and-save-concept :granule provider-id coll2 2)
          variable (concepts/create-and-save-concept :variable provider-id 1 1 {:coll-concept-id coll1-concept-id})
          var-assn-concept-id (get-in variable [:variable-association :concept-id])
          variable-association (:concept (util/get-concept-by-id var-assn-concept-id))
          service (concepts/create-and-save-concept :service provider-id 1)
          service-association (concepts/create-and-save-concept :service-association coll1 service 1 1)
          tool (concepts/create-and-save-concept :tool provider-id 1)
          tool-association (concepts/create-and-save-concept :tool-association coll1 tool 1 1)]

      ;; variable association is not a tombstone before collection is deleted
      (is (= false (is-association-tombstone? variable-association)))
      ;; service association is not a tombstone before collection is deleted
      (is (= false (is-association-tombstone? service-association)))
      ;; tool association is not a tombstone before collection is deleted
      (is (= false (is-association-tombstone? tool-association)))

      ;; now delete coll1
      (let [{:keys [status revision-id]} (util/save-concept {:concept-id (:concept-id coll1)
                                                             :deleted true
                                                             :user-id "user101"})
            deleted-coll1 (:concept (util/get-concept-by-id-and-revision
                                     (:concept-id coll1) revision-id))
            saved-coll1 (:concept (util/get-concept-by-id-and-revision
                                   (:concept-id coll1) (dec revision-id)))]

        (is (= {:status 201
                :revision-id 4}
               {:status status
                :revision-id revision-id}))

        ;; Make sure that the saved tombstone has expected concept-id, revision-id,
        ;; user-id, empty metadata, and deleted = true.
        (assert-collection-tombstone saved-coll1 deleted-coll1 revision-id "user101")

        ;; make sure that a deleted collection gets it's own unique transaction-id
        (is (> (:transaction-id deleted-coll1) (:transaction-id saved-coll1)))

        ;; Make sure that a deleted collection gets it's own unique revision date
        (is (t/after? (:revision-date deleted-coll1) (:revision-date saved-coll1))
            "The deleted collection revision date should be after the previous revisions revision date.")

        ;; Verify granule was deleted
        (is (= {:status 404} (util/get-concept-by-id-and-revision (:concept-id gran1) 1)))
        (is (= {:status 404} (util/get-concept-by-id-and-revision (:concept-id gran1) 2)))

        ;; verify variable association is deleted
        (is (= true (is-association-tombstone? variable-association)))
        ;; verify service association is deleted
        (is (= true (is-association-tombstone? service-association)))
        ;; verify tool association is deleted
        (is (= true (is-association-tombstone? tool-association)))

        ;; Other data left in database
        (util/verify-concept-was-saved coll2)
        (util/verify-concept-was-saved gran3)))))

(deftest delete-concept-failure-cases
  (let [coll-reg-prov (concepts/create-and-save-concept :collection "REG_PROV" 1)
        coll-small-prov (concepts/create-and-save-concept :collection "SMAL_PROV" 1)]
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
