(ns cmr.metadata-db.int-test.concepts.variable-association-save-test
  "Contains integration tests for saving variable associations. Tests saves with various
   configurations including checking for proper error handling."
  (:require
   [clojure.test :refer :all]
   [cmr.metadata-db.int-test.concepts.concept-save-spec :as c-spec]
   [cmr.metadata-db.int-test.utility :as util]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}))

(defmethod c-spec/gen-concept :variable-association
  [_ _ uniq-num attributes]
  (let [concept-attributes (or (:concept-attributes attributes) {})
        concept (util/create-and-save-collection "REG_PROV" uniq-num 1 concept-attributes)
        variable-attributes (or (:variable-attributes attributes) {})
        variable (util/create-and-save-variable "REG_PROV" uniq-num 1 variable-attributes)
        attributes (dissoc attributes :concept-attributes :variable-attributes)]
    (util/variable-association-concept concept variable uniq-num attributes)))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-variable-association-test
  (c-spec/general-save-concept-test :variable-association ["CMR"]))

(deftest save-variable-association-failure-test
  (testing "saving new variable associations on non system-level provider"
    (let [coll (util/create-and-save-collection "REG_PROV" 1)
          variable (util/create-and-save-variable "REG_PROV" 1)
          variable-association (-> (util/variable-association-concept coll variable 2)
                                   (assoc :provider-id "REG_PROV"))
          {:keys [status errors]} (util/save-concept variable-association)]

      (is (= 422 status))
      (is (= [(str "Variable association could not be associated with provider [REG_PROV]. "
                   "Variable associations are system level entities.")]
             errors)))))
