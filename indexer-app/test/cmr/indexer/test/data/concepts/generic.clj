(ns cmr.indexer.test.data.concepts.generic
  "Functions for testing cmr.indexer.data.concepts.generic namespace"
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [cmr.common.util :as util :refer [are3]]))

(deftest test-for-field->index-complex-field
  (let [field->index-complex-field
        @#'cmr.indexer.data.concepts.generic/field->index-complex-field]
    (are3
     [expected settings data]
     (is (= expected (field->index-complex-field settings data)) "expected failed")

     "Base case"
     {:generation "SourceProjection=EPSG:4326, OutputFormat=PPNG",
      :generation-lowercase "sourceprojection=epsg:4326, outputformat=ppng"}
     {:Field ".Generation"
      :Name "Generation"
      :Configuration {:sub-fields ["SourceProjection", "OutputFormat"]}}
     {:Generation {:SourceProjection "EPSG:4326",
                   :SourceResolution "Native",
                   :SourceFormat "GeoTIFF",
                   :SourceColorModel "Indexed RGBA",
                   :SourceCoverage "Tiled",
                   :OutputProjection "EPSG:4326",
                   :OutputResolution "31.25m",
                   :OutputFormat "PPNG"}})))

(deftest test-for-field->index-simple-array-field
  (let [field->index-simple-array-field
        @#'cmr.indexer.data.concepts.generic/field->index-simple-array-field]
    (are3
     [expected settings data]
     (is (= expected (field->index-simple-array-field settings data)) "expected failed")

     ;; Tests
     "Primary test"
     {:concept-ids "Short-Two Short-One V-Two V-One"
      :concept-ids-lowercase "short-two short-one v-two v-one"}
     {:Field ".ConceptIds"
      :Name "Concept-Ids"
      :Configuration {:sub-fields ["Value", "ShortName"]}}
     {:ConceptIds [{:Value "V-One" :ShortName "Short-One" :ignore "not-interested"}
                   {:Value "V-Two" :ShortName "Short-Two" :ignore "something-else"}]})))

(deftest test-for-field->index-default-field
  (let [field->index-default-field @#'cmr.indexer.data.concepts.generic/field->index-default-field]
    (are3
     [expected settings data]
     (is (= expected (field->index-default-field settings data)) "expected test")

     ;; Tests
     "Base test"
     {:visualizationtype "Default", :visualizationtype-lowercase "default"}
     {:Field ".VisualizationType" :Name "VisualizationType"}
     {:VisualizationType "Default"})))

(deftest test-for-merge-or-concat
  (let [merge-or-concat @#'cmr.indexer.data.concepts.generic/merge-or-concat]
    (are3
     [expected existing new-value delim]
     (is (= expected (if (nil? delim)
                       (merge-or-concat existing new-value)
                       (merge-or-concat existing new-value delim)))
         "expected test")

     "Base case"
     "" nil "" ""

     "Expected use case"
     "existing value" "existing" "value" nil

     "Expected use case, multi words"
     "existing text new value" "existing text" "new value" nil

     "Have existing, but not new value"
     "existing" "existing" "" nil

     "Have new value but not existing"
     "new value" "" "new value" nil

     "Base case, with delim"
     "" "" "" " & "

     "Expected case with deliminator"
     "existing & value" "existing" "value" " & "

     "Expected case with deliminator and now existing value"
     "value" "" "value" " & ")))

;; *************************************************************************************************
;; A more complex test, pass a set of documents to the internal parsed-concept->elastic-doc
;; function and make sure the dynamicly generated elastic document looks like what is expected. This
;; is also the best way to see how the function actually works.

(def sample-parsed-metadata
  "This is a sample Visualization record to be used in the test. It normally is derived from the
   metadata field of the concept variable, but for this test the parsed metadata is used for
   readability and will latter be converted to a json string and stored in the concept data."
  {:Specification
   {:ProductIdentification
    {:InternalIdentifier "MODIS_Terra_CorrectedReflectance_TrueColor"
     :StandardOrNRTExternalIdentifier "MODIS_Terra_CorrectedReflectance_TrueColor"
     :BestAvailableExternalIdentifier "MODIS_Terra_CorrectedReflectance"
     :GIBSTitle "Corrected Reflectance (True Color)"
     :WorldviewTitle "Corrected Reflectance (True Color)"
     :WorldviewSubtitle "Terra / MODIS"}}
   :ConceptIds [{:Type "STD"
                 :Value "C1000000001-EARTHDATA",
                 :DataCenter "MODAPS",
                 :ShortName "MODIS_Terra_CorrectedReflectance",
                 :Title "MODIS/Terra Corrected Reflectance True Color",
                 :Version "6.1"}],
   :VisualizationType "tiles",
   :Title "FOO UPDATE MODIS Terra Corrected Reflectance (True Color)",
   :Identifier "MODIS_Terra_CorrectedReflectance_TrueColor",
   :SpatialExtent {:GranuleSpatialRepresentation "GEODETIC",
                   :HorizontalSpatialDomain
                   {:Geometry {:CoordinateSystem "GEODETIC",
                               :BoundingRectangles
                               [{:WestBoundingCoordinate -180.0
                                 :NorthBoundingCoordinate 90.0
                                 :EastBoundingCoordinate 180.0
                                 :SouthBoundingCoordinate -90.0}]}}}
   :ScienceKeywords [{:Category "EARTH SCIENCE",
                      :Topic "SPECTRAL/ENGINEERING",
                      :Term "VISIBLE WAVELENGTHS",
                      :VariableLevel1 "REFLECTANCE"}
                     {:Category "EARTH SCIENCE",
                      :Topic "ATMOSPHERIC OPTICS",
                      :Term "ATMOSPHERIC TRANSMITTANCE",
                      :VariableLevel1 "ATMOSPHERIC TRANSPARENCY"}],
   :Subtitle "Terra / MODIS",
   :Name "MODIS_Terra_Corrected_Reflectance_TrueColor",
   :Description (str "MODIS Terra Corrected Reflectance True Color imagery shows land surface, "
                     "ocean and atmospheric features by combining three different channels (bands) "
                     "of the sensor data. The image has been enhanced through processing, "
                     "including atmospheric correction for aerosols, to improve the visual "
                     "depiction of the land surface while maintaining realistic colors.")
   :Generation {:SourceProjection "EPSG:4326",
                :SourceResolution "Native",
                :SourceFormat "GeoTIFF",
                :SourceColorModel "Full-Color RGB",
                :SourceCoverage "Granule",
                :OutputProjection "EPSG:4326",
                :OutputResolution "250m", :OutputFormat "JPEG"},
   :TemporalExtents [{:RangeDateTimes [{:BeginningDateTime "2002-05-01T00:00:00Z",
                                        :EndingDateTime "2023-12-31T23:59:59Z"}],
                      :EndsAtPresentFlag true}],
   :MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/umm/visualization/v1.1.0",
                           :Name "Visualization",
                           :Version "1.1.0"}})

(def sample-concept
  "This is what a metadata document looks like when passed in with all the extra fields. Construct
   the metadata from the sample-parsed-metadata so as to make this def more readable and managable.
   Normally these values are generated the other way around by CMR."
  {:revision-id 7
   :deleted false
   :format "application/vnd.nasa.cmr.umm+json;version=1.1.0"
   :provider-id "TCHERRY_A"
   :tool-associations ()
   :service-associations ()
   :user-id "ECHO_SYS"
   :variable-associations ()
   :transaction-id "2000072810"
   :tag-associations ()
   :native-id "32582d46ab08014528ec97bd535abc40"
   :generic-associations ()
   :concept-id "VIS1200000446-TCHERRY_A"
   :created-at "2025-05-29T16:02:03.200Z"
   :metadata (json/encode sample-parsed-metadata)
   :revision-date "2025-06-02T20:25:33.260Z"
   :extra-fields {:document-name "MODIS_Terra_Corrected_Reflectance_TrueColor"
                  :schema "visualization"
                  :concept-type :visualization}})

(def sample-index-data
  "A visualization Generic configuration file"
  {:MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/generic/config/v0.0.1"
                           :Name "Generic-Config"
                           :Version "0.0.1"}
   :Generic {:Name "Visualization", :Version "1.1.0"}
   :SubConceptType "VIS"
   :IndexSetup {:index {:number_of_shards 3 :number_of_replicas 1 :refresh_interval "1s"}}
   :IndexConfiguration {:AllowAppending true
                        :AdditionalKeywords ["Identifier"]}
   :Indexes [{:Description "Identifier" :Field ".Identifier" :Name "Id" :Mapping "string"}
             {:Description "Identifier" :Field ".Identifier" :Name "Identifier" :Mapping "string"}
             {:Description "Schema Name as the Name field"
              :Field ".Name"
              :Name "Name"
              :Mapping "string"}
             {:Description "Schema Title as the Title field"
              :Field ".Title"
              :Name "Title"
              :Mapping "string"}
             {:Description "VisualizationType"
              :Field ".VisualizationType"
              :Name "Visualization-Type"
              :Mapping "string"}
             {:Description "Visualization Source ConceptIds"
              :Field ".ConceptIds"
              :Name "Concept-Ids"
              :Mapping "token"
              :Indexer "simple-array-field"
              :Configuration {:sub-fields ["Value" "ShortName"]}}
             {:Description "Visualization Source ConceptIds in keywords"
              :Field ".ConceptIds"
              :Name "keyword"
              :Mapping "token"
              :Indexer "simple-array-field"
              :Configuration {:sub-fields ["Value"]}}
             {:Description "Best Available External Identifier in keywords"
              :Field ".Specification.ProductIdentification.BestAvailableExternalIdentifier"
              :Name "keyword"
              :Mapping "token"}]})

(deftest test-for-parsed-concept->elastic-doc
  (let [parsed-concept->elastic-doc @#'cmr.indexer.data.concepts.generic/parsed-concept->elastic-doc
        index-data sample-index-data
        actual (parsed-concept->elastic-doc sample-concept
                                            sample-parsed-metadata
                                            ""
                                            "visualization" "1.1.0"
                                            index-data)
        expected-keyword (str "(bands) aerosols aerosols, and atmospheric bands been by c1000000001 "
                              "c1000000001-earthdata channels color colors colors. combining "
                              "corrected correctedreflectance correction data data. depiction "
                              "different earthdata enhanced features for has image imagery improve "
                              "including land maintaining modis modis_terra_correctedreflectance "
                              "modis_terra_correctedreflectance_truecolor ocean of processing "
                              "processing, realistic reflectance sensor shows surface surface, "
                              "terra the three through to true truecolor visual while")
        expected-concept-ids "modis_terra_correctedreflectance c1000000001-earthdata"]
    (is (= expected-keyword (:keyword actual)) "appended keyword test")
    (is (= expected-keyword (:keyword-lowercase actual)) "lowercase keyword test")
    (is (= expected-concept-ids (:concept-ids-lowercase actual)) "a simple-array-field test")))
