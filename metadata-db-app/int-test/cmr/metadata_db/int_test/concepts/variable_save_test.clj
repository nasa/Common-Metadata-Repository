(ns cmr.metadata-db.int-test.concepts.variable-save-test
  "Contains integration tests for saving variables. Tests saves with various configurations including
  checking for proper error handling."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer (are3)]
   [cmr.metadata-db.int-test.concepts.concept-save-spec :as c-spec]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Fixtures & one-off utility functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (util/reset-database-fixture
                     {:provider-id "PROV1" :small false}
                     {:provider-id "PROV2" :small false}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest save-variable-and-association
  (c-spec/general-save-concept-test :variable ["PROV1" "PROV2"]))

(deftest save-variable-with-missing-required-parameters
  (c-spec/save-test-with-missing-required-parameters
    :variable ["PROV1"] [:concept-type :provider-id :native-id :extra-fields]))

(deftest save-variable-created-at
  (let [coll (concepts/create-and-save-concept :collection "PROV1" 1 1)
        coll-concept-id (:concept-id coll)
        attributes {:coll-concept-id coll-concept-id}
        concept (concepts/create-concept :variable "PROV1" 2 attributes)]
    (util/concept-created-at-assertions "variable" concept)))

(deftest save-variable-with-same-variable-name
  (let [coll (concepts/create-and-save-concept :collection "PROV1" 1 1)
        coll-concept-id (:concept-id coll)
        attributes {:coll-concept-id coll-concept-id}
        concept1 (concepts/create-concept :variable "PROV1" 1 attributes)
        {status :status
         revision-id :revision-id
         concept1-concept-id :concept-id} (util/save-concept concept1)]
    ;; verify variable is saved and the revision id is 1
    (is (= status 201))
    (is (= 1 revision-id))

    (testing "save variable with all the same data, i.e. update the variable"
      (let [{:keys [status concept-id revision-id]} (util/save-concept concept1)]
        (is (= 201 status))
        (is (= concept1-concept-id concept-id))
        (is (= 2 revision-id))))

    (testing "save variable with same data but different provider"
      (let [concept3 (assoc concept1 :provider-id "PROV2")
            {:keys [errors status concept-id revision-id]} (util/save-concept concept3)]
        (is (= status 409))
        (is (re-matches
             (re-pattern (str "Variable \\[V.*-PROV2\\] and collection \\["
                              coll-concept-id
                              "\\] can not be associated because they do not belong to the same provider."))
             (first errors)))))

    (testing "save variable with the same native-id, different variable name on the same provider"
      (let [concept4-var-name "different-variable-name"
            concept4 (update concept1 :extra-fields assoc :variable-name concept4-var-name)]
        (testing (str "save variable with the same native id, but a different variable name "
                      "on a non-deleted variable is allowed")
          (let [{:keys [status concept-id revision-id]} (util/save-concept concept4)]
            (is (= 201 status))
            (is (= concept1-concept-id concept-id))
            (is (= 3 revision-id))))))))
