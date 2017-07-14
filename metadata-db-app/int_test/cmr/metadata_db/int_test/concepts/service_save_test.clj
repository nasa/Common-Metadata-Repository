(ns cmr.metadata-db.int-test.concepts.service-save-test
  "Contains integration tests for saving services. Tests saves with various configurations including
  checking for proper error handling."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer (are3)]
   [cmr.metadata-db.int-test.concepts.concept-save-spec :as c-spec]
   [cmr.metadata-db.int-test.utility :as util]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}))

(defmethod c-spec/gen-concept :service
  [_ _ uniq-num attributes]
  (util/service-concept uniq-num attributes))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; CMR-4335 - services
;; This seems to fail because we keep creating new revisions of the same concept over and over.
; (deftest save-service-test
;   (c-spec/general-save-concept-test :service ["CMR"]))

(deftest save-service-specific-test
  (testing "saving new services"
    (are3 [service exp-status exp-errors]
          (let [{:keys [status errors]} (util/save-concept service)]
            (is (= exp-status status))
            (is (= (set exp-errors) (set errors))))

          "failure when using non system-level provider"
          (assoc (util/service-concept 2) :provider-id "REG_PROV")
          422
          ["Service could not be associated with provider [REG_PROV]. Services are system level entities."])))
