(ns cmr.metadata-db.int-test.concepts.variable-save-test
  "Contains integration tests for saving variables. Tests saves with various configurations including
  checking for proper error handling."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer (are3)]
   [cmr.metadata-db.int-test.concepts.concept-save-spec :as c-spec]
   [cmr.metadata-db.int-test.utility :as util]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}))

(defmethod c-spec/gen-concept :variable
  [_ provider-id uniq-num attributes]
  (util/variable-concept provider-id uniq-num attributes))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-variable-test
  (c-spec/general-save-concept-test :variable ["REG_PROV"]))

(deftest save-variable-with-missing-required-parameters-test
  (c-spec/save-test-with-missing-required-parameters
    :variable ["REG_PROV"] [:concept-type :provider-id :native-id :extra-fields]))
