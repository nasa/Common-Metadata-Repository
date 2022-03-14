(ns cmr.indexer.test.services.index-service
  "Tests for index service"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.indexer.services.index-service :as index-svc]))

(deftest index-concept-invalid-input-test
  (testing "invalid input"
    (are [concept-id revision-id]
         (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Concept-id .* and revision-id .* cannot be null"
           (index-svc/index-concept-by-concept-id-revision-id {} concept-id revision-id true))

         "C123-PROV1" nil
         nil 1
         nil nil)))

(deftest determine-reindex-batch-size-test
  (testing "determining the reindexing batch size based on the given provider."
    (are3 [expected-size provider]
      (is (= expected-size (index-svc/determine-reindex-batch-size provider)))

      "Testing a provider that has normal sized collections."
      (index-svc/collection-reindex-batch-size) "NSIDC"

      "Testing a provider that has large sized collections."
      (index-svc/collection-large-file-providers-reindex-batch-size) "GHRSSTCWIC")))
