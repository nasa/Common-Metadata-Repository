(ns cmr.metadata-db.int-test.concepts.tool-save-test
  "Contains integration tests for saving tools. Tests saves with various
  configurations including checking for proper error handling."
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

(defmethod c-spec/gen-concept :tool
  [_ provider-id uniq-num attributes]
  (concepts/create-concept :tool provider-id uniq-num attributes))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest save-tool
  (c-spec/general-save-concept-test :tool ["PROV1" "PROV2"]))

(deftest save-tool-with-missing-required-parameters
  (c-spec/save-test-with-missing-required-parameters
   :tool ["PROV1"] [:concept-type :provider-id :native-id :extra-fields]))

(deftest save-tool-created-at
  (let [concept (concepts/create-concept :tool "PROV1" 2)]
    (util/concept-created-at-assertions "tool" concept)))

(deftest save-tool-with-conflicting-native-id
  (let [concept (concepts/create-concept :tool "PROV1" 1)]
    (util/concept-with-conflicting-native-id-assertions
     "tool"
     :tool-name
     concept
     "tl-native-different")))
