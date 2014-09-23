(ns cmr.system-int-test.search.search-error-format-test
  "Integration tests for search error format"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.search-util :as search]))

(deftest search-in-json-or-xml-format

  (testing "invalid format"
    (is (= {:errors ["The mime type [application/echo11+xml] is not supported."],
            :status 400}
           (search/get-search-failure-xml-data
             (search/find-concepts-in-format
               "application/echo11+xml" :collection {})))))

  (is (= {:status 400,
          :errors ["Parameter [unsupported] was not recognized."]}
         (search/find-refs :collection {:unsupported "dummy"})))

  (is (= {:status 400,
          :errors ["Parameter [unsupported] was not recognized."]}
         (search/find-refs-json :collection {:unsupported "dummy"}))))