(ns cmr.metadata-db.int-test.concepts.service-save-test
  "Contains integration tests for saving services. Tests saves with various configurations including
  checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.local :as l]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.metadata-db.services.messages :as msg]
            [cmr.metadata-db.services.concept-constraints :as cc]
            [cmr.metadata-db.int-test.concepts.concept-save-spec :as c-spec]))


;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Set up REG_PROV as regular provider and SMAL_PROV1 as a small provider
(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV1" :small true}
                                                 {:provider-id "SMAL_PROV2" :small true}))

(defmethod c-spec/gen-concept :service
  [_ provider-id uniq-num attributes]
  (util/service-concept provider-id uniq-num attributes))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-collection-tests
  (c-spec/general-save-concept-test :service ["REG_PROV" "SMAL_PROV1"]))

(deftest save-service-with-same-native-id-test
  (testing "Save services with the same native-id for two small providers is OK"
    (let [serv1 (util/service-concept "SMAL_PROV1" 1 {:native-id "foo"})
          serv2 (util/service-concept "SMAL_PROV2" 2 {:native-id "foo"})]
      (c-spec/save-distinct-concepts-test serv1 serv2))))

(deftest save-test-with-missing-required-parameters
  (c-spec/save-test-with-missing-required-parameters
    :service ["REG_PROV" "SMAL_PROV1"] [:concept-type :provider-id :native-id :extra-fields]))