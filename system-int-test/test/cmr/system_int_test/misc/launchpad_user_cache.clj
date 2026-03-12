(ns cmr.system-int-test.misc.launchpad-user-cache
  (:require
   [clj-time.core :as t]
   [clojure.test :refer :all]
   [cmr.mock-echo.api.urs :as mock-urs]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.cache-util :as cache-util]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.url-helper :as url]
   [cmr.transmit.config :as transmit-config])
  (:import
   (org.apache.commons.codec.digest DigestUtils)))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"})
                       (dev-sys-util/freeze-resume-time-fixture)]))

(deftest launchpad-user-cache-test
  (testing "launchpad cache initial value"
    (let [launchpad-token (echo-util/login-with-launchpad-token (system/context) "user1")
          token-key (keyword (DigestUtils/sha256Hex launchpad-token))]
      (is (empty? (cache-util/list-cache-keys (url/ingest-read-caches-url) "launchpad-user" transmit-config/mock-echo-system-token)))
      (let [concept (data-umm-c/collection-concept {})
            {:keys [concept-id revision-id]} (ingest/ingest-concept concept {:token launchpad-token})]
        (index/wait-until-indexed)

        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= 1 revision-id))
        (is (some #{"launchpad-user"} (cache-util/list-caches-for-app (url/ingest-read-caches-url) transmit-config/mock-echo-system-token)))
        (is (= (:uid (cache-util/get-cache-value
                      (url/ingest-read-caches-url)
                      "launchpad-user"
                      (name token-key)
                      transmit-config/mock-echo-system-token
                      200))
               "user1"))

        (is (= mock-urs/mock-urs-token (cache-util/get-cache-value
                                        (url/ingest-read-caches-url)
                                        "urs"
                                        "cmr"
                                        transmit-config/mock-echo-system-token
                                        200)))

        (testing "Launchpad token cache value maintains expiration date"
          (dev-sys-util/advance-time! 20)
          (let [expiration-time (:expiration-time (cache-util/get-cache-value
                                                   (url/ingest-read-caches-url)
                                                   "launchpad-user"
                                                   (name token-key)
                                                   transmit-config/mock-echo-system-token
                                                   200))]
            (ingest/ingest-concept concept {:token launchpad-token})
            (index/wait-until-indexed)

            (is (= expiration-time (:expiration-time (cache-util/get-cache-value
                                                      (url/ingest-read-caches-url)
                                                      "launchpad-user"
                                                      (name token-key)
                                                      transmit-config/mock-echo-system-token
                                                      200))))
            (testing "token cache value expires"
              (dev-sys-util/advance-time! 2000)
              (let [resp (ingest/ingest-concept concept {:token launchpad-token})]
                (index/wait-until-indexed)
                (is (= 401 (:status resp)))
                (is (= ["Launchpad token (partially redacted) [ABC-1ZZZZZXXXZZZZZ] has expired."] (:errors resp)))
                (is (empty? (cache-util/list-cache-keys (url/ingest-read-caches-url) "launchpad-user" transmit-config/mock-echo-system-token)))))

            (testing "urs cache clears after 24 hours"
              (dev-sys-util/advance-time! (* 60 60 24))
              (is (= {:error "missing key [:cmr] for cache [urs]"}
                     (cache-util/get-cache-value
                      (url/ingest-read-caches-url)
                      "urs"
                      "cmr"
                      transmit-config/mock-echo-system-token
                      404))))))))))

(deftest transient-errors-not-cached-test
  (testing "Transient errors (429, 504) are not cached"
    (let [resp (ingest/ingest-concept (data-umm-c/collection-concept {}) {:token "ABC-429-ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ"})]
      (is (= 429 (:status resp)))
      (is (some #(re-find #"Launchpad rate limit exceeded" %) (:errors resp))))

    (is (empty? (cache-util/list-cache-keys (url/ingest-read-caches-url) "launchpad-user" transmit-config/mock-echo-system-token))))

  (testing "Gateway timeout errors (504) are not cached"
    (let [resp (ingest/ingest-concept (data-umm-c/collection-concept {}) {:token "ABC-504-ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ"})]
      (is (= 504 (:status resp)))
      (is (some #(re-find #"\(gateway timeout\)" %) (:errors resp))))

    (is (empty? (cache-util/list-cache-keys (url/ingest-read-caches-url) "launchpad-user" transmit-config/mock-echo-system-token)))))

(deftest non-transient-errors-are-cached-test
  (testing "Non-transient errors are cached for 5 minutes"
    (let [token "ABC-INV-ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ"
          token-key (keyword (DigestUtils/sha256Hex token))]
      (let [resp (ingest/ingest-concept (data-umm-c/collection-concept {}) {:token token})]
        (is (= 401 (:status resp))))

      ;; Verify the error is cached
      (is (seq (cache-util/list-cache-keys (url/ingest-read-caches-url) "launchpad-user" transmit-config/mock-echo-system-token)))
      (let [cached-value (cache-util/get-cache-value
                          (url/ingest-read-caches-url)
                          "launchpad-user"
                          (name token-key)
                          transmit-config/mock-echo-system-token
                          200)]
        (is (false? (:valid cached-value)))
        (is (:error-message cached-value))
        (is (:expiration-time cached-value)))

      ;; Advance past the 5 minute cache expiration
      (dev-sys-util/advance-time! 301)

      ;; After expiration, revalidation fails and cache is evicted (not re-cached)
      (let [resp (ingest/ingest-concept (data-umm-c/collection-concept {}) {:token token})]
        (is (= 401 (:status resp))))

      ;; Verify the cache is now empty after failed revalidation
      (is (empty? (cache-util/list-cache-keys (url/ingest-read-caches-url) "launchpad-user" transmit-config/mock-echo-system-token))))))
