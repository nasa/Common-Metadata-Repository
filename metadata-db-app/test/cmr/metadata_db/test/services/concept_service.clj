(ns cmr.metadata-db.test.services.concept-service
  "Contains unit tests for service layer methods and associated utility methods."
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cmr.common.util :refer [are3]]
   [cmr.common.test.test-util :as tu]
   [cmr.metadata-db.data.concepts :as c]
   [cmr.metadata-db.data.memory-db :as memory]
   [cmr.metadata-db.services.concept-service :as cs]
   [cmr.metadata-db.services.messages :as messages]
   [cmr.metadata-db.services.provider-validation :as pv])
   #_{:clj-kondo/ignore [:unused-import]}
  (:import
   (clojure.lang ExceptionInfo)))

(use-fixtures :once tu/silence-logging-fixture)

(def example-concept
  {:concept-id "C1000000000-PROV1"
   :concept-type :collection
   :native-id "provider collection id"
   :provider-id "PROV1"
   :metadata "xml here"
   :format "echo10"
   :revision-id 1
   :transaction-id 1
   :extra-fields {:entry-title "ET-1"
                  :entry-id "EID-1"}})

(deftest split-concept-id-revision-id-tuples-test
  (testing "one pair"
    (is (= {"PROV1" {:collection [["C10-PROV1" 0]]}}
           (cs/split-concept-id-revision-id-tuples [["C10-PROV1" 0]]))))
  (testing "multiple"
    (let [tuples [["C10-PROV1" 0]
                  ["G1-PROV1" 1]
                  ["G2-PROV1" 5]
                  ["C1-PROV2" 1]
                  ["C2-PROV2" 5]]
          expected {"PROV1" {:collection [["C10-PROV1" 0]]
                             :granule [["G1-PROV1" 1]
                                       ["G2-PROV1" 5]]}
                    "PROV2" {:collection [["C1-PROV2" 1]
                                          ["C2-PROV2" 5]]}}]
      (is (= expected (cs/split-concept-id-revision-id-tuples tuples))))))

;;; Verify that the revision id check works as expected.
(deftest check-concept-revision-id-test
  (let [previous-concept example-concept
        db (memory/create-db [example-concept])]
    (testing "valid revision-id"
      (let [concept (assoc previous-concept :revision-id 2)]
        (is (= {:status :pass} (#'cs/check-concept-revision-id db {:provider-id "PROV1"} concept previous-concept)))))
    (testing "skipped revision-id"
      (let [concept (assoc previous-concept :revision-id 100)]
        (is (= {:status :pass} (#'cs/check-concept-revision-id db {:provider-id "PROV1"} concept previous-concept)))))
    (testing "invalid revision-id - low"
      (let [concept (assoc previous-concept :revision-id 0)
            result (#'cs/check-concept-revision-id db {:provider-id "PROV1"} concept previous-concept)]
        (is (= (:status result) :fail))
        (is (= (:expected result) 2))))))

;;; Verify that the revision id validation works as expected.
(deftest validate-concept-revision-id-test
  (let [previous-concept example-concept
        db (memory/create-db [example-concept])]
    (testing "valid concept revision-id"
      (let [concept (assoc previous-concept :revision-id 2)]
        (#'cs/validate-concept-revision-id db {:provider-id "PROV1"} concept previous-concept)))
    (testing "invalid concept revision-id"
      (let [concept (assoc previous-concept :revision-id 1)]
        (tu/assert-exception-thrown-with-errors
          :conflict
          [(messages/invalid-revision-id (:concept-id concept) 2 1)]
          (#'cs/validate-concept-revision-id db {:provider-id "PROV1"} concept previous-concept))))
    (testing "missing concept-id no revision-id"
      (let [concept (dissoc previous-concept :concept-id :revision-id)]
        (#'cs/validate-concept-revision-id db {:provider-id "PROV1"} concept previous-concept)))
    (testing "missing concept-id valid revision-id"
      (let [concept (-> previous-concept (dissoc :concept-id) (assoc :revision-id 5))]
        (#'cs/validate-concept-revision-id db {:provider-id "PROV1"} concept previous-concept)))
    (testing "missing concept-id invalid revision-id"
      (let [concept-id (:concept-id previous-concept)
            concept (-> previous-concept (dissoc :concept-id) (assoc :revision-id 1))]
        (tu/assert-exception-thrown-with-errors
          :conflict
          [(messages/invalid-revision-id concept-id 2 1)]
          (#'cs/validate-concept-revision-id db {:provider-id "PROV1"} concept previous-concept))))))

(deftest validate-system-level-tag-concept-test
  (let [tag {:concept-type :tag
             :short-name "TAG1"}
        cmr-provider pv/cmr-provider
        prov1 {:provider-id "PROV1"
               :short-name "PROV1"
               :cmr-only true
               :small false}]
    (is (= nil (cs/validate-system-level-concept tag cmr-provider)))
    (tu/assert-exception-thrown-with-errors
     :invalid-data
     ["Tag could not be associated with provider [PROV1]. Tags are system level entities."]
     (cs/validate-system-level-concept tag prov1))))

;;; Verify that the try-to-save logic is correct.
(deftest try-to-save-test
  (testing "must be called with a revision-id"
    (let [db (memory/create-db [example-concept])
          nil-context nil]
      (is (thrown-with-msg? AssertionError #"Assert failed: .*revision-id"
                            (cs/try-to-save db {:provider-id "PROV1"} nil-context
                                            (dissoc example-concept :revision-id))))))
  (testing "valid with revision-id"
    (let [db (memory/create-db [example-concept])
          nil-context nil
          result (cs/try-to-save db {:provider-id "PROV1"} nil-context (assoc example-concept :revision-id 2))]
      (is (= 2 (:revision-id result)))))
  (testing "conflicting concept-id and revision-id"
    (let [nil-context nil]
      (tu/assert-exception-thrown-with-errors
        :conflict
        [(messages/concept-id-and-revision-id-conflict (:concept-id example-concept) 1)]
        (cs/try-to-save (memory/create-db [example-concept])
                        {:provider-id "PROV1"}
                        nil-context
                        (assoc example-concept :revision-id 1))))))

(deftest delete-expired-concepts-test
  (testing "basic case"
    (let [expired (assoc-in example-concept [:extra-fields :delete-time] "1986-10-14T04:03:27.456Z")
          db (memory/create-db [expired])]
      (cs/delete-expired-concepts {:system {:db db}} {:provider-id "PROV1"} :collection)
      (is (empty? (c/get-expired-concepts db {:provider-id "PROV1"} :collection)))))
  (testing "with a conflict"
    (let [expired (assoc-in example-concept [:extra-fields :delete-time] "1986-10-14T04:03:27.456Z")
          db (memory/create-db [expired])
          ;; create a mock save function that, the first time it is
          ;; called, updates our expired concept before calling the
          ;; original save function so that a conflict occurs when
          ;; delete-expired-concepts runs
          expired-2 (-> expired (assoc :revision-id 2))
          orig-save cs/try-to-save
          saved (atom false)
          nil-context nil

          fake-save (fn [& args]
                      (when-not @saved
                        (orig-save db {:provider-id "PROV1"} nil-context expired-2)
                        (reset! saved true))
                      (apply orig-save args))]
      ;; replace cs/try-to-save with our overridden function for this test
      (with-bindings {#'cs/try-to-save fake-save}
        (cs/delete-expired-concepts {:system {:db db}} {:provider-id "PROV1"} :collection)
        (is @saved)

        ;; ensure that the cleanup failed and our concurrent update
        ;; went through
        (is (= [expired-2]
               (for [concept (c/get-expired-concepts db {:provider-id "PROV1"} :collection)]
                 (dissoc concept :revision-date :created-at))))

        ;; run it again, this time without the conflict...
        (cs/delete-expired-concepts {:system {:db db}} {:provider-id "PROV1"} :collection)
        (is (empty? (c/get-expired-concepts db {:provider-id "PROV1"} :collection)))))))

(def providers
  (list {:provider-id "CMR" :short-name "CMR" :system-level? true :cmr-only true :small false}
        {:provider-id "REG_PROV" :short-name "REG_PROV" :cmr-only false :small false}
        {:provider-id "SMAL_PROV" :short-name "SMAL_PROV" :cmr-only false :small true}))

(deftest concept-service-provider-test
  (let [provider-ids (map :provider-id providers)]
    (testing "Testing validate-providers-exist - the providers exist."
      (is (nil? (#'cs/validate-providers-exist provider-ids providers))))

    (testing "Testing validate-providers-exist - a provider does not exist."
      (tu/assert-exception-thrown-with-errors
       :not-found
       [(messages/providers-do-not-exist ["hello"])]
       (#'cs/validate-providers-exist (conj provider-ids "hello") providers)))

    (testing "Regroup concepts and filter out providers that don't exist in the concept id map."
      (let [concept-ids (list "G1200000003-REG_PROV"
                              "AG1200000005-REG_PROV"
                              "C1200000001-SMAL_PROV"
                              "C1200000002-SMAL_PROV"
                              "C12-HELLO_PROV")
            split-concept-ids-map (cs/split-concept-ids concept-ids)]
        (is (= {"REG_PROV"
                {:granule '("G1200000003-REG_PROV")
                 :access-group '("AG1200000005-REG_PROV")}
                "SMAL_PROV"
                {:collection '("C1200000002-SMAL_PROV" "C1200000001-SMAL_PROV")}}
                (#'cs/filter-non-existent-providers split-concept-ids-map providers)))))))

(def example-granule
  {:concept-id "G1000000001-PROV1"
   :deleted false
   :concept-type :granule
   :native-id "provider granule id"
   :provider-id "PROV1"
   :metadata "xml here"
   :format "echo10"
   :revision-id 1
   :transaction-id 1
   :extra-fields {:entry-title "ET-1"
                  :entry-id "EID-1"
                  :parent-collection-id "C1000000000-PROV1"}})

(deftest set-created-at-test
  (let [created-at "2000-05-22T00:00:00Z"
        db (memory/create-db [(assoc example-concept :created-at created-at)
                              (assoc example-granule :created-at created-at)])
        provider {:provider-id "PROV1"
                  :short-name "PROV1"
                  :cmr-only false
                  :small false}]
    (testing "set-created-at"
      (are3
       [expected example-record]
       (is (= expected (:created-at (cs/set-created-at db provider example-record))))

       "for a collection"
       created-at
       example-concept

       "for a granule"
       created-at
       example-granule

       "just returns concept that went in the function"
       nil
       nil))))

(deftest set-or-generate-concept-id-test
  (let [db (memory/create-db [example-concept
                              example-granule])
        provider {:provider-id "PROV1"
                  :short-name "PROV1"
                  :cmr-only false
                  :small false}]
    (testing "set-or-generate-concept-id"
      (are3
       [example-record]
       (is (some? (:concept-id (cs/set-or-generate-concept-id db provider example-record))))

       "for a collection"
       example-concept

       "for a granule"
       example-granule

       "sets a granule concept id."
       (dissoc example-granule :concept-id)

       "sets a collection concept id."
       (dissoc example-concept :concept-id)))))

;(deftest set-subscription-arn-test
;  (testing "set-subscription-arn"
;    (are3 [concept-type concept expected]
;          (is (= expected (cs/set-subscription-arn nil concept-type concept)))
;
;          "non-subscription concept type returns un-changed concept"
;          :granule
;          {:metadata {:EndPoint ""}}
;          {:metadata {:EndPoint ""}}
;
;          "empty endpoint returns un-changed concept"
;          :subscription
;          {:metadata {:EndPoint ""}}
;          {:metadata {:EndPoint ""}}
;
;          "url endpoint returns un-changed concept"
;          :subscription
;          {:metadata {:EndPoint "https://www.endpoint.com"}}
;          {:metadata {:EndPoint "https://www.endpoint.com"}}))
;   (with-redefs [cmr.metadata-db.services.subscriptions/attach-subscription-to-topic (fn [context concept] "sqs:arn")]
;     (testing "local test queue url endpoint returns changed concept"
;       (let [concept {:metadata {:EndPoint "http://localhost:9324/000000000/"}}
;             expected-concept {:metadata {:EndPoint "http://localhost:9324/000000000/"} :extra-fields {:aws-arn "sqs:arn"}}]
;         (is (= expected-concept (cs/set-subscription-arn nil :subscription concept)))))
;
;     (testing "sqs arn endpoint returns changed concept"
;       (let [concept {:metadata {:EndPoint "arn:aws:sqs:1234:Queue-Name"}}
;             expected-concept {:metadata {:EndPoint "arn:aws:sqs:1234:Queue-Name"} :extra-fields {:aws-arn "sqs:arn"}}]
;         (is (= expected-concept (cs/set-subscription-arn nil :subscription concept)))))))







