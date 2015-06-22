(ns cmr.metadata-db.int-test.concepts.delete-test
  "Contains integration tests for deleting concepts. Tests delete with various configurations including
  checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.metadata-db.services.messages :as messages]
            [clj-time.core :as t]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV" :small true}))

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest delete-collection-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [coll1 (util/create-and-save-collection provider-id 1 3)
          gran1 (util/create-and-save-granule provider-id (:concept-id coll1) 1 2)
          coll2 (util/create-and-save-collection provider-id 2)
          gran3 (util/create-and-save-granule provider-id (:concept-id coll2) 1)
          {:keys [status revision-id]} (util/delete-concept (:concept-id coll1))
          deleted-coll1 (:concept (util/get-concept-by-id-and-revision (:concept-id coll1) revision-id))
          saved-coll1 (:concept (util/get-concept-by-id-and-revision (:concept-id coll1) (dec revision-id)))]
      (is (= status 200))
      (is (= revision-id 4))

      (is (= (dissoc (assoc saved-coll1
                            :deleted true
                            :metadata ""
                            :revision-id revision-id)
                     :revision-date)
             (dissoc deleted-coll1 :revision-date)))

      ;; Make sure that a deleted collection gets it's own unique revision date
      (is (t/after? (:revision-date deleted-coll1) (:revision-date saved-coll1))
          "The deleted collection revision date should be after the previous revisions revision date.")

      ;; Verify granule was deleted
      (is (= {:status 404} (util/get-concept-by-id-and-revision (:concept-id gran1) 1)))
      (is (= {:status 404} (util/get-concept-by-id-and-revision (:concept-id gran1) 2)))

      ;; Other data left in database
      (is (util/verify-concept-was-saved coll2))
      (is (util/verify-concept-was-saved gran3)))))

(deftest delete-collection-with-valid-revision-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [coll1 (util/create-and-save-collection provider-id 1 3)
          {:keys [status revision-id]} (util/delete-concept (:concept-id coll1) 4)]
      (is (= status 200))
      (is (= revision-id 4)))))

(deftest delete-granule-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [parent-coll-id (:concept-id (util/create-and-save-collection provider-id 1))
          gran1 (util/create-and-save-granule provider-id parent-coll-id 1 3)
          gran2 (util/create-and-save-granule provider-id parent-coll-id 2)
          {:keys [status revision-id]} (util/delete-concept (:concept-id gran1))
          stored-gran1 (:concept (util/get-concept-by-id-and-revision (:concept-id gran1) revision-id))]
      (is (= status 200))
      (is (= revision-id 4))
      (is (= true (:deleted stored-gran1)))
      (is (= "" (:metadata stored-gran1)))

      ;; Other data left in database
      (is (util/verify-concept-was-saved gran2)))))

(deftest delete-granule-with-valid-revision-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [parent-coll-id (:concept-id (util/create-and-save-collection provider-id 1))
          gran1 (util/create-and-save-granule provider-id parent-coll-id 1 3)
          {:keys [status revision-id]} (util/delete-concept (:concept-id gran1) 4)]
      (is (= status 200))
      (is (= revision-id 4)))))

(deftest delete-concept-with-skipped-revisions-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [coll1 (util/create-and-save-collection provider-id 1)
          {:keys [status revision-id]} (util/delete-concept (:concept-id coll1) 100)]
      (is (= status 200))
      (is (= revision-id 100)))))

(deftest delete-concept-with-invalid-revision
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [coll1 (util/create-and-save-collection provider-id 1)
          {:keys [status]} (util/delete-concept (:concept-id coll1) 0)]
      (is (= status 409)))))

(deftest delete-deleted-concept-with-new-revision-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [coll1 (util/create-and-save-collection provider-id 1)
          {status1 :status revision-id1 :revision-id} (util/delete-concept (:concept-id coll1) 5)
          {status2 :status revision-id2 :revision-id} (util/delete-concept (:concept-id coll1) 7)]
      (is (= 200 status1 status2))
      (is (= 5 revision-id1))
      (is (= 7 revision-id2)))))

(deftest fail-to-delete-missing-concept
  (let [{:keys [status revision-id errors]} (util/delete-concept "C100-REG_PROV")]
    (is (= status 404))
    (is (= errors [(messages/concept-does-not-exist "C100-REG_PROV")]))))

(deftest fail-to-delete-missing-concept-for-missing-provider
  (let [{:keys [status revision-id errors]} (util/delete-concept "C100-NONEXIST")]
    (is (= status 404))
    (is (= errors [(messages/providers-do-not-exist ["NONEXIST"])]))))

(deftest repeated-calls-to-delete-get-same-revision
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [coll1 (util/create-and-save-collection provider-id 1)]
      (is (= (util/delete-concept (:concept-id coll1))
             (util/delete-concept (:concept-id coll1))
             (util/delete-concept (:concept-id coll1)))))))

