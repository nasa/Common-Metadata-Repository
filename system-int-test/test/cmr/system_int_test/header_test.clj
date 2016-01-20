(ns cmr.system-int-test.header_test
  "Tests for headers in ingest and search responses"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.common-app.api.routes :as routes]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-and-ingest-headers
  (let [{ingest-headers :headers} (ingest/ingest-concept
                                    (dc/collection-concept {:short-name "Foo"})
                                    {:raw? true})
        ingest-request-id (ingest-headers "cmr-request-id")
        _ (index/wait-until-indexed)
        {search-headers :headers} (search/find-concepts-in-format
                                    "application/echo10+xml" :collection {})
        content-type (search-headers "Content-Type")
        aca-origin (search-headers "Access-Control-Allow-Origin")
        cmr-hits (search-headers "CMR-Hits")
        cmr-took (search-headers "CMR-Took")
        search-request-id (search-headers "CMR-Request-Id")
        req-id-regex #"\w{8}-\w{4}-\w{4}-\w{4}-\w{12}"]
    (is (re-matches #"application\/echo10\+xml.*" content-type))
    (is (= aca-origin "*"))
    (is (= cmr-hits "1"))
    (is (re-matches req-id-regex ingest-request-id))
    (is (re-matches req-id-regex search-request-id))
    (is (re-matches #"\d+" cmr-took))))

(defn- cmr-request-id-in-header?
  "Returns true if the given headers contain CMR-Request-Id with the given value."
  [headers cmr-request-id-value]
  (= cmr-request-id-value (get headers routes/RESPONSE_REQUEST_ID_HEADER)))

(deftest cmr-request-id-provided-in-header
  (testing "cmr-request-id is in ingest response header if it is provided in the request header"
    (let [cmr-request-id "testing-request-id"
          {:keys [headers]} (ingest/ingest-concept
                              (dc/collection-concept {:short-name "Foo"})
                              {:cmr-request-id cmr-request-id
                               :raw? true})]
      (is (cmr-request-id-in-header? headers cmr-request-id))))

  (testing "cmr-request-id is in ingest response header in error conditions"
    (let [cmr-request-id "testing-request-id"
          {:keys [headers]} (ingest/ingest-concept
                              (assoc (dc/collection-concept {}) :metadata "bad metadata")
                              {:cmr-request-id cmr-request-id
                               :raw? true})]
      (is (cmr-request-id-in-header? headers cmr-request-id))))

  (testing "cmr-request-id is in search response header if it is provided in the request header"
    (let [cmr-request-id "testing-request-id"
          {:keys [headers]} (search/find-concepts-in-format
                              "application/echo10+xml" :collection {}
                              {:headers {:cmr-request-id cmr-request-id}})]
      (is (cmr-request-id-in-header? headers cmr-request-id))))

  (testing "cmr-request-id is in search response header in error conditions"
    (let [cmr-request-id "testing-request-id"
          {:keys [headers]} (search/find-concepts-in-format
                              "application/echo10+xml" :collection
                              {:unsupported true}
                              {:headers {:cmr-request-id cmr-request-id}
                               :throw-exceptions false})]
      (is (cmr-request-id-in-header? headers cmr-request-id)))))
