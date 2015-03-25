(ns cmr.metadata-db.int-test.concepts.save-test
  "Contains integration tests for saving concepts. Tests saves with various configurations including
  checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.local :as l]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.metadata-db.services.messages :as msg]))


;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (util/reset-database-fixture "PROV1"))

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-collection-test
  (let [concept (util/collection-concept "PROV1" 1)
        {:keys [status revision-id concept-id]} (util/save-concept concept)]
    (is (= 201 status))
    (is (= revision-id 1))
    (is (util/verify-concept-was-saved (assoc concept :revision-id revision-id :concept-id concept-id)))))

(deftest save-collection-with-revision-date-test
  (let [concept (assoc (util/collection-concept "PROV1" 1)
                       :revision-date (t/date-time 2001 1 1 12 12 14))
        {:keys [status revision-id concept-id]} (util/save-concept concept)]
    (is (= 201 status))
    (is (= revision-id 1))
    (let [retrieved-concept (util/get-concept-by-id-and-revision concept-id revision-id)]
      (is (= (:revision-date concept) (:revision-date (:concept retrieved-concept)))))))

(deftest save-collection-with-bad-revision-date-test
  (let [concept (assoc (util/collection-concept "PROV1" 1) :revision-date "foo")
        {:keys [status errors]} (util/save-concept concept)]
    (is (= 422 status))
    (is (= ["[foo] is not a valid datetime"] errors))))

(deftest save-collection-without-version-id-test
  (let [concept (assoc-in (util/collection-concept "PROV1" 1) [:extra-fields :version-id] nil)
        {:keys [status revision-id concept-id]} (util/save-concept concept)]
    (is (= 201 status))
    (is (= revision-id 1))
    (is (util/verify-concept-was-saved (assoc concept :revision-id revision-id :concept-id concept-id)))))

(deftest save-granule-test
  (let [collection (util/collection-concept "PROV1" 1)
        parent-collection-id (:concept-id (util/save-concept collection))
        granule (util/granule-concept "PROV1" parent-collection-id 1)
        {:keys [status revision-id concept-id] :as resp} (util/save-concept granule)]
    (is (= 201 status) (pr-str resp))
    (is (= revision-id 1))
    (is (util/verify-concept-was-saved (assoc granule :revision-id revision-id :concept-id concept-id)))))

(deftest save-concept-test-with-proper-revision-id-test
  (let [concept (util/collection-concept "PROV1" 1)]
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
        (is (util/verify-concept-was-saved updated-concept))))))

(deftest save-concept-with-bad-revision-test
  (let [concept (util/collection-concept "PROV1" 1)
        {:keys [concept-id]} (util/save-concept concept)
        concept-with-bad-revision (assoc concept :concept-id concept-id :revision-id 3)
        {:keys [status]} (util/save-concept concept-with-bad-revision)
        {:keys [retrieved-concept]} (util/get-concept-by-id (:concept-id concept))
        retrieved-revision (:revision-id retrieved-concept)]
    (is (= 409 status))
    (is (nil? retrieved-revision))))

(deftest save-concept-with-low-revision-test
  (let [concept (util/collection-concept "PROV1" 1)
        {:keys [concept-id]} (util/save-concept concept)
        concept-with-bad-revision (assoc concept :concept-id concept-id :revision-id 0)
        {:keys [status]} (util/save-concept concept-with-bad-revision)
        {:keys [retrieved-concept]} (util/get-concept-by-id (:concept-id concept))
        retrieved-revision (:revision-id retrieved-concept)]
    (is (= 409 status))
    (is (nil? retrieved-revision))))

(deftest save-concept-with-revision-id-0
  (let [concept-with-bad-revision (assoc (util/collection-concept "PROV1" 1) :revision-id 0)
        {:keys [status]} (util/save-concept concept-with-bad-revision)]
    (is (= 409 status))))

(deftest save-concept-with-missing-required-parameter
  (let [concept (util/collection-concept "PROV1" 1)]
    (are [field] (let [{:keys [status errors]} (util/save-concept (dissoc concept field))]
                   (and (= 422 status)
                        (re-find (re-pattern (name field)) (first errors))))
         :concept-type
         :provider-id
         :native-id
         :extra-fields)))

(deftest save-concept-after-delete
  (let [concept (util/collection-concept "PROV1" 1)
        {:keys [concept-id]} (util/save-concept concept)]
    (is (= 200 (:status (util/delete-concept concept-id))))
    (let [{:keys [status revision-id]} (util/save-concept concept)]
      (is (= 201 status))
      (is (= revision-id 3)))))

(deftest save-concept-after-delete-invalid-revision-id
  (let [concept (util/collection-concept "PROV1" 1)
        {:keys [concept-id]} (util/save-concept concept)]
    (is (= 200 (:status (util/delete-concept concept-id))))
    (let [{:keys [status revision-id]} (util/save-concept (assoc concept :revision-id 0))]
      (is (= 409 status)))))

(deftest save-granule-with-concept-id
  (let [collection (util/collection-concept "PROV1" 1)
        parent-collection-id (:concept-id (util/save-concept collection))
        granule (util/granule-concept "PROV1" parent-collection-id 1 "G10-PROV1")
        {:keys [status revision-id concept-id]} (util/save-concept granule)]
    (is (= 201 status))
    (is (= revision-id 1))
    (is (util/verify-concept-was-saved (assoc granule :revision-id revision-id :concept-id concept-id)))

    (testing "with incorrect native id"
      (let [response (util/save-concept (assoc granule :native-id "foo"))]
        (is (= {:status 409,
                :errors [(msg/concept-exists-with-different-id
                           "G10-PROV1" (:native-id granule)
                           "G10-PROV1" "foo"
                           :granule "PROV1") ]}
               (select-keys response [:status :errors])))))

    (testing "with incorrect concept id"
      (let [response (util/save-concept (assoc granule :concept-id "G11-PROV1"))]
        (is (= {:status 409,
                :errors [(msg/concept-exists-with-different-id
                           "G10-PROV1" (:native-id granule)
                           "G11-PROV1" (:native-id granule)
                           :granule "PROV1") ]}
               (select-keys response [:status :errors])))))

    (testing "with incorrect concept id matching another concept"
      (let [granule2 (assoc (util/granule-concept "PROV1" parent-collection-id 1 "G11-PROV1")
                            :native-id "native2")
            _ (is (= 201 (:status (util/save-concept granule2))))
            response (util/save-concept (assoc granule :concept-id "G11-PROV1"))]
        (is (= {:status 409,
                :errors [(msg/concept-exists-with-different-id
                           "G10-PROV1" (:native-id granule)
                           "G11-PROV1" (:native-id granule)
                           :granule "PROV1") ]}
               (select-keys response [:status :errors])))))))

(deftest save-granule-with-nil-required-field
  (testing "nil parent-collection-id"
    (let [granule (util/granule-concept "PROV1" nil 1)
          {:keys [status revision-id concept-id]} (util/save-concept granule)]
      (is (= 422 status))
      (is (not (util/verify-concept-was-saved (assoc granule :revision-id revision-id :concept-id concept-id)))))))

(deftest save-concept-with-invalid-json-test
  (let [response (client/post util/concepts-url
                              {:body "some non-json"
                               :body-encoding "UTF-8"
                               :content-type :json
                               :accept :json
                               :throw-exceptions false})]
    (is (= 400 (:status response)))
    (is (= "Malformed JSON in request body." (:body response)))))

;; TODO refactor/improve this test
(deftest save-collection-post-commit-constraint-violations
  (testing "duplicate entry titles"
    (let [existing-collection (assoc (util/collection-concept "PROV1" 1 {:entry-title "ET-1"})
                                     :concept-id "C1-PROV1"
                                     :revision-id 1)
          test-collection (assoc (util/collection-concept "PROV1" 2 {:entry-title "ET-1"})
                                 :concept-id "C2-PROV1"
                                 :revision-id 1)
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
                                               {:entry-title "ET-1" :provider-id "PROV1"})]
        (is (= [existing-collection]
               (map #(dissoc % :revision-date) (:concepts found-concepts))))))))

;; TODO write this test if needed
#_(deftest save-granule-post-commit-constraint-violations
  (testing "duplicate granule URs"
    (let [collection (util/collection-concept "PROV1" 1)
          parent-collection-id (:concept-id (util/save-concept collection))
          existing-granule (util/granule-concept "PROV1" parent-collection-id 1)

          ;; TODO I was here -
          (assoc (util/collection-concept "PROV1" 1 {:entry-title "ET-1"})
                                     :concept-id "C1-PROV1"
                                     :revision-id 1)
          test-collection (assoc (util/collection-concept "PROV1" 2 {:entry-title "ET-1"})
                                 :concept-id "C2-PROV1"
                                 :revision-id 1)
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
                                               {:entry-title "ET-1" :provider-id "PROV1"})]
        (is (= [existing-collection]
               (map #(dissoc % :revision-date) (:concepts found-concepts))))))))

