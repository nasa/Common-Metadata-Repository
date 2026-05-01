(ns cmr.indexer.test.data.concepts.collection.project-keyword
  "This namespace conducts unit tests on the kms-util namespace."
  (:require
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing]]
   [cmr.common.hash-cache :as hc]
   [cmr.indexer.data.concepts.collection.kms-util :as kms-util]
   [cmr.common-app.services.kms-lookup :as kms-lookup]))

(def sample-map
  {:projects [{:short-name "KMS-SHORT" :long-name "KMS-LONG" :uuid "kms-uuid-123"}]
   :processing-levels [{:processing-level-id "PL-1" :uuid "uuid-pl-1"}]
   :temporal-resolution-range [{:temporal-resolution-range "TR-1" :uuid "uuid-tr-1"}]
   :concepts [{:short-name "C-1" :uuid "uuid-c-1"}]
   :iso-topic-categories [{:iso-topic-category "ISO-1" :uuid "uuid-iso-1"}]
   :related-urls [{:url-content-type "Type" :type "Type1" :subtype "SubType1" :uuid "uuid-ru-1"}]
   :granule-data-format [{:short-name "GDF-1" :uuid "uuid-gdf-1"}]
   :mime-type [{:mime-type "MIME-1" :uuid "uuid-mime-1"}]})

(defn- dummy-cache
  [mock-fn]
  (reify hc/CmrHashCache
    (get-map [_ _] nil)
    (key-exists [_ _] true)
    (get-keys [_ _] [])
    (get-value [_ _ field] (mock-fn field))
    (get-values [_ _ fields] nil)
    (reset [_] nil)
    (reset [_ _] nil)
    (set-value [_ _ _ _] nil)
    (set-values [_ _ _] nil)
    (remove-value [_ _ _] nil)
    (cache-size [_ _] 0)))

(def test-context
  {:system
   {:caches
    {:kms-project-index
     (dummy-cache
      (fn [field]
        (let [projects (:projects sample-map)
              project (first (filter #(= field (string/lower-case (:short-name %))) projects))]
          (:uuid project))))
     :kms-processing-level-index
     (dummy-cache
      (fn [field]
        (let [items (:processing-levels sample-map)
              item (first (filter #(= field (string/lower-case (:processing-level-id %))) items))]
          (:uuid item))))
     :kms-umm-c-index
     (dummy-cache
      (fn [keyword-scheme]
        (case keyword-scheme
          :temporal-keywords
          {{:temporal-resolution-range "tr-1"} {:uuid "uuid-tr-1"}}
          :concepts
          {{:short-name "c-1"} {:uuid "uuid-c-1"}}
          :iso-topic-categories
          {{:iso-topic-category "iso-1"} {:uuid "uuid-iso-1"}}
          :related-urls
          {{:url-content-type "type" :type "type1" :subtype "subtype1"} {:uuid "uuid-ru-1"}}
          :granule-data-format
          {{:short-name "gdf-1"} {:uuid "uuid-gdf-1"}}
          :mime-type
          {{:mime-type "mime-1"} {:uuid "uuid-mime-1"}}
          nil)))}}})

(deftest project-short-name->elastic-doc-test
  (testing "Project found in KMS"
    (is (= {:uuid "kms-uuid-123"}
           (kms-util/project-short-name->elastic-doc test-context "KMS-SHORT"))))

  (testing "Project not found in KMS"
    (is (nil?
         (kms-util/project-short-name->elastic-doc test-context "NOT-IN-KMS")))))
         
(deftest processing-level-id->elastic-doc-test
  (testing "Processing level found in KMS"
    (is (= {:uuid "uuid-pl-1"}
           (kms-util/processing-level-id->elastic-doc test-context "PL-1"))))

  (testing "Processing level not found in KMS"
    (is (nil?
         (kms-util/processing-level-id->elastic-doc test-context "NOT-IN-KMS")))))

(deftest temporal-keyword->elastic-doc-test
  (testing "Temporal keyword found in KMS"
    (is (= {:uuid "uuid-tr-1"}
           (kms-util/temporal-keyword->elastic-doc test-context "TR-1"))))

  (testing "Temporal keyword not found in KMS"
    (is (nil?
         (kms-util/temporal-keyword->elastic-doc test-context "NOT-IN-KMS")))))

(deftest concept->elastic-doc-test
  (testing "Concept found in KMS"
    (is (= {:uuid "uuid-c-1"}
           (kms-util/concept->elastic-doc test-context {:short-name "C-1"}))))

  (testing "Concept not found in KMS"
    (is (nil?
         (kms-util/concept->elastic-doc test-context {:short-name "NOT-IN-KMS"})))))

(deftest iso-topic-category->elastic-doc-test
  (testing "ISO topic category found in KMS"
    (is (= {:uuid "uuid-iso-1"}
           (kms-util/iso-topic-category->elastic-doc test-context "ISO-1"))))

  (testing "ISO topic category not found in KMS"
    (is (nil?
         (kms-util/iso-topic-category->elastic-doc test-context "NOT-IN-KMS")))))

(deftest related-url->elastic-doc-test
  (testing "Related url found in KMS"
    (is (= {:uuid "uuid-ru-1"}
           (kms-util/related-url->elastic-doc test-context {:url-content-type "Type" :type "Type1" :subtype "SubType1"}))))

  (testing "Related url not found in KMS"
    (is (nil?
         (kms-util/related-url->elastic-doc test-context {:url-content-type "Type" :type "NOT-IN-KMS"})))))

(deftest granule-data-format->elastic-doc-test
  (testing "Granule data format found in KMS"
    (is (= {:uuid "uuid-gdf-1"}
           (kms-util/granule-data-format->elastic-doc test-context {:short-name "GDF-1"}))))

  (testing "Granule data format not found in KMS"
    (is (nil?
         (kms-util/granule-data-format->elastic-doc test-context {:short-name "NOT-IN-KMS"})))))

(deftest mime-type->elastic-doc-test
  (testing "Mime type found in KMS"
    (is (= {:uuid "uuid-mime-1"}
           (kms-util/mime-type->elastic-doc test-context "MIME-1"))))

  (testing "Mime type not found in KMS"
    (is (nil?
         (kms-util/mime-type->elastic-doc test-context "NOT-IN-KMS")))))
