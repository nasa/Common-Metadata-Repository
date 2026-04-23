(ns cmr.elastic-utils.test.elastic-results-to-query-results
  (:require
   [clojure.test :refer :all]
   [cmr.elastic-utils.search.es-results-to-query-results :as es-results]
   ;; Load multimethod implementations for concept-type-specific revision-id extraction.
   [cmr.elastic-utils.search.elastic-results-to-query-results]))

(deftest granule-revision-id-defaults-to-elastic-version
  (let [elastic-result {:_version 1
                        :_source {:revision-id 2}}]
    (is (= 1 (es-results/get-revision-id-from-elastic-result :granule elastic-result)))))

(deftest collection-revision-id-comes-from-stored-source-field
  (let [elastic-result {:_version 1
                        :_source {:revision-id 3}}]
    (is (= 3 (es-results/get-revision-id-from-elastic-result :collection elastic-result)))))
