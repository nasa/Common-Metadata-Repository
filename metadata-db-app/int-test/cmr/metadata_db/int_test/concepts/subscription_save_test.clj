(ns cmr.metadata-db.int-test.concepts.subscription-save-test
  "Contains integration tests for saving subscriptions. Tests saves with various
  configurations including checking for proper error handling."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer (are3)]
   [cmr.metadata-db.int-test.concepts.concept-delete-spec :as cd-spec]
   [cmr.metadata-db.int-test.concepts.concept-save-spec :as c-spec]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Fixtures & one-off utility functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (util/reset-database-fixture
                     {:provider-id "PROV1" :small false}))

(defmethod c-spec/gen-concept :subscription
  [_ provider-id uniq-num attributes]
  (concepts/create-concept :subscription provider-id uniq-num attributes))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest save-subscription
  (c-spec/general-save-concept-test :subscription ["PROV1"]))

(deftest save-subscription-with-missing-required-parameters
  (c-spec/save-test-with-missing-required-parameters
   :subscription ["PROV1"] [:concept-type :provider-id :native-id :extra-fields]))

(deftest save-subscription-created-at
  (let [concept (concepts/create-concept :subscription "PROV1" 2)]
    (util/concept-created-at-assertions "subscription" concept)))

(deftest save-subscription-failures-test
  (testing "saving invalid subscription"
    (are3 [subscription exp-status exp-errors]
          (let [{:keys [status errors]} (util/save-concept subscription)]
            (is (= exp-status status))
            (is (= (set exp-errors) (set errors))))

          "subscription associated with provider that does not exist"
          (assoc (concepts/create-concept :subscription "PROV1" 2) :provider-id "REG_PROV1")
          404
          ["Provider with provider-id [REG_PROV1] does not exist."])))

(deftest force-delete-subscription-test
  ;; Testing physically removing a specific revision of a subscription from the database.
  (cd-spec/general-force-delete-test :subscription ["PROV1"]))

(deftest find-subscriptions
  (let [subscription (concepts/create-and-save-concept :subscription "PROV1" 1 5)]
    (testing "find latest revsions"
      (are3 [subscriptions params]
            (= (set subscriptions)
               (set (->> (util/find-latest-concepts :subscription params)
                         :concepts
                         (map #(dissoc % :provider-id :revision-date :transaction-id)))))
            "with metadata"
            [subscription] {}

            "exclude metadata"
            [(dissoc subscription :metadata)] {:exclude-metadata true}))

    (testing "find all revisions"
      (let [num-of-subscriptions (-> (util/find-concepts :subscription {})
                                  :concepts
                                  count)]
        (is (= 5 num-of-subscriptions))))))

(deftest find-subscriptions-with-latest-true
  (let [subscription (concepts/create-and-save-concept :subscription "PROV1" 1 5)
        latest-subscription (util/find-concepts :subscription {:latest true})]
    (testing "no parameters latest true search"
      (is (= 200
             (:status latest-subscription)))
      (is (= 5
             (:revision-id (first (:concepts latest-subscription))))))))
