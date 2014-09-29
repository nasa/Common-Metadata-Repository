(ns cmr.search.test.api.routes
  (:require [clojure.test :refer :all]
            [cmr.common.mime-types :as mt]
            [cmr.search.api.routes :as r]))

(deftest validate-search-result-mime-type-test
  (testing "valid mime types"
    (mt/validate-request-mime-type "application/json" r/search-result-supported-mime-types)
    (mt/validate-request-mime-type "application/xml" r/search-result-supported-mime-types)
    (mt/validate-request-mime-type "*/*" r/search-result-supported-mime-types))
  (testing "invalid mime types"
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"The mime type \[application/foo\] is not supported."
          (mt/validate-request-mime-type "application/foo" r/search-result-supported-mime-types)))))

