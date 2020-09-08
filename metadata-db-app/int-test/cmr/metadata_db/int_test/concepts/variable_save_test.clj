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

    ;; Uniqueness of variable name and association collection info is checked at association creation,
    ;; after the old fingerprint check is removed. However, many tests that assumed the old way of
    ;; ingesting variables without collection info are obsolete. They either need to be removed completely
    ;; or modified to suit the new variable ingest condition. The test cleanup ticket is CMR-6603.
    ;;(testing "save variable with the same data, but a different native id is not allowed"
     ;; (let [concept2 (assoc concept1 :native-id "different-native-id")
      ;;      {:keys [status errors]} (util/save-concept concept2)]
       ;; (is (= 409 status))
        ;;(is (= [(format (str "The Fingerprint of the variable which is defined by the variable's "
         ;;                    "Instrument short name, variable short name, units and dimensions "
          ;;                   "must be unique. The following variable with the same fingerprint "
           ;;                  "but different native id was found: [%s].")
            ;;            concept1-concept-id)]
             ;;  errors))))

    (testing "save variable with same data but different provider"
      (let [concept3 (assoc concept1 :provider-id "PROV2")
            {:keys [status concept-id revision-id]} (util/save-concept concept3)]
        (is (= status 201))
        (is (not= concept1-concept-id concept-id))
        (is (= 1 revision-id))))))
