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

(deftest save-variable-with-conflicting-native-id
  (let [concept (concepts/create-concept :variable "PROV1" 1)]
    (util/concept-with-conflicting-native-id-assertions
     "variable"
     :variable-name
     concept
     "var-native-different")))
