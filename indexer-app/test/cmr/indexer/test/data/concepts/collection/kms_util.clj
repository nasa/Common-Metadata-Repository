;; (ns cmr.indexer.test.data.concepts.collection.kms-util
;;   "This namespace conducts unit tests on the kms-util namespace."
;;   (:require
;;    [clojure.test :refer :all]
;;    [cmr.common-app.services.kms-lookup :as kms-lookup]
;;    [cmr.indexer.data.concepts.collection.kms-util :as kms-util]
;;    [cmr.redis-utils.test.test-util :as redis-embedded-fixture]))

;; (def ^:private sample-keyword-map
;;   {:projects [{:short-name "KMS-SHORT" :long-name "KMS-LONG" :uuid "kms-uuid-123"}]
;;    :processing-levels [{:processing-level-id "PL-1" :uuid "uuid-pl-1"}]
;;    :temporal-resolution-range [{:temporal-resolution-range "TR-1" :uuid "uuid-tr-1"}]
;;    :concepts [{:concept "C-1" :uuid "uuid-c-1"}]
;;    :iso-topic-categories [{:iso-topic-category "ISO-1" :uuid "uuid-iso-1"}]
;;    :related-urls [{:url-content-type "Type" :type "Type1" :subtype "SubType1" :uuid "uuid-ru-1"}]
;;    :granule-data-format [{:short-name "GDF-1" :uuid "uuid-gdf-1"}]
;;    :mime-type [{:mime-type "MIME-1" :uuid "uuid-mime-1"}]})

;; (def create-context
;;   {:system {:caches {kms-lookup/kms-short-name-cache-key (kms-lookup/create-kms-short-name-cache)
;;                      kms-lookup/kms-projects-cache-key   (kms-lookup/create-kms-project-uuid-cache)
;;                      kms-lookup/kms-umm-c-cache-key      (kms-lookup/create-kms-umm-c-cache)
;;                      kms-lookup/kms-location-cache-key   (kms-lookup/create-kms-location-cache)
;;                      kms-lookup/kms-measurement-cache-key (kms-lookup/create-kms-measurement-cache)}}})

;; (defn redis-cache-fixture
;;   [f]
;;   (let [context create-context]
;;     (kms-lookup/create-kms-index context sample-keyword-map)
;;     (f)))

;; (use-fixtures :once (join-fixtures [redis-embedded-fixture/embedded-redis-server-fixture
;;                                     redis-cache-fixture]))

;; (deftest project-short-name->elastic-doc-test
;;   (let [context create-context]
;;     (testing "Project found in KMS"
;;       (is (= {:uuid "kms-uuid-123"}
;;              (kms-util/project-short-name->elastic-doc context "KMS-SHORT"))))

;;     (testing "Project not found in KMS"
;;       (is (nil?
;;            (kms-util/project-short-name->elastic-doc context "NOT-IN-KMS"))))))

;; (deftest processing-level-id->elastic-doc-test
;;   (let [context create-context]
;;     (testing "Processing level found in KMS"
;;       (is (= {:uuid "uuid-pl-1"}
;;              (kms-util/processing-level-id->elastic-doc context "PL-1"))))

;;     (testing "Processing level not found in KMS"
;;       (is (nil?
;;            (kms-util/processing-level-id->elastic-doc context "NOT-IN-KMS"))))))

;; (deftest temporal-keyword->elastic-doc-test
;;   (let [context create-context]
;;     (testing "Temporal keyword found in KMS"
;;       (is (= {:uuid "uuid-tr-1"}
;;              (kms-util/temporal-keyword->elastic-doc context "TR-1"))))

;;     (testing "Temporal keyword not found in KMS"
;;       (is (nil?
;;            (kms-util/temporal-keyword->elastic-doc context "NOT-IN-KMS"))))))

;; (deftest concept->elastic-doc-test
;;   (let [context create-context]
;;     (testing "Concept found in KMS"
;;       (is (= {:uuid "uuid-c-1"}
;;              (kms-util/concept->elastic-doc context {:concept "C-1"}))))

;;     (testing "Concept not found in KMS"
;;       (is (nil?
;;            (kms-util/concept->elastic-doc context {:concept "NOT-IN-KMS"}))))))

;; (deftest iso-topic-category->elastic-doc-test
;;   (let [context create-context]
;;     (testing "ISO topic category found in KMS"
;;       (is (= {:uuid "uuid-iso-1"}
;;              (kms-util/iso-topic-category->elastic-doc context "ISO-1"))))

;;     (testing "ISO topic category not found in KMS"
;;       (is (nil?
;;            (kms-util/iso-topic-category->elastic-doc context "NOT-IN-KMS"))))))

;; (deftest related-url->elastic-doc-test
;;   (let [context create-context]
;;     (testing "Related url found in KMS"
;;       (is (= {:uuid "uuid-ru-1"}
;;              (kms-util/related-url->elastic-doc context {:url-content-type "Type" :type "Type1" :subtype "SubType1"}))))

;;     (testing "Related url not found in KMS"
;;       (is (nil?
;;            (kms-util/related-url->elastic-doc context {:url-content-type "Type" :type "NOT-IN-KMS"}))))))

;; (deftest granule-data-format->elastic-doc-test
;;   (let [context create-context]
;;     (testing "Granule data format found in KMS"
;;       (is (= {:uuid "uuid-gdf-1"}
;;              (kms-util/granule-data-format->elastic-doc context {:short-name "GDF-1"}))))

;;     (testing "Granule data format not found in KMS"
;;       (is (nil?
;;            (kms-util/granule-data-format->elastic-doc context {:short-name "NOT-IN-KMS"}))))))

;; (deftest mime-type->elastic-doc-test
;;   (let [context create-context]
;;     (testing "Mime type found in KMS"
;;       (is (= {:uuid "uuid-mime-1"}
;;              (kms-util/mime-type->elastic-doc context "MIME-1"))))

;;     (testing "Mime type not found in KMS"
;;       (is (nil?
;;            (kms-util/mime-type->elastic-doc context "NOT-IN-KMS"))))))
