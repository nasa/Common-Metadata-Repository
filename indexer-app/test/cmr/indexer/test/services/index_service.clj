(ns cmr.indexer.test.services.index-service
  "Tests for index service"
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.indexer.services.index-service :as index-svc]))

(deftest index-concept-invalid-input-test
  (testing "invalid input"
    (are [concept-id revision-id]
         (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Concept-id .* cannot be null"
           (index-svc/index-concept-by-concept-id-revision-id {} concept-id revision-id true))

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

(deftest index-log-size-test
  (testing "Make sure that any new time to visibility index log is not larger then the original"

    ;; The time to visibility log can print in the millions per day and there are two different
    ;; versions of this log depending on the value of defconfig reduced-indexer-log. When true a
    ;; JSON version of this log is printed which contains more information but should still be
    ;; shorter as Splunk storage is expensive.

    (let [concept-id "G1001055217-ASF"
          revision-id "123"
          milliseconds 1024
          all-revisions-index? true
          time-to-visibility-text (var index-svc/time-to-visibility-text) ;; private function
          time-to-visibility-json (var index-svc/time-to-visibility-json) ;; private function
          text (time-to-visibility-text concept-id milliseconds)
          json (time-to-visibility-json concept-id revision-id milliseconds all-revisions-index?)]

      (is (<= (count json) (count text))))))

(deftest time-to-visibility-json-test
  (testing "Ensure that the output of the time-to-vilibility-json function is in fact JSON"
    (let [time-to-visibility-json (var index-svc/time-to-visibility-json) ;; private function
          raw-json (time-to-visibility-json "ACL123-CMR", 1, 1234, false)
          data (json/parse-string raw-json true)]
      (is (some? data) "time-to-visibility-json parsing test")
      (is (= "ACL" (:ct data)) "Checking concept type")
      (is (= "index-vis" (:mg data)) "Checking message id"))))
