(ns cmr.metadata-db.int-test.concepts.acl-save-test
  "Contains integration tests for saving acls. Tests saves with various configurations including
  checking for proper error handling."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer (are2)]
   [cmr.metadata-db.int-test.concepts.concept-save-spec :as c-spec]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Set up REG_PROV as regular provider and SMAL_PROV1 as a small provider
(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV1" :small true}
                                                 {:provider-id "SMAL_PROV2" :small true}))

(defmethod c-spec/gen-concept :acl
  [_ provider-id uniq-num attributes]
  (concepts/create-concept :acl provider-id uniq-num attributes))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-acl-test
  (c-spec/general-save-concept-test :acl ["REG_PROV" "SMAL_PROV1" "CMR"]))

(deftest missing-required-parameters
  (c-spec/save-test-with-missing-required-parameters
    :acl ["REG_PROV" "SMAL_PROV1" "CMR"] [:concept-type :native-id]))

(deftest save-acl-with-same-native-id-test
  (testing "Save acls with the same native-id for two small providers is OK"
    (let [serv1 (concepts/create-and-save-concept :acl "SMAL_PROV1" 1 1 {:native-id "foo"})
          serv2 (concepts/create-and-save-concept :acl "SMAL_PROV2" 2 1 {:native-id "foo"})
          [serv1-concept-id serv2-concept-id] (map :concept-id [serv1 serv2])]
      (util/verify-concept-was-saved serv1)
      (util/verify-concept-was-saved serv2)
      (is (not= serv1-concept-id serv2-concept-id)))))
