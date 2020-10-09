(ns cmr.metadata-db.int-test.concepts.variable-association-save-test
  "Contains integration tests for saving variable associations. Tests saves with various
   configurations including checking for proper error handling."
  (:require
   [clojure.test :refer :all]
   [cmr.metadata-db.int-test.concepts.concept-save-spec :as c-spec]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-variable-association-failure-test
  (testing "saving new variable associations on non system-level provider"
    (let [coll (concepts/create-and-save-concept :collection "REG_PROV" 1)
          variable (concepts/create-and-save-concept :variable "REG_PROV" 1 1 {:coll-concept-id (:concept-id coll)})
          variable-association (-> (concepts/create-concept :variable-association coll variable 2)
                                   (assoc :provider-id "REG_PROV"))
          {:keys [status errors]} (util/save-concept variable-association)]

      (is (= 422 status))
      (is (= [(str "Variable association could not be associated with provider [REG_PROV]. "
                   "Variable associations are system level entities.")]
             errors)))))
