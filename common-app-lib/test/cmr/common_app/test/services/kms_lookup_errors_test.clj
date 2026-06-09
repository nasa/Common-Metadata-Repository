`(ns cmr.common-app.test.services.kms-lookup-errors-test
  "Unit tests for specific connection errors coming from kms-lookup"
  (:require
    [clojure.test :refer [deftest is testing]]
    [cmr.common-app.services.kms-lookup :as kms-lookup]))

(def create-context
  "Creates a testing concept with the KMS caches."
  {:system {:caches {kms-lookup/kms-short-name-cache-key (kms-lookup/create-kms-short-name-cache)
                     kms-lookup/kms-projects-cache-key (kms-lookup/create-kms-project-uuid-cache)
                     kms-lookup/kms-umm-c-cache-key (kms-lookup/create-kms-umm-c-cache)
                     kms-lookup/kms-location-cache-key (kms-lookup/create-kms-location-cache)
                     kms-lookup/kms-measurement-cache-key (kms-lookup/create-kms-measurement-cache)
                     kms-lookup/kms-processing-level-cache-key (kms-lookup/create-kms-processing-level-uuid-cache)
                     kms-lookup/kms-science-keywords-cache-key (kms-lookup/create-kms-science-keywords-uuid-cache)
                     kms-lookup/kms-platforms-cache-key (kms-lookup/create-kms-platforms-uuid-cache)
                     kms-lookup/kms-instruments-cache-key (kms-lookup/create-kms-instruments-uuid-cache)
                     kms-lookup/kms-providers-cache-key (kms-lookup/create-kms-providers-uuid-cache)
                     kms-lookup/kms-spatial-keywords-cache-key (kms-lookup/create-kms-spatial-keywords-uuid-cache)
                     kms-lookup/kms-concepts-cache-key (kms-lookup/create-kms-concepts-uuid-cache)
                     kms-lookup/kms-iso-topic-categories-cache-key (kms-lookup/create-kms-iso-topic-categories-uuid-cache)
                     kms-lookup/kms-granule-data-format-cache-key (kms-lookup/create-kms-granule-data-format-uuid-cache)
                     kms-lookup/kms-mime-type-cache-key (kms-lookup/create-kms-mime-type-uuid-cache)
                     kms-lookup/kms-related-urls-cache-key (kms-lookup/create-kms-related-urls-uuid-cache)
                     kms-lookup/kms-temporal-keywords-cache-key (kms-lookup/create-kms-temporal-keywords-uuid-cache)}}})

(def create-context-broken
  "Creates a testing concept with the KMS caches."
  (-> {:system {:caches {kms-lookup/kms-short-name-cache-key (kms-lookup/create-kms-short-name-cache)
                         kms-lookup/kms-projects-cache-key (kms-lookup/create-kms-project-uuid-cache)
                         kms-lookup/kms-umm-c-cache-key (kms-lookup/create-kms-umm-c-cache)
                         kms-lookup/kms-location-cache-key (kms-lookup/create-kms-location-cache)
                         kms-lookup/kms-measurement-cache-key (kms-lookup/create-kms-measurement-cache)
                         kms-lookup/kms-processing-level-cache-key (kms-lookup/create-kms-processing-level-uuid-cache)
                         kms-lookup/kms-science-keywords-cache-key (kms-lookup/create-kms-science-keywords-uuid-cache)
                         kms-lookup/kms-platforms-cache-key (kms-lookup/create-kms-platforms-uuid-cache)
                         kms-lookup/kms-instruments-cache-key (kms-lookup/create-kms-instruments-uuid-cache)
                         kms-lookup/kms-providers-cache-key (kms-lookup/create-kms-providers-uuid-cache)
                         kms-lookup/kms-spatial-keywords-cache-key (kms-lookup/create-kms-spatial-keywords-uuid-cache)
                         kms-lookup/kms-concepts-cache-key (kms-lookup/create-kms-concepts-uuid-cache)
                         kms-lookup/kms-iso-topic-categories-cache-key (kms-lookup/create-kms-iso-topic-categories-uuid-cache)
                         kms-lookup/kms-granule-data-format-cache-key (kms-lookup/create-kms-granule-data-format-uuid-cache)
                         kms-lookup/kms-mime-type-cache-key (kms-lookup/create-kms-mime-type-uuid-cache)
                         kms-lookup/kms-related-urls-cache-key (kms-lookup/create-kms-related-urls-uuid-cache)
                         kms-lookup/kms-temporal-keywords-cache-key (kms-lookup/create-kms-temporal-keywords-uuid-cache)}}}
      (update-in
       [:system :caches :kms-measurement-index :read-connection :spec :host]
       (constantly "example.gov"))))

(deftest lookup-project-by-short-name-test
  (testing "cache connection error"
    (is (nil? (kms-lookup/lookup-project-by-short-name create-context "short-name")))))

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
