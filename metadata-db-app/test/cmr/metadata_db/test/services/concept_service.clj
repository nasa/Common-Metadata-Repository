(ns cmr.metadata-db.test.services.concept-service
  "Contains unit tests for service layer methods and associated utility methods."
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [clojure.test.check :as tc]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [cmr.common.test.test-util :as tu]
   [cmr.metadata-db.data.concepts :as c]
   [cmr.metadata-db.data.memory-db :as memory]
   [cmr.metadata-db.services.concept-service :as cs]
   [cmr.metadata-db.services.messages :as messages]
   [cmr.metadata-db.services.provider-validation :as pv]
   [cmr.metadata-db.services.util :as util])
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
