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
  [_ _ uniq-num attributes]
  (util/variable-concept uniq-num attributes))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-variable-test
  (c-spec/general-save-concept-test :variable ["CMR"]))

(deftest save-variable-specific-test
  (testing "saving new variables"
    (are3 [variable exp-status exp-errors]
          (let [{:keys [status errors]} (util/save-concept variable)]
            (is (= exp-status status))
            (is (= (set exp-errors) (set errors))))

          "failure when using non system-level provider"
          (assoc (util/variable-concept 2) :provider-id "REG_PROV")
          422
          ["Variable could not be associated with provider [REG_PROV]. Variables are system level entities."])))
