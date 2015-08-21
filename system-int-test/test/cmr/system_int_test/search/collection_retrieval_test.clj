(ns cmr.system-int-test.search.collection-retrieval-test
  "Integration test for collection retrieval with cmr-concept-id"
  (:require [clojure.test :refer :all]
            [cmr.umm.echo10.collection :as c]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
            [cmr.common.mime-types :as mt]))

