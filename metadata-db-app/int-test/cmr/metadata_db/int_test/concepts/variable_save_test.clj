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

(defmethod c-spec/gen-concept :variable
  [_ provider-id uniq-num attributes]
  (concepts/create-concept :variable provider-id uniq-num attributes))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest save-variable
  (c-spec/general-save-concept-test :variable ["PROV1" "PROV2"]))

(deftest save-variable-with-missing-required-parameters
  (c-spec/save-test-with-missing-required-parameters
    :variable ["PROV1"] [:concept-type :provider-id :native-id :extra-fields]))

(deftest save-variable-created-at
  (let [concept (concepts/create-concept :variable "PROV1" 2)]
    (util/concept-created-at-assertions "variable" concept)))

(deftest save-variable-with-same-variable-name
  (let [concept1 (concepts/create-concept :variable "PROV1" 1)
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
    (testing "save variable with the same data, but a different native id creates a new variable"
      (let [concept2 (assoc concept1 :native-id "different-native-id")
            {:keys [status concept-id revision-id]} (util/save-concept concept2)]
        ;; We allow variables with the same variable name
        ;; to be saved within the same provider using a different native id
        (is (= 201 status))
        (is (not= concept1-concept-id concept-id))
        (is (= 1 revision-id))))
    (testing "save variable with same data but different provider"
      (let [concept3 (assoc concept1 :provider-id "PROV2")
            {:keys [status concept-id revision-id]} (util/save-concept concept3)]
        (is (= status 201))
        (is (not= concept1-concept-id concept-id))
        (is (= 1 revision-id))))))
