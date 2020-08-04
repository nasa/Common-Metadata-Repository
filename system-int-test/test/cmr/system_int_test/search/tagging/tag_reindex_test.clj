(ns cmr.system-int-test.search.tagging.tag-reindex-test
  "This tests re-index tags."
  (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.tag-util :as tags]))

(use-fixtures :each (join-fixtures [(ingest/reset-fixture {})
                                    tags/grant-all-tag-fixture]))

(deftest reindex-tags-test
  (let [user1-token (e/login (s/context) "user1")
        user2-token (e/login (s/context) "user2")
        tag1 (tags/save-tag user1-token (tags/make-tag {:tag-key "tag1"}))
        tag1-2 (tags/save-tag user1-token (dissoc tag1 :revision-id))
        tag2 (tags/save-tag user2-token (tags/make-tag {:tag-key "tag2"}))
        tag3 (tags/save-tag user1-token (tags/make-tag {:tag-key "tag3"}))
        tag3-tombstone (tags/delete-tag user1-token (:tag-key tag3))
        all-tags [tag1-2 tag2]]
    (index/wait-until-indexed)

    ;; Now I should find all tags when searching - not the tombstoned tag
    (tags/assert-tag-search all-tags (tags/search {}))

    ;; Delete tags from elasticsearch index
    (index/delete-tags-from-elastic all-tags)
    (index/wait-until-indexed)

    ;; Now searching tags finds nothing
    (tags/assert-tag-search [] (tags/search {}))

    ;; Re-index all tags
    (index/reindex-tags)
    (index/wait-until-indexed)

    ;; Now I should find all tags when searching - not the tombstoned tag
    (tags/assert-tag-search all-tags (tags/search {}))))
