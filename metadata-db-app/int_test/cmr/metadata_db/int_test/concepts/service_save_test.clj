(ns cmr.metadata-db.int-test.concepts.service-save-test
  "Contains integration tests for saving services. Tests saves with various
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

(defmethod c-spec/gen-concept :service
  [_ provider-id uniq-num attributes]
  (concepts/create-concept :service provider-id uniq-num attributes))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest save-service
  (c-spec/general-save-concept-test :service ["PROV1" "PROV2"]))

(deftest save-service-with-missing-required-parameters
  (c-spec/save-test-with-missing-required-parameters
   :service ["PROV1"] [:concept-type :provider-id :native-id :extra-fields]))

(deftest save-service-created-at
  (let [concept (concepts/create-concept :service "PROV1" 2)]
    (util/concept-created-at-assertions "service" concept)))

(deftest save-service-with-conflicting-native-id
  (let [concept (concepts/create-concept :service "PROV1" 1)]
    (util/concept-with-conflicting-native-id-assertions
     "service"
     :service-name
     concept
     "svc-native-different")))
