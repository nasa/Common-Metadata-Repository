(ns cmr.metadata-db.int-test.concepts.humanizer-test
  "Contains integration tests for saving, deleting, force deleting and searching humanizers."
  (:require
   [clj-time.core :as t]
   [clojure.test :refer :all]
   [cmr.common.util :refer (are3)]
   [cmr.metadata-db.int-test.concepts.concept-delete-spec :as cd-spec]
   [cmr.metadata-db.int-test.concepts.concept-save-spec :as c-spec]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}))

(defmethod c-spec/gen-concept :humanizer
  [_ _ uniq-num attributes]
  (concepts/create-concept :humanizer "CMR" uniq-num attributes))
  ; (concept uniq-num attributes))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-humanizer-test
  (testing "basic save"
    (let [concept (c-spec/gen-concept :humanizer "CMR" 1 {})]
      (c-spec/save-concept-test concept 201 1 nil)
      (testing "save again"
        (c-spec/save-concept-test concept 201 2 nil))))

  (testing "save after delete"
    (let [concept (c-spec/gen-concept :humanizer "CMR" 1 {})
          {:keys [concept-id revision-id]} (util/save-concept concept)
          delete-response (util/delete-concept concept-id)]
      (is (= 201 (:status delete-response)))
      (is (= (inc revision-id) (:revision-id delete-response)))
      (c-spec/save-concept-test concept 201 (+ revision-id 2) nil))))

(deftest save-humanizer-failures-test
  (testing "saving invalid humanizer"
    (are3 [humanizer exp-status exp-errors]
          (let [{:keys [status errors]} (util/save-concept humanizer)]
            (is (= exp-status status))
            (is (= (set exp-errors) (set errors))))

          "humanizer associated with non system-level provider"
          (assoc (concepts/create-concept :humanizer "CMR" 2) :provider-id "REG_PROV")
          422
          ["Humanizer could not be associated with provider [REG_PROV]. Humanizer is system level entity."]

          "humanizer's native-id is not humanizer"
          (assoc (concepts/create-concept :humanizer "CMR" 2) :native-id "humanizer-1")
          422
          ["Humanizer concept native-id can only be [humanizer], but was [humanizer-1]."])))

(deftest delete-humanizer-test
  (testing "with delete endpoint"
    (let [concept1 (c-spec/gen-concept :humanizer "CMR" 1 {})
          {:keys [concept-id]} (util/save-concept concept1 3)
          {:keys [status revision-id] :as tombstone} (util/delete-concept concept-id)
          deleted-concept1 (:concept (util/get-concept-by-id-and-revision concept-id revision-id))
          saved-concept1 (:concept (util/get-concept-by-id-and-revision concept-id (dec revision-id)))]
      (is (= {:status 201 :revision-id 4}
             {:status status :revision-id revision-id}))

      (is (= (dissoc (assoc saved-concept1
                            :deleted true
                            :metadata ""
                            :revision-id revision-id
                            :user-id nil)
                     :revision-date :user-id :transaction-id)
             (dissoc deleted-concept1 :revision-date :user-id :transaction-id)))

      ;; Make sure that a deleted concept gets it's own unique revision date
      (is (t/after? (:revision-date deleted-concept1) (:revision-date saved-concept1))
          "The deleted concept revision date should be after the previous revisions revision date.")

      ;; Make sure transaction-ids increment
      (is (> (:transaction-id deleted-concept1) (:transaction-id saved-concept1)))

      ;; Delete a tombstone is OK and does nothing
      (is (= (util/delete-concept concept-id)
             (util/delete-concept concept-id)
             (util/delete-concept concept-id)))))

  (testing "with save endpoint"
    (let [concept1 (c-spec/gen-concept :humanizer "CMR" 1 {})
          {concept-id :concept-id prev-revision-id :revision-id} (util/save-concept concept1 3)
          {:keys [status revision-id]} (util/save-concept {:concept-id concept-id
                                                           :deleted true
                                                           :user-id "user101"})
          deleted-concept1 (:concept (util/get-concept-by-id-and-revision concept-id revision-id))
          saved-concept1 (:concept (util/get-concept-by-id-and-revision concept-id (dec revision-id)))]

      (is (= {:status 201 :revision-id (inc prev-revision-id)}
             {:status status :revision-id revision-id}))

      (is (= (dissoc (assoc saved-concept1
                            :deleted true
                            :metadata ""
                            :revision-id revision-id
                            :user-id nil)
                     :revision-date :user-id :transaction-id)
             (dissoc deleted-concept1 :revision-date :user-id :transaction-id)))

      ;; Make sure that a deleted concept gets it's own unique revision date
      (is (t/after? (:revision-date deleted-concept1) (:revision-date saved-concept1))
          "The deleted concept revision date should be after the previous revisions revision date.")

      ;; Make sure transaction-ids increment
      (is (> (:transaction-id deleted-concept1) (:transaction-id saved-concept1))))))

(deftest force-delete-humanizer-test
  (cd-spec/general-force-delete-test :humanizer ["CMR"]))

(deftest find-humanizers
  (let [humanizer (concepts/create-and-save-concept :humanizer "CMR" 1 5)]
    (testing "find latest revsions"
      (are3 [humanizers params]
            (= (set humanizers)
               (set (->> (util/find-latest-concepts :humanizer params)
                         :concepts
                         (map #(dissoc % :provider-id :revision-date :transaction-id)))))
            "with metadata"
            [humanizer] {}

            "exclude metadata"
            [(dissoc humanizer :metadata)] {:exclude-metadata true}))

    (testing "find all revisions"
      (let [num-of-humanizers (-> (util/find-concepts :humanizer {})
                                  :concepts
                                  count)]
        (is (= 5 num-of-humanizers))))))

(deftest find-humanizers-with-invalid-parameters
  (testing "extra parameters"
    (is (= {:status 400
            :errors ["Finding concept type [humanizer] with parameters [provider-id] is not supported."]}
           (util/find-concepts :humanizer {:provider-id "REG_PROV"})))))
