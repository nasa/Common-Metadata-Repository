(ns cmr.metadata-db.int-test.provider-crud-test
  "Contains integration tests for deleting providers. Tests delete with various configurations including
  checking for proper error handling."
  (:require [clojure.test :refer :all]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.common.util :as u]
            [cmr.metadata-db.services.messages :as messages]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (util/reset-database-fixture))

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-provider-test
  (testing "successful saves"
    (u/are2
      [provider-id short-name cmr-only small]
      (let [{:keys [status]} (util/save-provider {:provider-id provider-id
                                                  :short-name short-name
                                                  :cmr-only cmr-only
                                                  :small small})]
        (and (= status 201)
             (util/verify-provider-was-saved provider-id
                                             short-name
                                             (if cmr-only true false)
                                             (if small true false))))
      "cmr-only false small false" "PROV1" "S1" false false
      "cmr-only true small false" "PROV2" "S2" true false
      "cmr-only false small true" "PROV3" "S3" false true
      "cmr-only true small true" "PROV4" "S4" true true
      "cmr-only and small default to false" "PROV5" "S5" nil nil))
  (testing "save provider twice"
    (let [{:keys [status errors]} (util/save-provider {:provider-id "PROV1"
                                                       :short-name "S1"
                                                       :cmr-only false
                                                       :small false})]
      (is (= [409 ["Provider with provider id [PROV1] already exists."]]
             [status errors]))))
  (testing "save provider with a conflict on short name"
    (let [{:keys [status errors]} (util/save-provider {:provider-id "PROV6"
                                                       :short-name "S1"
                                                       :cmr-only false
                                                       :small false})]
      (is (= [409 ["Provider with short name [S1] already exists. Its provider id is [PROV1]."]]
             [status errors]))))
  (testing "save reserved provder is not allowed"
    (let [{:keys [status errors]} (util/save-provider {:provider-id "SMALL_PROV"
                                                       :short-name "S5"
                                                       :cmr-only false
                                                       :small false})]
      (is (= [400 ["Provider Id [SMALL_PROV] is reserved"]]
             [status errors])))))

(deftest update-provider-test
  (testing "basic update"
    (util/save-provider {:provider-id "PROV1"
                         :short-name "S1"
                         :cmr-only false
                         :small false})
    (is (util/verify-provider-was-saved "PROV1" "S1" false false))
    (util/update-provider {:provider-id "PROV1"
                           :short-name "S1"
                           :cmr-only true
                           :small false})
    (is (util/verify-provider-was-saved "PROV1" "S1" true false)))
  (testing "cannot modify small field of a provider"
    (let [{:keys [status errors]} (util/update-provider {:provider-id "PROV1"
                                                         :short-name "S1"
                                                         :cmr-only true
                                                         :small true})]
      (is (= [400 ["Provider [PROV1] small field cannot be modified."]]
             [status errors]))))
  (testing "modify short name of a provider without conflict is OK"
    (util/update-provider {:provider-id "PROV1"
                           :short-name "S5"
                           :cmr-only true
                           :small false})
    (is (util/verify-provider-was-saved "PROV1" "S5" true false)))
  (testing "modify short name of a provider with conflict is not OK"
    (util/save-provider {:provider-id "PROV6"
                         :short-name "S6"
                         :cmr-only false
                         :small false})
    (let [{:keys [status errors]} (util/update-provider {:provider-id "PROV1"
                                                         :short-name "S6"
                                                         :cmr-only true
                                                         :small false})]
      (is (= [409 ["Provider with short name [S6] already exists. Its provider id is [PROV6]."]]
             [status errors]))))
  (testing "update nonexistant provider"
    (is (= 404 (:status (util/update-provider {:provider-id "PROV2"
                                               :short-name "S2"
                                               :cmr-only true
                                               :small false})))))
  (testing "update reserved provder is not allowed"
    (let [{:keys [status errors]} (util/update-provider {:provider-id "SMALL_PROV"
                                                         :short-name "S5"
                                                         :cmr-only false
                                                         :small false})]
      (is (= [400 ["Provider Id [SMALL_PROV] is reserved"]]
             [status errors]))))
  (testing "bad parameters"
    (is (= 400 (:status (util/update-provider {:provider-id nil
                                               :short-name "S1"
                                               :cmr-only true
                                               :small false}))))
    (is (= 400 (:status (util/update-provider {:provider-id "PROV1"
                                               :short-name "S1"
                                               :cmr-only nil
                                               :small false}))))))

(deftest get-providers-test
  (util/save-provider {:provider-id "PROV1"
                       :short-name "S1"
                       :cmr-only false
                       :small false})
  (util/save-provider {:provider-id "PROV2"
                       :short-name "S2"
                       :cmr-only true
                       :small true})
  (let [{:keys [status providers]} (util/get-providers)]
    (is (= status 200))
    (is (= [{:provider-id "PROV1" :short-name "S1" :cmr-only false :small false}
            {:provider-id "PROV2" :short-name "S2" :cmr-only true :small true}]
           (sort-by :provider-id providers)))))

(deftest delete-provider-test
  (testing "delete provider"
    (util/save-provider {:provider-id "PROV1"
                         :short-name "S1"
                         :cmr-only false
                         :small false})
    (util/save-provider {:provider-id "PROV2"
                         :short-name "S2"
                         :cmr-only false
                         :small true})
    (util/delete-provider "PROV1")
    (let [{:keys [status providers]} (util/get-providers)]
      (is (= status 200))
      (is (= [{:provider-id "PROV2" :short-name "S2" :cmr-only false :small true}] providers))))
  (testing "delete SMALL_PROV provider"
    (let [{:keys [status errors]} (util/delete-provider "SMALL_PROV")]
      (is (= [400 ["Provider [SMALL_PROV] is a reserved provider of CMR and cannot be deleted."]]
             [status errors]))))
  (testing "delete non-existent provider"
    (let [{:keys [status errors]} (util/delete-provider "NOT_PROV")]
      (is (= [404 ["Provider with provider-id [NOT_PROV] does not exist."]]
             [status errors])))))
