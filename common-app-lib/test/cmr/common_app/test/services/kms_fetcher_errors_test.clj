(ns cmr.common-app.test.services.kms-fetcher-errors-test
  "Unit tests for specific connection errors coming from kms-lookup"
  (:require
   [clojure.test :refer [deftest is testing]]
   [cmr.common-app.services.kms-fetcher :as kms-fetcher]))

(def create-context
  "Create a broken KMS cache connection so that we get an error"
    (update-in create-context
               [:system :caches :kms :read-connection :spec :host]
               (constantly "example.gov")))

(deftest fetch-gcmd-keywords-map-test
  (testing "cache connection error"
    (let [fun #'kms-fetcher/fetch-gcmd-keywords-map]
      (is (thrown? Exception (fun create-context))))))

(deftest get-kms-index-test
  (testing "cache connection error"
    (is (nil? (kms-fetcher/get-kms-index create-context)))))
