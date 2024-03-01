(ns cmr.indexer.test.data.humanizer-fetcher-test
  (:require
    [clojure.test :refer :all]
    [cmr.indexer.data.humanizer-fetcher :as humanizer-fetcher])
  (:import (clojure.lang ExceptionInfo)))

(def create-context
  "Creates a testing concept with the KMS caches."
  {:system {:caches {humanizer-fetcher/humanizer-cache-key (humanizer-fetcher/create-cache-client)}}})

(deftest get-humanizer-instructions-test
  (testing "redis connection error"
    (is (thrown? ExceptionInfo (humanizer-fetcher/get-humanizer-instructions create-context)))))