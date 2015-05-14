(ns cmr.system-int-test.ingest.provider-cmr-only-client-id-test
  "CMR provider CMR-ONLY flag and client id integration tests"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.index-util :as index]
            [ring.util.io :as io]
            [clj-http.client :as client]
            [clojure.string :as string]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [clj-time.core :as t]
            [cmr.common.mime-types :as mt]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]))


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest validate-collection-cmr-only-client-id-test
  (testing "validation on CMR-ONLY provider cannot be submitted by client Echo"
    (are [client-id expected-status expected-errors]
         (let [concept (dc/collection-concept {})
               {:keys [status errors]} (ingest/validate-concept concept {:client-id client-id})]
           (= [expected-status expected-errors] [status errors]))

         "Echo" 400 ["Provider [PROV1] is CMR-ONLY which requires the request to not have client id of [Echo], but was."]
         "any" 200 nil))
  (testing "validation on non CMR-ONLY provider must be submitted by client Echo"
    (ingest/update-ingest-provider "PROV1" false)
    (ingest/clear-caches)
    (are [client-id expected-status expected-errors]
         (let [concept (dc/collection-concept {})
               {:keys [status errors]} (ingest/validate-concept concept {:client-id client-id})]
           (= [expected-status expected-errors] [status errors]))

         "bad" 400 ["Provider [PROV1] is not CMR-ONLY which requires the request to have client id of [Echo], but was [bad]."]
         "Echo" 200 nil)))

(deftest ingest-collection-cmr-only-client-id-test
  (testing "ingest on CMR-ONLY provider cannot be submitted by client Echo"
    (are [client-id expected-status expected-errors]
         (let [concept (dc/collection-concept {})
               {:keys [status errors]} (ingest/ingest-concept concept {:client-id client-id})]
           (= [expected-status expected-errors] [status errors]))
         "Echo" 400 ["Provider [PROV1] is CMR-ONLY which requires the request to not have client id of [Echo], but was."]
         "any" 200 nil))
  (testing "ingest on non CMR-ONLY provider must be submitted by client Echo"
    (ingest/update-ingest-provider "PROV1" false)
    (ingest/clear-caches)
    (are [client-id expected-status expected-errors]
         (let [concept (dc/collection-concept {})
               {:keys [status errors]} (ingest/ingest-concept concept {:client-id client-id})]
           (= [expected-status expected-errors] [status errors]))

         "bad" 400 ["Provider [PROV1] is not CMR-ONLY which requires the request to have client id of [Echo], but was [bad]."]
         "Echo" 200 nil)))


(deftest delete-collection-cmr-only-client-id-test
  (testing "delete on CMR-ONLY provider cannot be submitted by client Echo"
    (are [client-id expected-status expected-errors]
         (let [coll1 (d/ingest "PROV1" (dc/collection))
               {:keys [status errors]} (ingest/delete-concept (d/item->concept coll1) {:client-id client-id})]
           (= [expected-status expected-errors] [status errors]))

         "Echo" 400 ["Provider [PROV1] is CMR-ONLY which requires the request to not have client id of [Echo], but was."]
         "any" 200 nil))
  (testing "delete on non CMR-ONLY provider must be submitted by client Echo"
    (let [coll1 (d/ingest "PROV1" (dc/collection))]
      (ingest/update-ingest-provider "PROV1" false)
      (ingest/clear-caches)
      (are [client-id expected-status expected-errors]
           (let [{:keys [status errors]} (ingest/delete-concept (d/item->concept coll1) {:client-id client-id})]
             (= [expected-status expected-errors] [status errors]))

           "bad" 400 ["Provider [PROV1] is not CMR-ONLY which requires the request to have client id of [Echo], but was [bad]."]
           "Echo" 200 nil))))

(deftest validate-granule-cmr-only-client-id-test
  (testing "validation on CMR-ONLY provider cannot be submitted by client Echo"
    (let [collection (d/ingest "PROV1" (dc/collection {}))
          granule (d/item->concept (dg/granule collection))]
      (are [client-id expected-status expected-errors]
           (let [{:keys [status errors]} (ingest/validate-concept granule {:client-id client-id})]
             (= [expected-status expected-errors] [status errors]))

           "Echo" 400 ["Provider [PROV1] is CMR-ONLY which requires the request to not have client id of [Echo], but was."]
           "any" 200 nil)))
  (testing "validation on non CMR-ONLY provider must be submitted by client Echo"
    (let [collection (d/ingest "PROV1" (dc/collection {}))]
      (ingest/update-ingest-provider "PROV1" false)
      (ingest/clear-caches)
      (are [client-id expected-status expected-errors]
           (let [granule (d/item->concept (dg/granule collection))
                 {:keys [status errors]} (ingest/validate-concept granule {:client-id client-id})]
             (= [expected-status expected-errors] [status errors]))

           "bad" 400 ["Provider [PROV1] is not CMR-ONLY which requires the request to have client id of [Echo], but was [bad]."]
           "Echo" 200 nil))))

(deftest ingest-granule-cmr-only-client-id-test
  (testing "ingest on CMR-ONLY provider cannot be submitted by client Echo"
    (let [collection (d/ingest "PROV1" (dc/collection {}))
          granule (d/item->concept (dg/granule collection))]
      (are [client-id expected-status expected-errors]
           (let [{:keys [status errors]} (ingest/ingest-concept granule {:client-id client-id})]
             (= [expected-status expected-errors] [status errors]))
           "Echo" 400 ["Provider [PROV1] is CMR-ONLY which requires the request to not have client id of [Echo], but was."]
           "any" 200 nil)))
  (testing "ingest on non CMR-ONLY provider must be submitted by client Echo"
    (let [collection (d/ingest "PROV1" (dc/collection {}))]
      (ingest/update-ingest-provider "PROV1" false)
      (ingest/clear-caches)
      (are [client-id expected-status expected-errors]
           (let [granule (d/item->concept (dg/granule collection))
                 {:keys [status errors]} (ingest/ingest-concept granule {:client-id client-id})]
             (= [expected-status expected-errors] [status errors]))

           "bad" 400 ["Provider [PROV1] is not CMR-ONLY which requires the request to have client id of [Echo], but was [bad]."]
           "Echo" 200 nil))))


(deftest delete-granule-cmr-only-client-id-test
  (testing "delete on CMR-ONLY provider cannot be submitted by client Echo"
    (let [collection (d/ingest "PROV1" (dc/collection {}))
          granule (d/item->concept (dg/granule collection))]
      (ingest/ingest-concept granule)
      (are [client-id expected-status expected-errors]
           (let [{:keys [status errors]} (ingest/delete-concept granule {:client-id client-id})]
             (= [expected-status expected-errors] [status errors]))

           "Echo" 400 ["Provider [PROV1] is CMR-ONLY which requires the request to not have client id of [Echo], but was."]
           "any" 200 nil)))
  (testing "delete on non CMR-ONLY provider must be submitted by client Echo"
    (let [collection (d/ingest "PROV1" (dc/collection {}))
          granule (d/item->concept (dg/granule collection))]
      (ingest/ingest-concept granule)
      (ingest/update-ingest-provider "PROV1" false)
      (ingest/clear-caches)
      (are [client-id expected-status expected-errors]
           (let [{:keys [status errors]} (ingest/delete-concept granule {:client-id client-id})]
             (= [expected-status expected-errors] [status errors]))

           "bad" 400 ["Provider [PROV1] is not CMR-ONLY which requires the request to have client id of [Echo], but was [bad]."]
           "Echo" 200 nil))))

