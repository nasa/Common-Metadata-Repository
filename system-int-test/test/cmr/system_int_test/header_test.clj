(ns cmr.system-int-test.header_test
  "Tests for headers in ingest and search responses"
  (:require [clojure.test :refer :all]
            [cmr.search.api.routes :as sr]
            [cmr.system-int-test.utils.metadata-db-util :as mdb]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]))

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
