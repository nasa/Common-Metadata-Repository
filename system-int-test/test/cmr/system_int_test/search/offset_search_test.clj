(ns cmr.system-int-test.search.offset-search-test
  "Tests for search paging."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.common.concepts :as concepts]
            [cmr.system-int-test.data2.core :as d2c]
            [cmr.search.services.parameters.parameter-validation :as pm]
    ;; borrow code to create test data from the paging-search-test namespace
            [cmr.system-int-test.search.paging-search-test :as pst]))

(use-fixtures :each
              (compose-fixtures
                (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"})
                (fn [f]
                  (pst/create-collections)
                  (f))))

(deftest search-with-offset
  (testing "invalid offset param values"
    (are [offset]
      (= "offset must be a number greater than or equal to zero"
         (first (:errors (search/find-refs :collection {"offset" offset}))))
      "asdf"
      "-1"))
  (testing "invalid combination of offset and page-num"
    (is (= "Only one of offset or page-num may be specified"
           (first (:errors (search/find-refs :collection {"page_num" 2 "offset" 100}))))))
  (testing "with valid offset"
    (is (= (:refs (search/find-refs :collection {"page_num" 1 "page_size" 5}))
           (:refs (search/find-refs :collection {"offset" 0 "page_size" 5}))))
    (is (= (:refs (search/find-refs :collection {"page_num" 2 "page_size" 5}))
           (:refs (search/find-refs :collection {"offset" 5 "page_size" 5}))))))
