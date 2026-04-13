(ns cmr.indexer.test.data.concepts.collection.project-keyword
  "This namespace conducts unit tests on the project-keyword namespace."
  (:require
   [clojure.string :as string]
   [clojure.test :refer [deftest is join-fixtures use-fixtures testing]]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as imc]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.common-app.services.kms-lookup :as kms-lookup]
   [cmr.indexer.data.concepts.collection.project-keyword :as project-keyword]
   [cmr.redis-utils.test.test-util :as redis-embedded-fixture]))

(def ^:private sample-keyword-map
  {:projects [{:short-name "KMS-SHORT" :long-name "KMS-LONG" :uuid "kms-uuid-123"}]})

(def create-context
  {:system {:caches {kms-lookup/kms-short-name-cache-key (kms-lookup/create-kms-short-name-cache)
                     kms-lookup/kms-umm-c-cache-key      (kms-lookup/create-kms-umm-c-cache)
                     kms-lookup/kms-location-cache-key   (kms-lookup/create-kms-location-cache)
                     kms-lookup/kms-measurement-cache-key (kms-lookup/create-kms-measurement-cache)}}})
(defn redis-cache-fixture
  [f]
  (let [context create-context]
    (kms-lookup/create-kms-index context sample-keyword-map)
    (f)))

(use-fixtures :once (join-fixtures [redis-embedded-fixture/embedded-redis-server-fixture
                                    redis-cache-fixture]))

(deftest project-short-name->elastic-doc-test
  (let [context create-context]
    (testing "Project found in KMS"
      (is (= {:bucket nil,
              :bucket-lowercase nil,
              :long-name "KMS-LONG",
              :long-name-lowercase "kms-long",
              :short-name "KMS-SHORT",
              :short-name-lowercase "kms-short",
              :uuid "kms-uuid-123",
              :uuid-lowercase "kms-uuid-123"}
             (project-keyword/project-short-name->elastic-doc context "KMS-SHORT"))))

    (testing "Project not found in KMS"
      (is (= {:bucket nil,
              :bucket-lowercase nil,
              :long-name "Not Provided",
              :long-name-lowercase "not provided",
              :short-name "NOT-IN-KMS",
              :short-name-lowercase "not-in-kms",
              :uuid nil,
              :uuid-lowercase nil}
             (project-keyword/project-short-name->elastic-doc context "NOT-IN-KMS"))))))
