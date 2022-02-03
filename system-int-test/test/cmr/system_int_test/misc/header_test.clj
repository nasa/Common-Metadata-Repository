(ns cmr.system-int-test.misc.header_test
  "Tests for headers in ingest and search responses"
  (:require
   [clj-http.client :as client]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.routes :as routes]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.url-helper :as url]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-and-ingest-headers
  (let [{ingest-headers :headers} (ingest/ingest-concept
                                    (dc/collection-concept {:short-name "Foo"})
                                    {:raw? true})
        ingest-request-id (ingest-headers "cmr-request-id")
        ingest-x-request-id (ingest-headers "x-request-id")
        response-strict-transport-security-header-ingest (ingest-headers "Strict-Transport-Security")
        response-x-content-type-options-header-ingest (ingest-headers "X-Content-Type-Options")
        response-x-frame-options-header-ingest (ingest-headers "X-Frame-Options")
        response-x-xss-protection-header-ingest (ingest-headers "X-XSS-Protection")
        _ (index/wait-until-indexed)
        {search-headers :headers} (search/find-concepts-in-format
                                    "application/echo10+xml" :collection {})
        content-type (search-headers "Content-Type")
        aca-origin (search-headers "Access-Control-Allow-Origin")
        cmr-hits (search-headers "CMR-Hits")
        cmr-took (search-headers "CMR-Took")
        search-request-id (search-headers "CMR-Request-Id")
        search-x-request-id (search-headers "X-Request-Id")
        response-strict-transport-security-header-search (search-headers "Strict-Transport-Security")
        response-x-content-type-options-header-search (search-headers "X-Content-Type-Options")
        response-x-frame-options-header-search (search-headers "X-Frame-Options")
        response-x-xss-protection-header-search (search-headers "X-XSS-Protection")
        req-id-regex #"\w{8}-\w{4}-\w{4}-\w{4}-\w{12}"]
    (is (re-matches #"application\/echo10\+xml.*" content-type))
    (is (= aca-origin "*"))
    (is (= cmr-hits "1"))
    (is (= ingest-request-id ingest-x-request-id))
    (is (= search-request-id search-x-request-id))
    (is (re-matches req-id-regex ingest-request-id))
    (is (re-matches req-id-regex search-request-id))
    (is (= response-strict-transport-security-header-ingest "max-age=31536000"))
    (is (= response-x-content-type-options-header-ingest "nosniff"))
    (is (= response-x-frame-options-header-ingest "SAMEORIGIN"))
    (is (= response-x-xss-protection-header-ingest "1; mode=block"))
    (is (= response-strict-transport-security-header-search "max-age=31536000"))
    (is (= response-x-content-type-options-header-search "nosniff"))
    (is (= response-x-frame-options-header-search "SAMEORIGIN"))
    (is (= response-x-xss-protection-header-search "1; mode=block"))
    (is (re-matches #"\d+" cmr-took))))

(defn- cmr-request-id-in-header?
  "Returns true if the given headers contain CMR-Request-Id with the given value."
  [headers cmr-request-id-value]
  (= cmr-request-id-value (get headers routes/RESPONSE_REQUEST_ID_HEADER)))

(defn- x-request-id-in-header?
  "Returns true if the given headers contain X-Request-Id with the given value."
  [headers x-request-id-value]
  (= x-request-id-value (get headers routes/RESPONSE_X_REQUEST_ID_HEADER)))

(deftest request-id-provided-in-header
  (testing "both cmr-request-id and x-request-id are in ingest response header if cmr-request-id is provided in the request header"
    (let [cmr-request-id "testing-request-id"
          {:keys [headers]} (ingest/ingest-concept
                              (dc/collection-concept {:short-name "Foo"})
                              {:cmr-request-id cmr-request-id
                               :raw? true})]
      (is (cmr-request-id-in-header? headers cmr-request-id))
      (is (x-request-id-in-header? headers cmr-request-id))))
  (testing "both cmr-request-id and x-request-id are in ingest response header if x-request-id is provided in the request header"
    (let [x-request-id "testing-request-id"
          {:keys [headers]} (ingest/ingest-concept
                              (dc/collection-concept {:short-name "Foo"})
                              {:x-request-id x-request-id
                               :raw? true})]
      (is (x-request-id-in-header? headers x-request-id))
      (is (cmr-request-id-in-header? headers x-request-id))))
  (testing "both cmr-request-id and x-request-id are in ingest response header, with the same value as cmr-request-id provided, if both cmr-request-id and x-request-id are provided with different values."
    (let [cmr-request-id "testing-request-id"
          x-request-id "testing-x-request-id"
          {:keys [headers]} (ingest/ingest-concept
                              (dc/collection-concept {:short-name "Foo"})
                              {:cmr-request-id cmr-request-id
                               :x-request-id x-request-id
                               :raw? true})]
      (is (cmr-request-id-in-header? headers cmr-request-id))
      (is (x-request-id-in-header? headers cmr-request-id))))
  (testing "both cmr-request-id and x-request-id are in ingest response header in error conditions when cmr-request-id is provided."
    (let [cmr-request-id "testing-request-id"
          {:keys [headers]} (ingest/ingest-concept
                              (assoc (dc/collection-concept {}) :metadata "bad metadata")
                              {:cmr-request-id cmr-request-id
                               :raw? true})]
      (is (cmr-request-id-in-header? headers cmr-request-id))
      (is (x-request-id-in-header? headers cmr-request-id))))
  (testing "both cmr-request-id and x-request-id are in ingest response header in error conditions when x-request-id is provided."
    (let [x-request-id "testing-request-id"
          {:keys [headers]} (ingest/ingest-concept
                              (assoc (dc/collection-concept {}) :metadata "bad metadata")
                              {:x-request-id x-request-id
                               :raw? true})]
      (is (x-request-id-in-header? headers x-request-id))
      (is (cmr-request-id-in-header? headers x-request-id))))
  (testing "both cmr-request-id and x-request-id are in search response header if cmr-request-id is provided in the request header"
    (let [cmr-request-id "testing-request-id"
          {:keys [headers]} (search/find-concepts-in-format
                              "application/echo10+xml" :collection {}
                              {:headers {:cmr-request-id cmr-request-id}})]
      (is (cmr-request-id-in-header? headers cmr-request-id))
      (is (x-request-id-in-header? headers cmr-request-id))))
  (testing "both cmr-request-id and x-request-id are in search response header if x-request-id is provided in the request header"
    (let [x-request-id "testing-request-id"
          {:keys [headers]} (search/find-concepts-in-format
                              "application/echo10+xml" :collection {}
                              {:headers {:x-request-id x-request-id}})]
      (is (x-request-id-in-header? headers x-request-id))
      (is (cmr-request-id-in-header? headers x-request-id))))
  (testing "both cmr-request-id and x-request-id are in search response header, with the same value as cmr-request-id provided, if both cmr-request-id and x-request-id are provided with different values."
    (let [cmr-request-id "testing-request-id"
          x-request-id "testing-x-request-id"
          {:keys [headers]} (search/find-concepts-in-format
                              "application/echo10+xml" :collection {}
                              {:headers {:cmr-request-id cmr-request-id :x-request-id x-request-id}})]
      (is (x-request-id-in-header? headers cmr-request-id))
      (is (cmr-request-id-in-header? headers cmr-request-id))))
  (testing "both cmr-request-id and x-request-id are in search response header in error conditions when cmr-request-id is provided"
    (let [cmr-request-id "testing-request-id"
          {:keys [headers]} (search/find-concepts-in-format
                              "application/echo10+xml" :collection
                              {:unsupported true}
                              {:headers {:cmr-request-id cmr-request-id}
                               :throw-exceptions false})]
      (is (cmr-request-id-in-header? headers cmr-request-id))
      (is (x-request-id-in-header? headers cmr-request-id))))
  (testing "both cmr-request-id and x-request-id are in search response header in error conditions when x-request-id is provided"
    (let [x-request-id "testing-request-id"
          {:keys [headers]} (search/find-concepts-in-format
                              "application/echo10+xml" :collection
                              {:unsupported true}
                              {:headers {:x-request-id x-request-id}
                               :throw-exceptions false})]
      (is (x-request-id-in-header? headers x-request-id))
      (is (cmr-request-id-in-header? headers x-request-id)))))

(deftest cors-headers
  (testing "allowed headers in options search request when ECHO-Tokens are allowed"
    (dev-sys-util/eval-in-dev-sys `(acl/set-allow-echo-token! true))
    (let [allowed-headers (-> (client/options (url/search-url :collection))
                              (get-in [:headers "Access-Control-Allow-Headers"])
                              (string/split #", "))]
      (is (some #{"Echo-Token"} allowed-headers))
      (is (some #{"Authorization"} allowed-headers))
      (is (some #{"Client-Id"} allowed-headers))
      (is (some #{"CMR-Request-Id"} allowed-headers))
      (is (some #{"X-Request-Id"} allowed-headers))
      (is (some #{"CMR-Scroll-Id"} allowed-headers))
      (is (some #{"CMR-Search-After"} allowed-headers))))

  (testing "that Echo-Token header is not allowed when the toggle for it is off"
    (dev-sys-util/eval-in-dev-sys `(acl/set-allow-echo-token! false))
    (let [allowed-headers (-> (client/options (url/search-url :collection))
                              (get-in [:headers "Access-Control-Allow-Headers"])
                              (string/split #", "))]
      (is (not-any? #{"Echo-Token"} allowed-headers))
      (dev-sys-util/eval-in-dev-sys `(acl/set-allow-echo-token! true))))

  (testing "exposed headers in search request"
    (let [exposed-headers (-> (client/head (url/search-url :collection))
                              (get-in [:headers "Access-Control-Expose-Headers"])
                              (string/split #", "))]
      (is (some #{"CMR-Hits"} exposed-headers))
      (is (some #{"CMR-Request-Id"} exposed-headers))
      (is (some #{"X-Request-Id"} exposed-headers))
      (is (some #{"CMR-Scroll-Id"} exposed-headers))
      (is (some #{"CMR-Search-After"} exposed-headers)))))
