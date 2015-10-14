(ns cmr.system-int-test.search.tagging.tag-index-tombstone-test
  "This tests indexing tombstoned tags."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.tag-util :as tags]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.transmit.metadata-db :as tmdb]
            [cmr.system-int-test.system :as s]))

(use-fixtures :each (join-fixtures [(ingest/reset-fixture {})
                                    tags/grant-all-tag-fixture]))


