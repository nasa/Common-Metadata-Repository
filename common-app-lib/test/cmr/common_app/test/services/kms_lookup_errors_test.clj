(ns cmr.common-app.test.services.kms-lookup-errors-test
  "Unit tests for specific connection errors coming from kms-lookup"
  (:require
    [clojure.test :refer [deftest is testing]]
    [cmr.common-app.services.kms-lookup :as kms-lookup]))

(def create-context
  "Creates a testing concept with the KMS caches."
  {:system {:caches {kms-lookup/kms-short-name-cache-key (kms-lookup/create-kms-short-name-cache)
                     kms-lookup/kms-umm-c-cache-key (kms-lookup/create-kms-umm-c-cache)
                     kms-lookup/kms-location-cache-key (kms-lookup/create-kms-location-cache)
                     kms-lookup/kms-measurement-cache-key (kms-lookup/create-kms-measurement-cache)}}})

(def create-context-broken
  "Creates a testing concept with the KMS caches."
  (-> {:system {:caches {kms-lookup/kms-short-name-cache-key (kms-lookup/create-kms-short-name-cache)
                         kms-lookup/kms-umm-c-cache-key (kms-lookup/create-kms-umm-c-cache)
                         kms-lookup/kms-location-cache-key (kms-lookup/create-kms-location-cache)
                         kms-lookup/kms-measurement-cache-key (kms-lookup/create-kms-measurement-cache)}}}
      (update-in
       [:system :caches :kms-measurement-index :read-connection :spec :host]
       (constantly "example.gov"))))

(deftest lookup-by-short-name-test
  (testing "cache connection error"
    (is (nil? (kms-lookup/lookup-by-short-name create-context "keyword-scheme" "short-name")))))

(deftest lookup-by-location-string-test
  (testing "cache connection error"
    (is (nil? (kms-lookup/lookup-by-location-string create-context "location-string")))))

(deftest lookup-by-umm-c-keyword-data-format-test
  (testing "cache connection error"
    (is (nil? (kms-lookup/lookup-by-umm-c-keyword-data-format create-context {} "umm-c-keyword")))))

(deftest lookup-by-umm-c-keyword-platforms-test
  (testing "cache connection error"
    (is (nil? (kms-lookup/lookup-by-umm-c-keyword-platforms create-context {} {})))))

(deftest lookup-by-umm-c-keyword-test
  (testing "cache connection error"
    (is (nil? (kms-lookup/lookup-by-umm-c-keyword create-context {} {})))))

(deftest lookup-by-measurement-test
  (testing "cache connection error"
    (let [actual (kms-lookup/lookup-by-measurement
                  create-context-broken
                  {:MeasurementContextMedium "The Force"
                   :MeasurementObject "Midichlorians"
                   :MeasurementQuantities [{:Value "Cell Count"}]})]
      (is (nil? actual)))))
