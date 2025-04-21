(ns cmr.system-int-test.misc.kms-fetcher-test
  (:require
   [clojure.test :refer :all]
   [cmr.common.cache :as cache]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.common-app.services.kms-lookup :as kl]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.kms :as trans-kms]))

(deftest validate-getting-kms-keywords-test
  (let [sys (transmit-config/system-with-connections
             {:caches {kf/kms-cache-key (kf/create-kms-cache)
                       kl/kms-short-name-cache-key (kl/create-kms-short-name-cache)
                       kl/kms-umm-c-cache-key (kl/create-kms-umm-c-cache)
                       kl/kms-location-cache-key (kl/create-kms-location-cache)
                       kl/kms-measurement-cache-key (kl/create-kms-measurement-cache)}}
             [:kms])
        context {:system sys}
        kms-cache (cache/context->cache context kf/kms-cache-key)
        _  (#'kf/refresh-kms-cache context)
        kms-map (cache/get-value kms-cache kf/kms-cache-key)]

    (testing "Testing that KMS keywords such as projects exist after normal loading."
      (is (some? (:projects kms-map))))

    (testing "Test KMS keywords API returning nil for testing purposes."
      (let [context (assoc context :testing-for-nil-keyword-scheme-value true)]
        (is (nil? (trans-kms/get-keywords-for-keyword-scheme context :projects)))))

    ;; Test to make sure that if KMS keywords API returns nil for a keyword after
    ;; parsing, (KMS API is down) that previous cache value is used, so that we don't
    ;; wipe out the KMS keyword values in the cache.
    (comment
      ;; We are no longer interested in this use case. KMS being down is now an external problem to
      ;; CMR as the update script runs outside of CMR. CMR should assume it always has a cache.
      (testing "Makes sure that KMS keywords are not wiped out."
        (let [context (assoc context :testing-for-nil-keyword-scheme-value true)]
          (#'kf/refresh-kms-cache context)
          (is (some? (:projects (cache/get-value kms-cache kf/kms-cache-key)))))))))
