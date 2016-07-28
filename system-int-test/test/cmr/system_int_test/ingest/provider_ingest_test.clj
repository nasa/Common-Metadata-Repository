(ns cmr.system-int-test.ingest.provider-ingest-test
  "CMR provider ingest integration test"
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cmr.common.util :as u]
            [cmr.common.mime-types :as mt]
            [cmr.access-control.test.util :as access-control]
            [cmr.system-int-test.utils.metadata-db-util :as mdb]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.system :as s]
            [cmr.transmit.config :as transmit-config]
            [cmr.mock-echo.client.echo-util :as e]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest provider-ingest-test
  (testing "create provider and get providers through ingest app"
    (are [provider-id short-name cmr-only small]
         (let [{:keys [status]} (ingest/create-ingest-provider {:provider-id provider-id
                                                                :short-name short-name
                                                                :cmr-only cmr-only
                                                                :small small})]
           (and (= 201 status))
           (= (ingest/get-providers) (ingest/get-ingest-providers)))
         "PROV3" "S3" false false
         "PROV4" "S4" true false
         "PROV5" "S5" false true
         "PROV6" "S6" true true
         "PROV7" "S7" nil nil
         "PROV8" nil nil nil))
  (testing "create provider invalid value"
    (u/are2
      [provider error]
      (let [response (ingest/create-ingest-provider provider)
            {:keys [status errors]} (ingest/parse-ingest-response response {:accept-format :json})]
        (= [400 [error]] [status errors]))

      "cmr-only invalid value"
      {:provider-id "PROV9" :short-name "S8" :cmr-only "" :small false}
      "Cmr Only must be either true or false but was [\"\"]"

      "small invalid value"
      {:provider-id "PROV9" :short-name "S8" :cmr-only false :small ""}
      "Small must be either true or false but was [\"\"]")

    (testing "not json"
      (let [response (client/post (url/ingest-create-provider-url)
                                  {:body "<somexml/>"
                                   :content-type :xml
                                   :throw-exceptions false
                                   :connection-manager (s/conn-mgr)
                                   :headers {transmit-config/token-header (transmit-config/echo-system-token)}})
            {:keys [status body]} response]
        (is (= 415 status))
        (is (re-find #"Creating or updating a provider requires a JSON content type" body))))))

(deftest update-provider-test
  (testing "creating a provider and changing attributes"
    (ingest/create-ingest-provider {:provider-id "PROV3"
                                    :short-name "S3"
                                    :cmr-only false
                                    :small false})
    (ingest/create-ingest-provider {:provider-id "PROV4"
                                    :short-name "S4"
                                    :cmr-only true
                                    :small true})
    (ingest/update-ingest-provider {:provider-id "PROV4"
                                    :short-name "S4"
                                    :cmr-only false
                                    :small true})
    (is (= #{{:provider-id "PROV4" :short-name "S4" :cmr-only false :small true}
             {:provider-id "PROV3" :short-name "S3" :cmr-only false :small false}
             {:provider-id "PROV2" :short-name "PROV2" :cmr-only true :small false}
             {:provider-id "PROV1" :short-name "PROV1":cmr-only true :small false}}
           (set (ingest/get-ingest-providers)))))
  (testing "updating a non-existent provider fails"
    (is (= 404 (:status (ingest/update-ingest-provider {:provider-id "PROV5"
                                                        :short-name "S5"
                                                        :cmr-only true
                                                        :small false})))))
  (testing "update provider with a different small value is invalid"
    (ingest/create-ingest-provider {:provider-id "PROV5"
                                    :short-name "S5"
                                    :cmr-only true
                                    :small true})
    (let [response (ingest/update-ingest-provider {:provider-id "PROV5"
                                                   :short-name "S5"
                                                   :cmr-only true
                                                   :small false})
          {:keys [status errors]} (ingest/parse-ingest-response response {:accept-format :json})]
      (is (= [400 ["Provider [PROV5] small field cannot be modified."]]
             [status errors]))))
  (testing "update provider without permission"
    (let [response (client/put (url/ingest-provider-url "PROV1")
                               {:throw-exceptions false
                                :connection-manager (s/conn-mgr)
                                :query-params {:token "dummy-token"}})]
      (is (= 401 (:status response))))))


(deftest delete-provider-test
  (testing "delete provider"
    (let [coll1 (d/ingest "PROV1" (dc/collection))
          gran1 (d/ingest "PROV1" (dg/granule coll1))
          gran2 (d/ingest "PROV1" (dg/granule coll1))
          coll2 (d/ingest "PROV1" (dc/collection))
          gran3 (d/ingest "PROV1" (dg/granule coll2))
          coll3 (d/ingest "PROV2" (dc/collection))
          gran4 (d/ingest "PROV2" (dg/granule coll3))
          ;; create an access group to test cascading deletes
          access-group (u/map-keys->kebab-case
                        (access-control/create-group
                         (transmit-config/echo-system-token)
                         {:name "Administrators"
                          :description "A Group"
                          :provider_id "PROV1"}))
          acl1 (u/map-keys->kebab-case
                 (access-control/create-acl (transmit-config/echo-system-token)
                                            {:group_permissions [{:permissions [:read :order]
                                                                  :user_type "guest"}]
                                             :catalog_item_identity {:name "PROV1 read, order"
                                                                     :collection_applicable true
                                                                     :provider_id "PROV1"}}))
          acl2 (u/map-keys->kebab-case
                 (access-control/create-acl (transmit-config/echo-system-token)
                                           {:group_permissions [{:permissions [:read :order]
                                                                 :user_type "guest"}]
                                            :provider_identity {:provider_id "PROV1"
                                                                :target "INGEST_MANAGEMENT_ACL"}}))]
      (index/wait-until-indexed)

      (is (= 2 (count (:refs (search/find-refs :collection {:provider-id "PROV1"})))))
      (is (= 3 (count (:refs (search/find-refs :granule {:provider-id "PROV1"})))))

      ;; ensure PROV1 group is indexed
      (is (= [(:concept-id access-group)]
             (map :concept_id
                  (:items
                    (access-control/search-for-groups (transmit-config/echo-system-token)
                                                      {:provider "PROV1"})))))

      ;; PROV1 ACLs are indexed
      (is (= [(:concept-id acl1) (:concept-id acl2)]
             (map :concept_id
                  (:items
                    (access-control/search-for-acls (transmit-config/echo-system-token)
                                                    {:provider "PROV1"})))))

      ;; delete provider PROV1
      (let [{:keys [status content-length]} (ingest/delete-ingest-provider "PROV1")]
        (is (= 204 status))
        (is (nil? content-length)))
      (index/wait-until-indexed)

      ;; PROV1 concepts are not in metadata-db
      (are [concept]
        (not (mdb/concept-exists-in-mdb? (:concept-id concept) (:revision-id concept)))
        coll1
        coll2
        gran1
        gran2
        gran3
        access-group
        acl1
        acl2)

      ;; PROV1 access group is unindexed
      (is (= 0 (:hits
                (access-control/search-for-groups (transmit-config/echo-system-token)
                                                  {:provider "PROV1"}))))

      ;; PROV1 ACLs are no longer indexed
      (is (= 0 (:hits
                 (access-control/search-for-acls (transmit-config/echo-system-token)
                                                 {:provider "PROV1"}))))

      ;; PROV2 concepts are in metadata-db
      (are [concept]
        (mdb/concept-exists-in-mdb? (:concept-id concept) (:revision-id concept))
        coll3
        gran4)

      ;; search on PROV1 finds nothing
      (is (d/refs-match?
           []
           (search/find-refs :collection {:provider-id "PROV1"})))
      (is (d/refs-match?
           []
           (search/find-refs :granule {:provider-id "PROV1"})))

      ;; search on PROV2 finds the concepts
      (is (d/refs-match?
           [coll3]
           (search/find-refs :collection {:provider-id "PROV2"})))
      (is (d/refs-match?
           [gran4]
           (search/find-refs :granule {:provider-id "PROV2"})))))

  (testing "delete non-existent provider"
    (let [{:keys [status errors content-type]} (ingest/delete-ingest-provider "NON_EXIST")]
      (is (= (mt/with-utf-8 mt/json) content-type))
      (is (= [404 ["Provider with provider-id [NON_EXIST] does not exist."]]
             [status errors]))))

  (testing "delete SMALL_PROV provider"
    (let [{:keys [status errors content-type]} (ingest/delete-ingest-provider "SMALL_PROV")]
      (is (= (mt/with-utf-8 mt/json) content-type))
      (is (= [400 ["Provider [SMALL_PROV] is a reserved provider of CMR and cannot be deleted."]]
             [status errors]))))

  (testing "delete provider without permission"
    (let [response (client/delete (url/ingest-provider-url "PROV1")
                                  {:throw-exceptions false
                                   :connection-manager (s/conn-mgr)
                                   :query-params {:token "dummy-token"}})]
      (is (= 401 (:status response))))))

(deftest provider-delete-cascades-to-concepts-test
  (doseq [provider [{:provider-id "SMALL" :short-name "SP" :cmr-only true :small true}
                    {:provider-id "NOTSMALL" :short-name "NSP"}]]
    (ingest/create-ingest-provider provider)
    (let [access-group (u/map-keys->kebab-case
                        (access-control/create-group
                         (transmit-config/echo-system-token)
                         {:name "Administrators"
                          :description "A Group"
                          :provider_id (:provider-id provider)}))]
      (is (mdb/concept-exists-in-mdb? (:concept-id access-group) (:revision-id access-group)))
      (ingest/delete-ingest-provider (:provider-id provider))
      (is (not (mdb/concept-exists-in-mdb? (:concept-id access-group) (:revision-id access-group))))
      ;; re-create the provider to ensure nothing has stuck around in the DB
      (ingest/create-ingest-provider provider)
      (is (not (mdb/concept-exists-in-mdb? (:concept-id access-group) (:revision-id access-group)))))))
