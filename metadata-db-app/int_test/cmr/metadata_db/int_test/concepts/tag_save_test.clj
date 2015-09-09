(ns cmr.metadata-db.int-test.concepts.tag-save-test
  "Contains integration tests for saving tags. Tests saves with various configurations including
  checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.local :as l]
            [cmr.common.util :refer (are2)]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.metadata-db.services.messages :as msg]
            [cmr.metadata-db.services.concept-constraints :as cc]))


;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-tag-test
  (testing "saving new tags"
    (are2 [tag exp-status exp-errors]
          (let [{:keys [status errors]} (util/save-concept tag)]
            (is (= exp-status status))
            (is (= (set exp-errors) (set errors))))

          "valid save"
          (util/tag-concept 1) 201 []

          "failure when using non system-level provider"
          (assoc (util/tag-concept 2) :provider-id "REG_PROV")
          422
          [(str "Tag could not be associated with provider [REG_PROV]. Tags are system level"
                " entities.")])))

(deftest save-tag-with-concept-id
  (testing "with concept-id"
    ;; valid save with concept-id
    (let [tag (util/tag-concept 1 {:concept-id "T10-CMR"})
          {:keys [status revision-id concept-id]} (util/save-concept tag)]
      (is (= 201 status))
      (is (= revision-id 1))
      (is (= true (util/verify-concept-was-saved
                    (assoc tag :revision-id revision-id :provider-id "CMR"))))

      (testing "new revision"
        (let [{:keys [status revision-id]} (util/save-concept (assoc tag :revision-id 2))]
          (is (= 201 status))
          (is (= revision-id 2))
          (is (util/verify-concept-was-saved
                (assoc tag :revision-id revision-id :provider-id "CMR")))))

      ;; error cases
      (are2 [test-tag exp-status exp-errors]
            (let [{:keys [status errors]} (util/save-concept test-tag)]
              (is (= exp-status status))
              (is (= (set exp-errors) (set errors))))

            "conflicting native-id"
            (assoc tag :native-id "foo")
            409
            [(str "A concept with concept-id [T10-CMR] and native-id [namespace1" (char 29)
                  "value1] already exists for concept-type [:tag] provider-id [CMR]. The given "
                  "concept-id [T10-CMR] and native-id [foo] would conflict with that one.")]


            "conflicting concept-id"
            (assoc tag :concept-id "T11-CMR")
            409
            [(str "A concept with concept-id [T10-CMR] and native-id [namespace1" (char 29)
                  "value1] already exists for concept-type [:tag] provider-id [CMR]. The given "
                  "concept-id [T11-CMR] and native-id [namespace1" (char 29) "value1] "
                  "would conflict with that one.")]

            "incorrect concept-id prefix"
            (assoc (util/tag-concept 2) :concept-id "G10-CMR")
            422
            [(str "Concept-id [G10-CMR] for concept does not match provider-id [CMR] or "
                  "concept-type [:tag].")]))))

