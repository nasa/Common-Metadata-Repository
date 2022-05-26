(ns cmr.system-int-test.site.metadata-preview-test
  "Integration tests for metadata preview page"
  (:require
   [clojure.test :refer :all]
   [cmr.system-int-test.data2.core :as data]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
   [cmr.system-int-test.utils.html-helper :refer [find-element-by-id]]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.url-helper :as url]
   [crouton.html :as html]))

(use-fixtures :each (join-fixtures [(ingest/reset-fixture {"provguid1" "PROV1"})]))

(deftest metadata-preview-test
  (let [coll (data/ingest-umm-spec-collection
              "PROV1"
              (data-umm-c/collection {:concept-id "C1-PROV1"
                                      :TemporalExtents
                                      [(data-umm-cmn/temporal-extent
                                        {:beginning-date-time "1970-01-01T00:00:00Z"})]}))]
    (index/wait-until-indexed)
    (testing "Page renders"
      (let [page-data (html/parse (format "%sconcepts/%s.html"
                                          (url/search-root)
                                          (:concept-id coll)))]
        (is (seq page-data))

        (testing "elements are present"
          ;; This checks for the raw elements, the React elements will not be rendered
          (is (seq (find-element-by-id "earthdata-tophat-script" page-data)))
          (is (seq (find-element-by-id "metadata-preview" page-data))))))))
