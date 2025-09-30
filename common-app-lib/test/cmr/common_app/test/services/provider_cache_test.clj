(ns cmr.common-app.test.services.provider-cache-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cmr.common-app.services.provider-cache :as provider-cache]
   [cmr.common.hash-cache :as hash-cache]
   [cmr.common.time-keeper :as tk]
   [cmr.redis-utils.test.test-util :as test-util]))

(use-fixtures :each tk/freeze-resume-time-fixture)
(use-fixtures :each test-util/embedded-redis-server-fixture)

(def providers
  '({:provider-id "PROV1" :short-name "PROV1" :cmr-only false :small false :consortiums "EOSDIS GEOSS"}
    {:provider-id "PROV2" :short-name "PROV2" :cmr-only false :small false :consortiums "EOSDIS GEOSS"}))

(def providers-cache-map
  {"PROV1" {:provider-id "PROV1"
            :short-name "PROV1"
            :cmr-only false
            :small false
            :consortiums "EOSDIS GEOSS"}
   "PROV2" {:provider-id "PROV2"
            :short-name "PROV2"
            :cmr-only false
            :small false
            :consortiums "EOSDIS GEOSS"}})

(deftest validate-providers-exist-test
  (let [cache-key provider-cache/cache-key
        test-context {:system {:caches {cache-key (provider-cache/create-cache)}}}
        cache (get-in test-context [:system :caches cache-key])]
    (hash-cache/reset cache cache-key)
    (testing "Cache is empty"
      (is (nil? (hash-cache/get-map cache cache-key))))
    (testing "Refreshing cache"
      (is (= '(1 1) (provider-cache/refresh-provider-cache test-context providers))))
    (testing "Cache has values"
      (is (= providers-cache-map (hash-cache/get-map cache cache-key))))
    (testing "Validate that a provider exists."
      (is (= `("PROV1") (provider-cache/validate-providers-exist test-context '("PROV1")))))
    (testing "Validate that a provider does not exist."
      (is (thrown? clojure.lang.ExceptionInfo (provider-cache/validate-providers-exist test-context '("PROV3")))))))

(deftest get-provider-test
  (let [cache-key provider-cache/cache-key
        test-context {:system {:caches {cache-key (provider-cache/create-cache)}}}
        cache (get-in test-context [:system :caches cache-key])]
    (hash-cache/reset cache cache-key)
    (provider-cache/refresh-provider-cache test-context providers)
    (testing "Get an existing provider"
      (is (= {:provider-id "PROV1"
              :short-name "PROV1"
              :cmr-only false
              :small false
              :consortiums "EOSDIS GEOSS"}
             (provider-cache/get-provider test-context "PROV1"))))
    (testing "Get another existing provider"
      (is (= {:provider-id "PROV2"
              :short-name "PROV2"
              :cmr-only false
              :small false
              :consortiums "EOSDIS GEOSS"}
             (provider-cache/get-provider test-context "PROV2"))))
    (testing "Get a non-existent provider throws an exception"
      (is (thrown? clojure.lang.ExceptionInfo
                   (provider-cache/get-provider test-context "PROV3"))))))

(deftest job-config-test
  (testing "testing the provider cache refresh job"
    (is (= {:job-type cmr.common_app.services.provider_cache.RefreshProviderCacheJob
            :job-key "SomeJob"
            :daily-at-hour-and-minute [07 00]}
           (provider-cache/refresh-provider-cache-job "SomeJob")))))
