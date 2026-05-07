(ns cmr.indexer.test.data.concepts.collection.kms-util
  "This namespace conducts unit tests on the kms-util namespace."
  (:require
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing]]
   [cmr.common.hash-cache :as hc]
   [cmr.indexer.data.concepts.collection.kms-util :as kms-util]))

(def sample-map
  {:projects [{:short-name "KMS-SHORT" :long-name "KMS-LONG" :uuid "kms-uuid-123"}]
   :processing-levels [{:processing-level-id "PL-1" :uuid "uuid-pl-1"}]
   :temporal-resolution-range [{:temporal-resolution-range "TR-1" :uuid "uuid-tr-1"}]
   :concepts [{:short-name "C-1" :uuid "uuid-c-1"}]
   :iso-topic-categories [{:iso-topic-category "ISO-1" :uuid "uuid-iso-1"}]
   :related-urls [{:url-content-type "Type" :type "Type1" :subtype "SubType1" :uuid "uuid-ru-1"}]
   :granule-data-format [{:short-name "GDF-1" :uuid "uuid-gdf-1"}]
   :mime-type [{:mime-type "MIME-1" :uuid "uuid-mime-1"}]})

(defn- mock-cache
  [mock-fn]
  (reify hc/CmrHashCache
    (get-map [_ _] nil)
    (key-exists [_ _] true)
    (get-keys [_ _] [])
    (get-value [_ _ field] (mock-fn field))
    (get-values [_ _ _] nil)
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
     (mock-cache
      (fn [field]
        (let [projects (:projects sample-map)
              project (first (filter #(= field (string/lower-case (:short-name %))) projects))]
          (:uuid project))))
     :kms-processing-level-index
     (mock-cache
      (fn [field]
        (let [items (:processing-levels sample-map)
              item (first (filter #(= field (string/lower-case (:processing-level-id %))) items))]
          (:uuid item))))
     :kms-temporal-keywords-index
     (mock-cache
      (fn [field]
        (let [items (:temporal-resolution-range sample-map)
              item (first (filter #(= field (string/lower-case (:temporal-resolution-range %))) items))]
          (:uuid item))))
     :kms-concepts-index
     (mock-cache
      (fn [field]
        (let [items (:concepts sample-map)
              item (first (filter #(= field (string/lower-case (:short-name %))) items))]
          (:uuid item))))
     :kms-iso-topic-categories-index
     (mock-cache
      (fn [field]
        (let [items (:iso-topic-categories sample-map)
              item (first (filter #(= field (string/lower-case (:iso-topic-category %))) items))]
          (:uuid item))))
     :kms-related-urls-index
     (mock-cache
      (fn [field]
        (let [items (:related-urls sample-map)
              ;; field is a map for related urls
              item (first (filter #(= field {:url-content-type (string/lower-case (:url-content-type %))
                                             :type (string/lower-case (:type %))
                                             :subtype (string/lower-case (:subtype %))}) items))]
          (:uuid item))))
     :kms-granule-data-format-index
     (mock-cache
      (fn [field]
        (let [items (:granule-data-format sample-map)
              item (first (filter #(= field (string/lower-case (:short-name %))) items))]
          (:uuid item))))
     :kms-mime-type-index
     (mock-cache
      (fn [field]
        (let [items (:mime-type sample-map)
              item (first (filter #(= field (string/lower-case (:mime-type %))) items))]
          (:uuid item))))
     :kms-umm-c-index
     (mock-cache
      (fn [keyword-scheme]
        (case keyword-scheme
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
