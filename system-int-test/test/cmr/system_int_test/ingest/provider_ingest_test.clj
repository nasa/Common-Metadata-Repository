(ns cmr.system-int-test.ingest.provider-ingest-test
  "CMR provider ingest integration test"
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cmr.common.util :as u]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.system :as s]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest provider-ingest-test
  (testing "create provider and get providers through ingest app"
    (are [provider-id cmr-only small]
         (let [{:keys [status]} (ingest/create-ingest-provider {:provider-id provider-id
                                                                :cmr-only cmr-only
                                                                :small small})]
           (and (= 201 status))
           (= (ingest/get-providers) (ingest/get-ingest-providers)))
         "PROV3" false false
         "PROV4" true false
         "PROV5" false true
         "PROV6" true true))
  (testing "create provider invalid value"
    (u/are2
      [provider error]
      (let [response (ingest/create-ingest-provider provider)
            {:keys [status errors]} (ingest/parse-ingest-response response {:accept-format :json})]
        (= [400 [error]] [status errors]))

      "without cmr-only"
      {:provider-id "PROV8" :small false}
      "Cmr Only is required."

      "cmr-only invalid value"
      {:provider-id "PROV8" :cmr-only "" :small false}
      "Cmr Only must be either true or false but was [\"\"]"

      "without small"
      {:provider-id "PROV8" :cmr-only false}
      "Small is required."

      "small invalid value"
      {:provider-id "PROV8" :cmr-only false :small ""}
      "Small must be either true or false but was [\"\"]")))

(deftest update-provider-test
  (testing "creating a provider and changing attributes"
    (ingest/create-ingest-provider {:provider-id "PROV3"
                                    :cmr-only false
                                    :small false})
    (ingest/create-ingest-provider {:provider-id "PROV4"
                                    :cmr-only true
                                    :small true})
    (ingest/update-ingest-provider "PROV4" false true)
    (is (= #{{:provider-id "PROV4" :cmr-only false :small true}
             {:provider-id "PROV3" :cmr-only false :small false}
             {:provider-id "PROV2" :cmr-only true :small false}
             {:provider-id "PROV1" :cmr-only true :small false}}
           ;; filter out the SMALL_PROV which always exists in metadata db real database
           (set (filter #(not= "SMALL_PROV" (:provider-id %)) (ingest/get-ingest-providers))))))
  (testing "updating a non-existent provider fails"
    (is (= 404 (:status (ingest/update-ingest-provider "PROV5" true false)))))
  (testing "update provider with a different small value is invalid"
    (ingest/create-ingest-provider {:provider-id "PROV5"
                                    :cmr-only true
                                    :small true})
    (let [response (ingest/update-ingest-provider "PROV5" true false)
          {:keys [status errors]} (ingest/parse-ingest-response response {:accept-format :json})]
      (is (= [400 ["Provider [PROV5] small field cannot be modified."]]
             [status errors])))))

(deftest update-provider-without-permission-test
  (let [response (client/put (url/ingest-provider-url "PROV1")
                             {:throw-exceptions false
                              :connection-manager (s/conn-mgr)
                              :query-params {:token "dummy-token"}})]
    (is (= 401 (:status response)))))

(deftest delete-provider-test
  (let [coll1 (d/ingest "PROV1" (dc/collection))
        gran1 (d/ingest "PROV1" (dg/granule coll1))
        gran2 (d/ingest "PROV1" (dg/granule coll1))
        coll2 (d/ingest "PROV1" (dc/collection))
        gran3 (d/ingest "PROV1" (dg/granule coll2))
        coll3 (d/ingest "PROV2" (dc/collection))
        gran4 (d/ingest "PROV2" (dg/granule coll3))]
    (index/wait-until-indexed)

    (is (= 2 (count (:refs (search/find-refs :collection {:provider-id "PROV1"})))))
    (is (= 3 (count (:refs (search/find-refs :granule {:provider-id "PROV1"})))))

    ;; delete provider PROV1
    (is (= 200 (ingest/delete-ingest-provider "PROV1")))
    (index/wait-until-indexed)

    ;; PROV1 concepts are not in metadata-db
    (are [concept]
         (not (ingest/concept-exists-in-mdb? (:concept-id concept) (:revision-id concept)))
         coll1
         coll2
         gran1
         gran2
         gran3)

    ;; PROV2 concepts are in metadata-db
    (are [concept]
         (ingest/concept-exists-in-mdb? (:concept-id concept) (:revision-id concept))
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

(deftest delete-non-existent-provider-test
  (let [[status errors] (ingest/delete-ingest-provider "NON_EXIST")]
    (is (= [404 ["Provider with provider-id [NON_EXIST] does not exist."]]
           [status errors]))))

(deftest delete-small-provider-test
  (testing "delete SMALL_PROV is not allowed"
    (let [[status errors] (ingest/delete-ingest-provider "SMALL_PROV")]
      (is (= [400 ["Provider [SMALL_PROV] is a reserved provider of CMR and cannot be deleted."]]
             [status errors])))))

(deftest delete-provider-without-permission-test
  (let [response (client/delete (url/ingest-provider-url "PROV1")
                                {:throw-exceptions false
                                 :connection-manager (s/conn-mgr)
                                 :query-params {:token "dummy-token"}})]
    (is (= 401 (:status response)))))
