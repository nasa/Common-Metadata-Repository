(ns cmr.metadata-db.int-test.concepts.variable-association-save-test
  "Contains integration tests for saving variable associations. Tests saves with various
   configurations including checking for proper error handling."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer (are2)]
   [cmr.metadata-db.int-test.concepts.concept-save-spec :as c-spec]
   [cmr.metadata-db.int-test.utility :as util]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}))

(defmethod c-spec/gen-concept :variable-association
  [_ _ uniq-num attributes]
  (let [concept-attributes (or (:concept-attributes attributes) {})
        concept (util/create-and-save-collection "REG_PROV" uniq-num 1 concept-attributes)
        variable-attributes (or (:variable-attributes attributes) {})
        variable (util/create-and-save-variable uniq-num 1 variable-attributes)
        attributes (dissoc attributes :concept-attributes :variable-attributes)]
    (util/variable-association-concept concept variable uniq-num attributes)))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-variable-association-test
  (c-spec/general-save-concept-test :variable-association ["CMR"]))

(deftest save-variable-association-specific-test
  (testing "saving new variable associations"
    (are2 [variable-association exp-status exp-errors]
          (let [variable-collection (util/create-and-save-collection "REG_PROV" 1)
                variable (util/create-and-save-variable 1)
                {:keys [status errors]} (util/save-concept variable-association)]

            (is (= exp-status status))
            (is (= (set exp-errors) (set errors))))

          "failure when using non system-level provider"
          (assoc (util/variable-association-concept variable-collection variable 2) :provider-id "REG_PROV")
          422
          [(str "Variable association could not be associated with provider [REG_PROV]. "
                "Variable associations are system level entities.")])))
