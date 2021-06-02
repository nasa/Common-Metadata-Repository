(ns cmr.system-int-test.bootstrap.bulk-index.tag-test
  "Integration test for CMR bulk index tag operations."
  (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.oracle.connection :as oracle]
   [cmr.system-int-test.bootstrap.bulk-index.core :as core]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.tag-util :as tags]))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture {"provguid1" "PROV1"}
                                            {:grant-all-ingest? true
                                             :grant-all-search? true
                                             :grant-all-access-control? false})
                      tags/grant-all-tag-fixture]))

;; This test is to verify that bulk index works with tombstoned tag associations
(deftest ^:oracle bulk-index-collections-with-tag-association-test
  (s/only-with-real-database
    (let [[coll1 coll2] (for [n (range 1 3)]
                          (d/ingest "PROV1" (dc/collection {:entry-title (str "coll" n)})))
          ;; Wait until collections are indexed so tags can be associated with them
          _ (index/wait-until-indexed)
          user1-token (e/login (s/context) "user1")
          tag1-colls [coll1 coll2]
          tag-key "tag1"
          tag1 (tags/save-tag
                 user1-token
                 (tags/make-tag {:tag-key tag-key})
                 tag1-colls)]

      (index/wait-until-indexed)
      ;; dissociate tag1 from coll2 and not send indexing events
      (core/disable-automatic-indexing)
      (tags/dissociate-by-query user1-token tag-key {:concept_id (:concept-id coll2)})
      (core/reenable-automatic-indexing)

      (bootstrap/bulk-index-provider "PROV1")
      (index/wait-until-indexed)

      (testing "All tag parameters with XML references"
        (is (d/refs-match? [coll1]
                           (search/find-refs :collection {:tag-key "tag1"})))))))
