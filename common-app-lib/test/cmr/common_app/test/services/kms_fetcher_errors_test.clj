(ns cmr.common-app.test.services.kms-fetcher-errors-test
  "Unit tests for specific connection errors coming from kms-lookup"
  (:require
    [clojure.test :refer [deftest is testing]]
    [cmr.common-app.services.kms-fetcher :as kms-fetcher]))

(def create-context
  "Creates a testing concept with the KMS caches."
   {:system {:caches {kms-fetcher/kms-cache-key (kms-fetcher/create-kms-cache)}}})

(deftest fetch-gcmd-keywords-map-test
  (testing "cache connection error"
    (let [fun #'kms-fetcher/fetch-gcmd-keywords-map]
      (is (nil? (fun create-context))))))

(deftest get-kms-index-test
  (testing "cache connection error"
    (is (nil? (kms-fetcher/get-kms-index create-context)))))
