(ns cmr.metadata-db.int-test.concepts.subscription-save-test
  "Contains integration tests for saving subscriptions. Tests saves with various configurations including
  checking for proper error handling."
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
(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}))

(defmethod c-spec/gen-concept :subscription
  [_ _ uniq-num attributes]
  (concepts/create-concept :subscription "CMR" uniq-num attributes))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest save-subscription-test
  (testing "basic save"
    (let [concept (c-spec/gen-concept :subscription "CMR" 1 {})]
      (c-spec/save-concept-test concept 201 1 nil)
      (testing "save again"
        (c-spec/save-concept-test concept 201 2 nil))))

  (testing "save after delete"
    (let [concept (c-spec/gen-concept :subscription "CMR" 1 {})
          {:keys [email-address concept-id revision-id metadata]} (util/save-concept concept)
          delete-response (util/delete-concept concept-id)]
      (is (= 201 (:status delete-response)))
      (is (= (inc revision-id) (:revision-id delete-response)))
      (c-spec/save-concept-test concept 201 (+ revision-id 2) nil))))

(deftest save-subscription-failures-test
  (testing "saving invalid subscription"
    (are3 [subscription exp-status exp-errors]
          (let [{:keys [status errors]} (util/save-concept subscription)]
            (is (= exp-status status))
            (is (= (set exp-errors) (set errors))))
          
          "subscription associated with non system-level provider"
          (assoc (concepts/create-concept :subscription "CMR" 2) :provider-id "REG_PROV")
          422
          ["Subscription could not be associated with provider [REG_PROV]. Subscription is system level entity."])))

(deftest force-delete-subscription-test
  "Testing physically removing a specific revision of a subscription from the database."
  (cd-spec/general-force-delete-test :subscription ["CMR"]))

(deftest find-subscriptions
  (let [subscription (concepts/create-and-save-concept :subscription "CMR" 1 5)]
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

(deftest find-subscriptions-with-invalid-parameters
  (testing "extra parameters"
    (is (= {:status 400
            :errors ["Finding concept type [subscription] with parameters [provider-id] is not supported."]}
           (util/find-concepts :subscription {:provider-id "REG_PROV"})))))

(deftest find-subscriptions-with-latest-true
  (let [subscription (concepts/create-and-save-concept :subscription "CMR" 1 5)
        latest-subscription (util/find-concepts :subscription {:latest true})]
    (testing "no parameters latest true search"
      (is (= 200 
             (:status latest-subscription)))
      (is (= 5
             (:revision-id (first (:concepts latest-subscription)))))))) 
