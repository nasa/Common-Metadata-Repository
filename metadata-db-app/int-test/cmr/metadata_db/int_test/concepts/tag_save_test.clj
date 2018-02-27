(ns cmr.metadata-db.int-test.concepts.tag-save-test
  "Contains integration tests for saving tags. Tests saves with various configurations including
  checking for proper error handling."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer (are2)]
   [cmr.metadata-db.int-test.concepts.concept-save-spec :as c-spec]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}))

(defmethod c-spec/gen-concept :tag
  [_ _ uniq-num attributes]
  (concepts/create-concept :tag "CMR" uniq-num attributes))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-tag-test
  (c-spec/general-save-concept-test :tag ["CMR"]))

(deftest save-tag-specific-test
  (testing "saving new tags"
    (are2 [tag exp-status exp-errors]
          (let [{:keys [status errors]} (util/save-concept tag)]
            (is (= exp-status status))
            (is (= (set exp-errors) (set errors))))

          "failure when using non system-level provider"
          (assoc (concepts/create-concept :tag "CMR" 2) :provider-id "REG_PROV")
          422
          ["Tag could not be associated with provider [REG_PROV]. Tags are system level entities."])))
