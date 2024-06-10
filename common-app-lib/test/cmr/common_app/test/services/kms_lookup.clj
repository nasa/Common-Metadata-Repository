(ns cmr.common-app.test.services.kms-lookup
  "Unit tests for KMS lookup namespace."
  (:require
   [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
   [cmr.common-app.services.kms-lookup :as kms-lookup]
   [cmr.common.util :refer [are3]]
   [cmr.redis-utils.test.test-util :as redis-embedded-fixture]))

(def sample-map
  "Sample KMS map to use for all of the tests"
  {:providers [{:level-0 "ACADEMIC" :level-1 "OR-STATE/EOARC" :short-name "PROV1"
                :long-name "Eastern Oregon Agriculture Research Center, Oregon State University"
                :uuid "prov1-uuid"}]
   :platforms [{:short-name "PLAT1" :long-name "Platform 1" :category "Aircraft" :other-random-key 7 :uuid "plat1-uuid"}]
   :instruments [{:short-name "INST1" :long-name "Instrument 1" :uuid "inst1-uuid"}]
   :projects [{:short-name "PROJ1" :long-name "Project 1" :uuid "proj1-uuid"}]
   :spatial-keywords [{:category "CONTINENT" :type "AFRICA" :subregion-1 "CENTRAL AFRICA"
                       :subregion-2 "CHAD" :subregion-3 "AOUZOU" :uuid "location1-uuid"}
                      {:category "CONTINENT" :type "AFRICA" :uuid "location2-uuid"}
                      {:category "CONTINENT" :type "AFRICA" :subregion-1 "CENTRAL AFRICA"
                       :uuid "location3-uuid"}
                      {:category "CONTINENT" :type "EUROPE" :subregion-1 "BLACK SEA"
                       :uuid "location4-uuid"}
                      {:category "SPACE" :uuid "location5-uuid"}
                      {:category "CONTINENT" :type "UNITED STATES" :subregion-1 "GEORGIA"
                       :uuid "location6-uuid"}]
   :related-urls [{:url-content-type "DistributionURL"
                   :type "GOTO WEB TOOL"
                   :subtype "HITIDE"
                   :uuid "related1-uuid-hitide"}
                  {:url-content-type "VisualizationURL"
                   :type "GET RELATED VISUALIZATION"
                   :subtype "MAP"
                   :uuid "related2-uuid-map"}]
   :iso-topic-categories [{:iso-topic-category "BIOTA" :uuid "itc1-uuid"} {:iso-topic-category "CLIMATOLOGY/METEOROLOGY/ATMOSPHERE" :uuid "itc2-uuid"}]
   :concepts [{:short-name "GOSIC/GTOS" :uuid "dn1-uuid"} {:short-name "GOMMP" :uuid "dn2-uuid"}]
   :science-keywords [{:category "EARTH SCIENCE" :topic "TOPIC1" :term "TERM1"
                       :variable-level-1 "VL1" :variable-level-2 "VL2"
                       :variable-level-3 "VL3" :uuid "sk1-uuid"}]
   :granule-data-format [{:short-name "ASCII" :long-name "American Standard Code for Information Interchange" :uuid "gdf1-uuid"}
                         {:short-name "CSV" :long-name "Comma-Separated Values File" :uuid "465809cc-e76c-4630-8594-bb8bd7a1a380"}]
   :measurement-name [{:context-medium "atmosphere-at_cloud_base" :object "air" :quantity "pressure" :uuid "d9e1cc0b-f124-4058-a022-ae234f831076"}
                      {:context-medium "atmosphere-at_cloud_base" :object "air" :uuid "c6347897-a64d-4393-9935-dbce464eab09"}]
   :mime-type [{:mime-type "application/gml+xml" :uuid "40bdf6e5-780c-43e2-ab8e-e5dfae4bd779"}
               {:mime-type "application/gzip" :uuid "a8ee535a-8bc8-46fd-8b97-917bd7ea7666"}]
   :temporal-keywords [{:temporal-resolution-range "1 minute - < 1 hour" :uuid "bca20202-2b06-4657-a425-5b0e416bce0c"}
                       {:temporal-resolution-range "1 second - < 1 minute" :uuid "48ff676f-836c-4cff-bc88-4c4cc06b2e1b"}]
   :processing-levels [{:processing-level "0" :uuid "b58fa5bb-dfc7-4d6c-b240-161fe44c15d3"}
                       {:processing-level "1A" :uuid "87fdeb97-2d3e-4812-8540-b88f425d920c"}]})

(def create-context
  "Creates a testing concept with the KMS caches."
  {:system {:caches {kms-lookup/kms-short-name-cache-key (kms-lookup/create-kms-short-name-cache)
                     kms-lookup/kms-umm-c-cache-key (kms-lookup/create-kms-umm-c-cache)
                     kms-lookup/kms-location-cache-key (kms-lookup/create-kms-location-cache)
                     kms-lookup/kms-measurement-cache-key (kms-lookup/create-kms-measurement-cache)}}})

(defn redis-cache-fixture
  "Sets up the redis cache fixture to load data into the caches for testing."
  [f]
  (kms-lookup/create-kms-index create-context sample-map)
  (f))

(use-fixtures :once (join-fixtures [redis-embedded-fixture/embedded-redis-server-fixture
                                    redis-cache-fixture]))

(deftest lookup-by-location-string-test
  (testing "Full location hierarchy is returned"
    (is (= {:category "CONTINENT" :type "AFRICA" :subregion-1 "CENTRAL AFRICA"
            :subregion-2 "CHAD" :subregion-3 "AOUZOU" :uuid "location1-uuid"}
           (kms-lookup/lookup-by-location-string create-context "AOUZOU"))))

  (are3
   [location-string expected-uuid]
   (is (= expected-uuid (:uuid (kms-lookup/lookup-by-location-string create-context location-string))))

   "Fewest number of hierarchical keys are returned"
   "CENTRAL AFRICA" "location3-uuid"

   "Lookups are case insensitive"
   "cENtRAl aFRiCA" "location3-uuid"

   "Not found returns nil"
   "cha" nil

   "Duplicate maps are used to override default location choice - SPACE"
   "SPACE" "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"

   "Duplicate maps are used to override default location choice - BLACK SEA"
   "BLACK SEA" "afbc0a01-742e-49da-939e-3eaa3cf431b0"

   "Duplicate maps are used to override default location choice - GEORGIA"
   "GEORGIA" "d79e134c-a4d0-44f2-9706-cad2b59de992"))

(deftest lookup-by-umm-c-keyword-test
  (testing "Full keyword map is returned by umm-c lookup"
    (is (= {:short-name "PLAT1" :long-name "Platform 1" :category "Aircraft" :other-random-key 7 :uuid "plat1-uuid"}
           (kms-lookup/lookup-by-umm-c-keyword create-context
                                               :platforms
                                               {:short-name "Plat1" :long-name "Platform 1" :type "Aircraft"}))))
  ;; Test each of the different keywords with fields present and missing
  (are3
   [keyword-scheme umm-c-keyword expected-uuid]
   (is (= expected-uuid
          (:uuid (kms-lookup/lookup-by-umm-c-keyword create-context keyword-scheme umm-c-keyword))))
   "Lookup Platform"
   :platforms {:short-name "PLAT1" :long-name "Platform 1" :type "Aircraft"} "plat1-uuid"

   "Lookups are case insensitive"
   :platforms {:short-name "plAt1" :long-name "PLATFORm 1" :type "AirCrAFt"} "plat1-uuid"

   "Lookup without all keys used in the lookup returns nil"
   :platforms {:short-name "PLAT1"} nil

   "Lookup platform with extra keys finds the platform"
   :platforms {:a-key "a" :b-key "b" :short-name "PLAT1" :long-name "Platform 1" :type "Aircraft"} "plat1-uuid"

   "Lookup Instrument"
   :instruments {:short-name "inst1" :long-name "Instrument 1"} "inst1-uuid"

   "Lookup science keyword"
   :science-keywords {:category "EARTH SCIENCE" :topic "TOPIC1" :term "TERM1"
                      :variable-level-1 "VL1" :variable-level-2 "VL2"
                      :variable-level-3 "VL3" :detailed-variable "detailed"} "sk1-uuid"

   "Lookup project"
   :projects {:short-name "proj1" :long-name "Project 1"} "proj1-uuid"

   "Lookup concepts"
   :concepts {:short-name "GOSIC/GTOS"} "dn1-uuid"

   "Lookup iso topic category"
   :iso-topic-categories {:iso-topic-category "BIOTA"} "itc1-uuid"

   "Lookup first related-url"
   :related-urls
   {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "HITIDE"}
   "related1-uuid-hitide"

   "Lookup second related-url"
   :related-urls
   {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "MAP"}
   "related2-uuid-map"

   "Lookup related-url and not find one when the Content Type is wrong"
   :related-urls
   {:URLContentType "Wrong" :Type "GOTO WEB TOOL" :Subtype "HITIDE"}
   nil

   "Lookup related-url and not find one when the Type is wrong"
   :related-urls
   {:URLContentType "DistributionURL" :Type "Wrong" :Subtype "HITIDE"}
   nil

   "Lookup related-url and not find one when the Subtype is wrong"
   :related-urls
   {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "Wrong"}
   nil

   "Lookup spatial keyword"
   :spatial-keywords {:category "CONTINENT" :type "AFRICA" :subregion-1 "CENTRAL AFRICA"
                      :subregion-2 "CHAD" :subregion-3 "AOUZOU"} "location1-uuid"

   "Lookup spatial keyword without all matching fields present"
   :spatial-keywords {:category "CONTINENT" :type "AFRICA"} "location2-uuid"

   "Lookup data format"
   :granule-data-format "ASCII" "gdf1-uuid"

   "Lookup data format 2"
   :granule-data-format
   {:FormatType "Native"
    :AverageFileSize 50
    :Fees "None currently"
    :Format "ASCII"
    :AverageFileSizeUnit "MB"}
   "gdf1-uuid"

   "Lookup mime-type"
   :mime-type {:mime-type "application/gzip"} "a8ee535a-8bc8-46fd-8b97-917bd7ea7666"

   "Lookup processing levels"
   :processing-levels {:processing-level "1A"} "87fdeb97-2d3e-4812-8540-b88f425d920c"

   "Lookup temporal keywords"
   :temporal-keywords {:temporal-resolution-range "1 minute - < 1 hour"} "bca20202-2b06-4657-a425-5b0e416bce0c")

  ;; CMR-4400
  (testing "Platform Shortname exists but Longname is nil"
    (is (= {:short-name "PLAT1" :long-name "Platform 1" :category "Aircraft" :other-random-key 7 :uuid "plat1-uuid"}
           (kms-lookup/lookup-by-umm-c-keyword create-context :platforms
                                               {:short-name "Plat1" :long-name nil :type "Aircraft"})))))

(deftest lookup-by-short-name-test
  (testing "Full keyword map is returned by short-name lookup"
    (is (= {:short-name "PLAT1" :long-name "Platform 1" :category "Aircraft" :other-random-key 7 :uuid "plat1-uuid"}
           (kms-lookup/lookup-by-short-name create-context :platforms "PLAT1"))))
  ;; Test each of the different keywords with fields present and missing
  (are3
   [keyword-scheme short-name expected-uuid]
   (is (= expected-uuid
          (:uuid (kms-lookup/lookup-by-short-name create-context keyword-scheme short-name))))

   "Lookup Platform"
   :platforms "PLAT1" "plat1-uuid"

   "Lookups are case insensitive"
   :platforms "plAt1" "plat1-uuid"

   "Lookup Provider"
   :providers "PROV1" "prov1-uuid"

   "Lookup Instrument"
   :instruments "INST1" "inst1-uuid"

   "Lookup returns nil for no match in another keyword scheme"
   :platforms "INST1" nil))

(deftest lookup-by-measurement-name-valid-test
  (are3
   [expected input]
   (is (= expected (kms-lookup/lookup-by-measurement create-context input)))

   "Checking valid measurement names without quantities"
   nil
   {:MeasurementContextMedium "atmosphere-at_cloud_base" :MeasurementObject "air"}

   "Checking valid measurement names with quantities"
   nil
   {:MeasurementContextMedium "atmosphere-at_cloud_base", :MeasurementContextMediumURI nil, :MeasurementObject "air", :MeasurementObjectURI nil, :MeasurementQuantities [{:Value "pressure", :MeasurementQuantityURI nil}]}

   "Checking invalid measurement names without quantities. Getting back the error map."
   '({:context-medium "atmosphere-at_cloud_invalid" :object "air"})
   {:MeasurementContextMedium "atmosphere-at_cloud_invalid" :MeasurementObject "air"}

   "Checking invalid measurement names with quantities. Getting back the error map."
   '({:context-medium "atmosphere-at_cloud_base" :object "air_bad" :quantity "pressure"})
   {:MeasurementContextMedium "atmosphere-at_cloud_base", :MeasurementContextMediumURI nil, :MeasurementObject "air_bad", :MeasurementObjectURI nil, :MeasurementQuantities [{:Value "pressure", :MeasurementQuantityURI nil}]}))
