(ns cmr.system-int-test.misc.kms-fetcher-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [cmr.common.cache :as cache]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.bootstrap.system :as bootstrap-system]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.kms :as trans-kms]))

(deftest validate-getting-kms-keywords-test
  ;; TODO I can pull off application caches or this. This seemed better
  (let [sys (transmit-config/system-with-connections
             {:caches (:caches (bootstrap-system/create-system))}
             [:kms])
        context {:system sys}
        kms-cache (cache/context->cache context kf/kms-cache-key)
        _  (#'kf/refresh-kms-cache context)
        kms-map (cache/get-value kms-cache kf/kms-cache-key)]

    (testing "Testing that KMS keywords such as projects exist after normal loading."
      (is (some? (:projects kms-map))))

    (testing "Test KMS keywords API returning nil for testing purposes."
      (let [context (assoc context :testing-for-nil-keyword-scheme-value true)]
        (is (nil? (trans-kms/get-keywords-for-keyword-scheme context :projects)))))))
