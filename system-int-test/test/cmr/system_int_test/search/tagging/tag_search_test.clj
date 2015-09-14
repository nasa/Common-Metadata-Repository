(ns cmr.system-int-test.search.tagging.tag-search-test
  "This tests associating tags with collections."
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.common.util :refer [are2]]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.tag-util :as tags]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]))

(use-fixtures :each (ingest/reset-fixture {}))


(deftest search-for-tags-validation-test
  ;; TODO come up with some validations
  )

#_(deftest search-for-tags-test
  (let [user1-token (e/login (s/context) "user1")
        user2-token (e/login (s/context) "user2")
        tag1 (tags/save-tag user1-token {:namespace "Namespace1"
                                         :value "Value1"
                                         :category "Category1"
                                         :description "This is a description1."})
        tag2 (tags/save-tag user2-token {:namespace "Namespace1"
                                         :value "Value2"
                                         :description "This is a description1."})
        tag3 (tags/save-tag user1-token {:namespace "Namespace2"
                                         :value "Value1"
                                         :category "Category2"
                                         :description "This is a description2."})
        tag4 (tags/save-tag user2-token {:namespace "Namespace2"
                                         :value "Value2"
                                         :category "Category2"
                                         :description "This is a description2."})
        tag5 (tags/save-tag user1-token {:namespace "Namespace Other"
                                         :value "Value Other"
                                         :description "This is a description2."})
        all-tags [tag1 tag2 tag3 tag4 tag5]]
    (index/wait-until-indexed)

    ;; TODO test searching by:
    ;; - namespace
    ;; - value
    ;; - category
    ;; - description
    ;; - originator-id
    ;; - combinations of things

    (let [response (-> (tags/search {})
                       (dissoc :took))]
      (is (= {:status 200
              :hits 5
              :tags (map #(select-keys % [:namespace :value :description :category :originator-id])
                         all-tags)}
            response)))


    ;; TODO add a separate paging test with something like 20 tags


    ))



