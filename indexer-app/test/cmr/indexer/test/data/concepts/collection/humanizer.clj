(ns cmr.indexer.test.data.concepts.collection.humanizer
  "Tests for humanized keywords"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.common-app.services.kms-lookup :as kms-lookup]
   [cmr.common-app.test.sample-humanizer :as sh]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as imc]
   [cmr.indexer.data.concepts.collection.humanizer :as humanizer]
   [cmr.indexer.data.humanizer-fetcher :as hf]
   [cmr.redis-utils.test.test-util :as redis-embedded-fixture]))

(def ^:private sample-keyword-map
   {:platforms [{:basis "Space-based Platforms", :category "Earth Observation Satellites" :sub-category nil :short-name "Terra" :long-name "Earth Observing System, Terra (AM-1)" :uuid "80eca755-c564-4616-b910-a4c4387b7c54"}
                {:basis "Space-based Platforms", :category "Space Stations/Crewed Spacecraft", :sub-category "Space Station", :short-name "ISS", :long-name "International Space Station", :uuid "93c5d18c-be62-46c4-9545-42f73a854d85"}]})

(def create-context
  (let [humanizer-cache (imc/create-in-memory-cache)]
    {:system {:caches {hf/humanizer-cache-key humanizer-cache
                       kms-lookup/kms-short-name-cache-keys (kms-lookup/create-kms-short-name-cache)
                       kms-lookup/kms-umm-c-cache-key (kms-lookup/create-kms-umm-c-cache)
                       kms-lookup/kms-location-cache-key (kms-lookup/create-kms-location-cache)
                       kms-lookup/kms-measurement-cache-key (kms-lookup/create-kms-measurement-cache)}}}))

(defn redis-cache-fixture
  [f]
  (let [context create-context
        humanizer-cache (cache/context->cache context hf/humanizer-cache-key)]
    (kms-lookup/create-kms-index context sample-keyword-map)
    (cache/set-value humanizer-cache hf/humanizer-cache-key sh/sample-humanizers)
    (f)))

(use-fixtures :once (join-fixtures [redis-embedded-fixture/embedded-redis-server-fixture
                                    redis-cache-fixture]))

(deftest humanized-collection-platforms2-nested-test
  (let [context create-context
        collection {:provider-id "PROV1"
                    :concept-id "C1200000004-PROV1"
                    :ShortName "short-name"
                    :Version "V15"
                    :ScienceKeywords [{:Category "Bioosphere"
                                        :Topic "Topic1"
                                        :Term "Term1"}
                                      {:Category "Bio sphere"
                                        :Topic "Topic2"
                                        :Term "Term2"}]
                    :Platforms [{:ShortName "AM-1"}]}]
    (are3 [short-name actual-basis actual-category actual-short-name]
      (let [collection (assoc collection :Platforms [{:ShortName short-name}])
            humanized-collection (humanizer/collection-humanizers-elastic context collection)
            platform (first (:platforms2-humanized humanized-collection))]
        (is (= actual-basis (:basis platform)))
        (is (= actual-category (:category platform)))
        (is (= actual-short-name (:short-name platform))))

      "Test humanizing AM-1 and finding the value in KMS"
      "AM-1" "Space-based Platforms" "Earth Observation Satellites" "Terra"

      "Test no humanizing and finding the value in KMS"
      "Terra" "Space-based Platforms" "Earth Observation Satellites" "Terra"

      "Test no humanizing but finding the term case insensitive in KMS."
      "TERRA" "Space-based Platforms" "Earth Observation Satellites" "Terra"

      "Test humanizing and not finding the term in KMS, but the original is in KMS."
      "ISS" "Space-based Platforms" "Space Stations/Crewed Spacecraft" "International Space Station"

      "Test no humanizing and not finding in KMS."
      "AM-2" nil nil "AM-2")))
