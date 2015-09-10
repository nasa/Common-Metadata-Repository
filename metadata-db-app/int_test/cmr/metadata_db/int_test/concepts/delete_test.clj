(ns cmr.metadata-db.int-test.concepts.delete-test
  "Contains integration tests for deleting concepts. Tests delete with various configurations including
  checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.metadata-db.services.messages :as messages]
            [cmr.common.util :as u]
            [clj-time.core :as t]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV" :small true}))

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest delete-collection-using-delete-end-point-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [coll1 (util/create-and-save-collection provider-id 1 3)
          gran1 (util/create-and-save-granule provider-id (:concept-id coll1) 1 2)
          coll2 (util/create-and-save-collection provider-id 2)
          gran3 (util/create-and-save-granule provider-id (:concept-id coll2) 1)
          {:keys [status revision-id]} (util/delete-concept (:concept-id coll1))
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

(deftest delete-collection-using-save-end-point-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [coll1 (util/create-and-save-collection provider-id 1 3)
          gran1 (util/create-and-save-granule provider-id (:concept-id coll1) 1 2)
          coll2 (util/create-and-save-collection provider-id 2)
          gran3 (util/create-and-save-granule provider-id (:concept-id coll2) 1)
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
      (is (= {:status 201
              :revision-id 4}
             {:status status
              :revision-id revision-id})))))

(deftest delete-granule-using-delete-end-point-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [parent-coll-id (:concept-id (util/create-and-save-collection provider-id 1))
          gran1 (util/create-and-save-granule provider-id parent-coll-id 1 3)
          gran2 (util/create-and-save-granule provider-id parent-coll-id 2)
          {:keys [status revision-id]} (util/delete-concept (:concept-id gran1))
          stored-gran1 (:concept (util/get-concept-by-id-and-revision (:concept-id gran1) revision-id))]
      (is (= {:status 201
              :revision-id 4
              :deleted true
              :metadata ""}
             {:status status
              :revision-id revision-id
              :deleted (:deleted stored-gran1)
              :metadata (:metadata stored-gran1)}))

      ;; Other data left in database
      (is (util/verify-concept-was-saved gran2)))))

(deftest delete-granule-using-save-end-point-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [parent-coll-id (:concept-id (util/create-and-save-collection provider-id 1))
          gran1 (util/create-and-save-granule provider-id parent-coll-id 1 3)
          gran2 (util/create-and-save-granule provider-id parent-coll-id 2)
          {:keys [status revision-id]} (util/save-concept {:concept-id (:concept-id gran1)
                                                           :deleted true})
          stored-gran1 (:concept (util/get-concept-by-id-and-revision (:concept-id gran1) revision-id))]
      (is (= {:status 201
              :revision-id 4
              :deleted true
              :metadata ""}
             {:status status
              :revision-id revision-id
              :deleted (:deleted stored-gran1)
              :metadata (:metadata stored-gran1)}))

      ;; Other data left in database
      (is (util/verify-concept-was-saved gran2)))))

(deftest delete-granule-with-valid-revision-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [parent-coll-id (:concept-id (util/create-and-save-collection provider-id 1))
          gran1 (util/create-and-save-granule provider-id parent-coll-id 1 3)
          {:keys [status revision-id]} (util/delete-concept (:concept-id gran1) 4)]
      (is (= {:status 201
              :revision-id 4}
             {:status status
              :revision-id revision-id})))))

(deftest delete-tag-using-delete-end-point-test
  (let [tag1 (util/create-and-save-tag 1 3)
        tag2 (util/create-and-save-tag 2)
        {:keys [status revision-id]} (util/delete-concept (:concept-id tag1))
        stored-tag1 (:concept (util/get-concept-by-id-and-revision (:concept-id tag1) revision-id))]
    (is (= {:status 201
            :revision-id 4
            :deleted true
            :metadata ""}
           {:status status
            :revision-id revision-id
            :deleted (:deleted stored-tag1)
            :metadata (:metadata stored-tag1)}))

    ;; Other data left in database
    (is (util/verify-concept-was-saved (assoc tag2 :provider-id "CMR")))))

(deftest delete-tag-using-save-end-point-test
  (let [tag1 (util/create-and-save-tag 1 3)
        tag2 (util/create-and-save-tag 2)
        {:keys [status revision-id]} (util/save-concept {:concept-id (:concept-id tag1)
                                                         :deleted true})
        stored-tag1 (:concept (util/get-concept-by-id-and-revision (:concept-id tag1) revision-id))]
    (is (= {:status 201
            :revision-id 4
            :deleted true
            :metadata ""}
           {:status status
            :revision-id revision-id
            :deleted (:deleted stored-tag1)
            :metadata (:metadata stored-tag1)}))

    ;; Other data left in database
    (is (util/verify-concept-was-saved (assoc tag2 :provider-id "CMR")))))

(deftest delete-tag-with-valid-revision-test
  (let [tag1 (util/create-and-save-tag 1 3)
        {:keys [status revision-id]} (util/delete-concept (:concept-id tag1) 4)]
    (is (= {:status 201
              :revision-id 4}
             {:status status
              :revision-id revision-id}))))

(deftest delete-concept-with-skipped-revisions-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [coll1 (util/create-and-save-collection provider-id 1)
          {:keys [status revision-id]} (util/delete-concept (:concept-id coll1) 100)]
      (is (= {:status 201
              :revision-id 100}
             {:status status
              :revision-id revision-id})))))

(deftest delete-deleted-concept-with-new-revision-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [coll1 (util/create-and-save-collection provider-id 1)
          {status1 :status revision-id1 :revision-id} (util/delete-concept (:concept-id coll1) 5)
          {status2 :status revision-id2 :revision-id} (util/delete-concept (:concept-id coll1) 7)]
      (is (= 201 status1 status2))
      (is (= 5 revision-id1))
      (is (= 7 revision-id2)))))

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

(deftest repeated-calls-to-delete-get-same-revision
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [coll1 (util/create-and-save-collection provider-id 1)]
      (is (= (util/delete-concept (:concept-id coll1))
             (util/delete-concept (:concept-id coll1))
             (util/delete-concept (:concept-id coll1)))))))


