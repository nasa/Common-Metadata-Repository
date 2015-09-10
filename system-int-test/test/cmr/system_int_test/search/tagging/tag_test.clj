(ns cmr.system-int-test.search.tagging.tag-test
  "This tests the CMR Search API's tagging capabilities"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.metadata-db-util :as mdb]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.tag-util :as tags]
            [cmr.common.mime-types :as mt]
            [cheshire.core :as json]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest create-tag-test
  ;; TODO
  ;; verify user id is in originator id
  ;; Verify tag with namespace or value with group separator is rejected

  ;; TODO pass in token as the first argument
  (let [tag {:namespace "org.nasa.something"
             :category "QA"
             :value "quality"
             :description "A very good tag"}
        {:keys [status concept-id revision-id]} (tags/create-tag tag)]
    (is (= 200 status))
    (is concept-id)
    (is (= 1 revision-id))

    (let [concept (mdb/get-concept concept-id revision-id)]
      (is (= {:concept-type :tag
              :native-id (str "org.nasa.something" (char 29) "quality")
              ;; TODO Get James or change it yourself that provider id shouldn't be returned if we don't send it in
              :provider-id "CMR"
              :format mt/edn
              ;; TODO the originator id should be in the tag
              :metadata (pr-str tag)
              :deleted false
              :concept-id concept-id
              :revision-id revision-id}
             (dissoc concept :revision-date))))))

; (mdb/get-concept "T1200000000-CMR" )

;; TODO tests
;; tags without required fields
;; Tags that aren't JSON
;; No token passed in