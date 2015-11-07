(ns cmr.metadata-db.int-test.concepts.group-save-test
  "Contains integration tests for saving groups. Tests saves with various configurations including
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

;; Set up REG_PROV as regular provider and SMAL_PROV1 as a small provider
(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV1" :small true}
                                                 {:provider-id "SMAL_PROV2" :small true}))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-group-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept (util/group-concept provider-id 1)
          {:keys [status revision-id concept-id]} (util/save-concept concept)]
      (is (= 201 status))
      (is (= 1 revision-id))
      (util/verify-concept-was-saved (assoc concept :revision-id revision-id :concept-id concept-id)))))

(deftest save-group-with-concept-id
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [group-concept-id (str "AG10-" provider-id)
          group (util/group-concept provider-id 1 {:concept-id group-concept-id})
          {:keys [status revision-id concept-id]} (util/save-concept group)]
      (is (= 201 status))
      (is (= revision-id 1))
      (util/verify-concept-was-saved (assoc group :revision-id revision-id :concept-id concept-id))

      (testing "with incorrect native id"
        (let [response (util/save-concept (assoc group :native-id "foo"))]
          (is (= {:status 409,
                  :errors [(msg/concept-exists-with-different-id
                             group-concept-id (:native-id group)
                             group-concept-id "foo"
                             :access-group provider-id)]}
                 (select-keys response [:status :errors])))))

      (testing "with incorrect concept id"
        (let [other-group-concept-id (str "AG11-" provider-id)
              response (util/save-concept (assoc group :concept-id other-group-concept-id))]
          (is (= {:status 409,
                  :errors [(msg/concept-exists-with-different-id
                             group-concept-id (:native-id group)
                             other-group-concept-id (:native-id group)
                             :access-group provider-id) ]}
                 (select-keys response [:status :errors])))))

      (testing "with incorrect concept id matching another concept"
        (let [other-group-concept-id (str "AG11-" provider-id)
              group2 (util/group-concept provider-id 2
                                             {:concept-id other-group-concept-id
                                              :native-id "native2"})
              _ (is (= 201 (:status (util/save-concept group2))))
              response (util/save-concept (assoc group :concept-id other-group-concept-id))]
          (is (= {:status 409,
                  :errors [(msg/concept-exists-with-different-id
                             group-concept-id (:native-id group)
                             other-group-concept-id (:native-id group)
                             :access-group provider-id) ]}
                 (select-keys response [:status :errors]))))))))