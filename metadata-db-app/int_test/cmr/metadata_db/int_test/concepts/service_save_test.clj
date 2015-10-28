(ns cmr.metadata-db.int-test.concepts.service-save-test
  "Contains integration tests for saving services. Tests saves with various configurations including
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
(deftest save-service-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept (util/service-concept provider-id 1)
          {:keys [status revision-id concept-id]} (util/save-concept concept)]
      (is (= 201 status))
      (is (= 1 revision-id))
      (util/verify-concept-was-saved (assoc concept :revision-id revision-id :concept-id concept-id)))))

(deftest save-service-with-concept-id
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [serv-concept-id (str "S10-" provider-id)
          service (util/service-concept provider-id 1 {:concept-id serv-concept-id})
          {:keys [status revision-id concept-id]} (util/save-concept service)]
      (is (= 201 status))
      (is (= revision-id 1))
      (util/verify-concept-was-saved (assoc service :revision-id revision-id :concept-id concept-id))

      (testing "with incorrect native id"
        (let [response (util/save-concept (assoc service :native-id "foo"))]
          (is (= {:status 409,
                  :errors [(msg/concept-exists-with-different-id
                             serv-concept-id (:native-id service)
                             serv-concept-id "foo"
                             :service provider-id) ]}
                 (select-keys response [:status :errors])))))

      (testing "with incorrect concept id"
        (let [other-serv-concept-id (str "S11-" provider-id)
              response (util/save-concept (assoc service :concept-id other-serv-concept-id))]
          (is (= {:status 409,
                  :errors [(msg/concept-exists-with-different-id
                             serv-concept-id (:native-id service)
                             other-serv-concept-id (:native-id service)
                             :service provider-id) ]}
                 (select-keys response [:status :errors])))))

      (testing "with incorrect concept id matching another concept"
        (let [other-serv-concept-id (str "S11-" provider-id)
              service2 (util/service-concept provider-id 2
                                             {:concept-id other-serv-concept-id
                                              :native-id "native2"})
              _ (is (= 201 (:status (util/save-concept service2))))
              response (util/save-concept (assoc service :concept-id other-serv-concept-id))]
          (is (= {:status 409,
                  :errors [(msg/concept-exists-with-different-id
                             serv-concept-id (:native-id service)
                             other-serv-concept-id (:native-id service)
                             :service provider-id) ]}
                 (select-keys response [:status :errors]))))))))

(deftest save-service-with-revision-date-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept (util/service-concept provider-id 1 {:revision-date (t/date-time 2001 1 1 12 12 14)})
          {:keys [status revision-id concept-id]} (util/save-concept concept)]
      (is (= 201 status))
      (is (= revision-id 1))
      (let [retrieved-concept (util/get-concept-by-id-and-revision concept-id revision-id)]
        (is (= (:revision-date concept) (:revision-date (:concept retrieved-concept))))))))

(deftest save-service-with-bad-revision-date-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept (util/service-concept provider-id 1 {:revision-date "foo"})
          {:keys [status errors]} (util/save-concept concept)]
      (is (= 422 status))
      (is (= ["[foo] is not a valid datetime"] errors)))))

(deftest save-service-with-same-native-id-test
  (testing "Save services with the same native-id for two small providers is OK"
    (let [serv1 (util/create-and-save-service "SMAL_PROV1" 1 1 {:native-id "foo"})
          serv2 (util/create-and-save-service "SMAL_PROV2" 2 1 {:native-id "foo"})
          [serv1-concept-id serv2-concept-id] (map :concept-id [serv1 serv2])]
      (util/verify-concept-was-saved serv1)
      (util/verify-concept-was-saved serv2)
      (is (not= serv1-concept-id serv2-concept-id)))))

(deftest save-service-test-with-proper-revision-id-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept (util/service-concept provider-id 1)]
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
          (util/verify-concept-was-saved updated-concept))))))

(deftest save-service-with-skipped-revisions-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept (util/service-concept provider-id 1)
          {:keys [concept-id]} (util/save-concept concept)
          concept-with-skipped-revisions (assoc concept :concept-id concept-id :revision-id 100)
          {:keys [status revision-id]} (util/save-concept concept-with-skipped-revisions)
          {retrieved-concept :concept} (util/get-concept-by-id concept-id)]
      (is (= 201 status))
      (is (= 100 revision-id (:revision-id retrieved-concept))))))

(deftest auto-increment-of-revision-id-works-with-skipped-revisions-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept (util/service-concept provider-id 1)
          {:keys [concept-id]} (util/save-concept concept)
          concept-with-concept-id (assoc concept :concept-id concept-id)
          _ (util/save-concept (assoc concept-with-concept-id :revision-id 100))
          {:keys [status revision-id]} (util/save-concept concept-with-concept-id)
          {retrieved-concept :concept} (util/get-concept-by-id concept-id)]
      (is (= 201 status))
      (is (= 101 revision-id (:revision-id retrieved-concept))))))

(deftest save-service-with-low-revision-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept (util/service-concept provider-id 1)
          {:keys [concept-id]} (util/save-concept concept)
          concept-with-bad-revision (assoc concept :concept-id concept-id :revision-id 0)
          {:keys [status revision-id]} (util/save-concept concept-with-bad-revision)]
      (is (= 409 status))
      (is (nil? revision-id)))))

(deftest save-service-with-missing-required-field
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [concept (util/service-concept provider-id 1)]
      (are [field] (let [{:keys [status errors]} (util/save-concept (dissoc concept field))]
                     (and (= 422 status)
                          (re-find (re-pattern (name field)) (first errors))))
           :concept-type
           :provider-id
           :native-id
           :extra-fields))))
