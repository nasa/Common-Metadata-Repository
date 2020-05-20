(ns cmr.system-int-test.search.autocomplete.suggestion-reindex-test
  "This tests re-indexes autocomplete suggestions."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-spec]
   [cmr.system-int-test.data2.umm-spec-common :as umm-spec-common]
   [cmr.system-int-test.search.facets.facets-util :as fu]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.humanizer-util :as hu]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(defn- scores-descending?
  [results]
  (->> results
       (map :score)
       (apply >=)))

(defn compare-autocomplete-results
  "Compare expected to actual response for the following:
  - Items are ordered by score in descending order
  - Ensure that the other fields match"
  [actual expected]
  ;; check score returns
  (when (seq actual)
    (is (scores-descending? actual) actual))

  ;; compare values
  (let [expected (map #(dissoc % :score) expected)
        actual (map #(dissoc % :score) actual)]
    (is (= expected actual))))

(def sk1 (umm-spec-common/science-keyword {:Category "Earth science"
                                           :Topic "Topic1"
                                           :Term "Term1"
                                           :VariableLevel1 "Level1-1"
                                           :VariableLevel2 "Level1-2"
                                           :VariableLevel3 "Level1-3"
                                           :DetailedVariable "Detail1"}))

(def sk2 (umm-spec-common/science-keyword {:Category "EARTH SCIENCE"
                                           :Topic "Popular"
                                           :Term "Extreme"
                                           :VariableLevel1 "Level2-1"
                                           :VariableLevel2 "Level2-2"
                                           :VariableLevel3 "Level2-3"
                                           :DetailedVariable "UNIVERSAL"}))

(def sk3 (umm-spec-common/science-keyword {:Category "EARTH SCIENCE"
                                           :Topic "Popular"
                                           :Term "UNIVERSAL"}))

(def sk4 (umm-spec-common/science-keyword {:Category "EARTH SCIENCE"
                                           :Topic "Popular"
                                           :Term "Alpha"}))

(def sk5 (umm-spec-common/science-keyword {:Category "EARTH SCIENCE"
                                           :Topic "Popular"
                                           :Term "Beta"}))

(def sk6 (umm-spec-common/science-keyword {:Category "EARTH SCIENCE"
                                           :Topic "Popular"
                                           :Term "Omega"}))

(def sk7 (umm-spec-common/science-keyword {:Category "missing"
                                           :Topic "value"
                                           :Term "Not Provided"}))

(def sk8 (umm-spec-common/science-keyword {:Category "missing"
                                           :Topic "value"
                                           :Term "Not Applicable"}))

(def sk9 (umm-spec-common/science-keyword {:Category "missing"
                                           :Topic "value"
                                           :Term "None"}))

(def sk10 (umm-spec-common/science-keyword {:Category "missing"
                                            :Topic "value"
                                            :Term "na"}))

(def sk11 (umm-spec-common/science-keyword {:Category "EARTH SCIENCE"
                                           :Topic "BIOSPHERE"
                                           :Term "Nothofagus"}))

(def gdf1 {:FileDistributionInformation
           [{:FormatType "Binary"
             :AverageFileSize 50
             :AverageFileSizeUnit "MB"
             :Fees "None currently"
             :Format "NetCDF-3"}]})

(defn autocomplete-reindex-fixture
  [f]
  (let [coll1 (d/ingest "PROV1"
                        (dc/collection
                          {:DataCenters [(data-umm-spec/data-center {:Roles ["ARCHIVER"] :ShortName "DOI/USGS/CMG/WHSC"})]
                           :ArchiveAndDistributionInformation gdf1
                           :SpatialKeywords ["DC" "Miami"]
                           :ProcessingLevelId (:ProcessingLevelId (fu/processing-level-id "PL1"))
                           :Projects [(:Projects (fu/projects "proj1" "PROJ2"))]
                           :Platforms [(:Platforms (fu/platforms fu/FROM_KMS 2 2 1))]
                           :ScienceKeywords [(:ScienceKeywords (fu/science-keywords sk1 sk2))]}))
        coll2 (fu/make-coll 2 "PROV1"
                            (fu/science-keywords sk1 sk3)
                            (fu/projects "proj1" "PROJ2")
                            (fu/platforms fu/FROM_KMS 2 2 1)
                            (fu/processing-level-id "PL1")
                            {:DataCenters [(data-umm-spec/data-center {:Roles ["ARCHIVER"] :ShortName "DOI/USGS/CMG/WHSC"})]
                             :ScienceKeywords [(:ScienceKeywords (fu/science-keywords sk1 sk2))]})
        coll3 (d/ingest-concept-with-metadata-file "CMR-6287/C1000000029-EDF_OPS.xml"
                                                   {:provider-id "PROV1"
                                                    :concept-type :collection
                                                    :format-key :echo10})
        coll4 (fu/make-coll 1 "PROV1" (fu/science-keywords sk1 sk2 sk3 sk4 sk5 sk6 sk7 sk8 sk9 sk10 sk11))]

    (index/wait-until-indexed)
    (index/reindex-suggestions)
    (index/wait-until-indexed)

    (f)))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       hu/grant-all-humanizers-fixture
                       hu/save-sample-humanizers-fixture
                       autocomplete-reindex-fixture]))

(deftest reindex-suggestions-test
  (testing "Ensure that response is in proper format and results are correct"
    (compare-autocomplete-results
      (get-in (search/get-autocomplete-json "q=level2") [:feed :entry])
      [{:type "organization" :value "Langley DAAC User Services" :fields "Langley DAAC User Services"}
       {:type "instrument" :value "lVIs" :fields "lVIs"}]))

  (testing "Ensure science keywords are being indexed properly"
    (are3
      [query expected]
      (let [actual (get-in (search/get-autocomplete-json query) [:feed :entry])]
        (compare-autocomplete-results actual expected))

      "shorter match"
      "q=solar"
      [{:type "science_keywords" :value "Solar Irradiance" :fields "Sun-Earth Interactions:Solar Activity:Solar Irradiance"}
       {:type "science_keywords" :value "Solar Irradiance" :fields "Atmosphere:Atmospheric Radiation:Solar Irradiance"}
       {:type "organization" :value "ACRIM SCF" :fields "ACRIM SCF"}
       {:type "organization" :value "Langley DAAC User Services" :fields "Langley DAAC User Services"}]

      "more complete match"
      "q=solar irradiation"
      [{:type "science_keywords" :value "Solar Irradiance" :fields "Sun-Earth Interactions:Solar Activity:Solar Irradiance"}
       {:type "science_keywords" :value "Solar Irradiance" :fields "Atmosphere:Atmospheric Radiation:Solar Irradiance"}
       {:type "organization" :value "ACRIM SCF" :fields "ACRIM SCF"}
       {:type "organization" :value "Langley DAAC User Services" :fields "Langley DAAC User Services"}]))

  (testing "Anti-value filtering"
    (are3
      [query expected]
      (let [results (get-in (search/get-autocomplete-json (str "q=" query)) [:feed :entry])]
        (compare-autocomplete-results results expected))
      
      "excludes 'NA'"
      "na" [{:value "Nothofagus" :type "science_keywords" :fields "Biosphere:Nothofagus"}]
      
      "excludes 'None'"
      "none" [{:value "Nothofagus" :type "science_keywords" :fields "Biosphere:Nothofagus"}]
      
      "excludes 'Not Applicable'"
      "not applicable" [{:type "science_keywords" :value "Nothofagus" :fields "Biosphere:Nothofagus"}
                        {:type "instrument" :value "ATM" :fields "ATM"}
                        {:type "platform" :value "ACRIMSAT" :fields "ACRIMSAT"}
                        {:type "instrument" :value "ACRIM" :fields "ACRIM"}
                        {:type "science_keywords" :value "Alpha" :fields "Popular:Alpha"}
                        {:type "project" :value "ACRIM" :fields "ACRIM"}
                        {:type "organization" :value "ACRIM SCF" :fields "ACRIM SCF"}]
      
      "excludes 'Not Provided'"
      "not provided" [{:type "science_keywords" :value "Nothofagus" :fields "Biosphere:Nothofagus"}
                      {:type "project" :value "proj1" :fields "proj1"}
                      {:type "project" :value "PROJ2" :fields "PROJ2"}
                      {:type "processing_level" :value "PL1" :fields "PL1"}]

      "does not filter 'not' prefixed values"
      "q=not" [{:value "Nothofagus" :type "science_keywords" :fields "Biosphere:Nothofagus"}])))
