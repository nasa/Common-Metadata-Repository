(ns cmr.common-app.services.kms-fetcher-test
  (:require
   [clojure.test :refer :all]
   [cmr.common.cache :as cache]
   [cmr.common-app.services.kms-fetcher :as fetcher]
   [cmr.indexer.system :as idx-sys]
   [cmr.transmit.kms :as trans-kms]))

(deftest validate-getting-kms-keywords-test
  (let [context {:system (idx-sys/create-system)}
        kms-cache (cache/context->cache context fetcher/kms-cache-key)
        _  (#'fetcher/refresh-kms-cache context)
        kms-map (cache/get-value kms-cache fetcher/kms-cache-key)]
    
    (testing "Testing that KMS keywords such as projects exist after normal loading."
      (is (some? (:projects kms-map))))
    
    (testing "Test KMS keywords API returning nil for testing purposes."
      (let [context (assoc context :testing-for-nil-keyword-scheme-value true)]
        (is (nil? (trans-kms/get-keywords-for-keyword-scheme context :projects)))))
    
    ;; Test to make sure that if KMS keywords API returns nil for a keyword after 
    ;; parsing, (KMS API is down) that previous cache value is used, so that we don't
    ;; wipe out the KMS keyword values in the cache.
    (testing "Makes sure that KMS keywords are not wiped out."
      (let [context (assoc context :testing-for-nil-keyword-scheme-value true)]
        (#'fetcher/refresh-kms-cache context)
        (is (some? (:projects (cache/get-value kms-cache fetcher/kms-cache-key))))))))
