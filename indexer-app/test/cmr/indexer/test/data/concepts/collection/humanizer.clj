(ns cmr.indexer.test.data.concepts.collection.humanizer
  "Tests for humanized keywords"
  (:require
   [clojure.test :refer :all]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.common-app.services.kms-lookup :as kms-lookup]
   [cmr.common-app.test.sample-humanizer :as sh]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as imc]
   [cmr.indexer.data.concepts.collection.humanizer :as humanizer]
   [cmr.indexer.data.humanizer-fetcher :as hf]))

(def ^:private sample-keyword-map
   {:platforms [{:basis "Space-based Platforms", :category "Earth Observation Satellites" :sub-category nil :short-name "Terra" :long-name "Earth Observing System, Terra (AM-1)" :uuid "80eca755-c564-4616-b910-a4c4387b7c54"}]})

(def sample-kms-index
  "Sample KMS index to use for tests"
  (kms-lookup/create-kms-index sample-keyword-map))

(defn setup-context-for-test
  "Sets up a cache by taking values necessary for the cache and returns a map of context"
  []
  (let [kms-cache (imc/create-in-memory-cache)
        humanizer-cache (imc/create-in-memory-cache)]
    (cache/set-value kms-cache kf/kms-cache-key sample-kms-index)
    (cache/set-value humanizer-cache hf/humanizer-cache-key sh/sample-humanizers)
    {:system {:caches {kf/kms-cache-key kms-cache
                       hf/humanizer-cache-key humanizer-cache}}}))

(deftest humanized-collection-platforms2-nested-test
  (let [context (setup-context-for-test)
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
                    :Platforms [{:ShortName "AM-1"}]}
        humanized-collection (humanizer/collection-humanizers-elastic
                               context collection)
        platform (first (:platforms2-humanized humanized-collection))]
    (is (= "Space-based Platforms" (:basis platform)))
    (is (= "Earth Observation Satellites" (:category platform)))
    (is (= "Terra" (:short-name platform)))))
