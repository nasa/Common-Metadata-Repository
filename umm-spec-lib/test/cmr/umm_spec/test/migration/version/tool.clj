(ns cmr.umm-spec.test.migration.version.tool
  (:require
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [cmr.common.mime-types :as mt]
   [cmr.common.test.test-check-ext :as ext :refer [defspec]]
   [cmr.umm-spec.migration.version.core :as vm]
   [cmr.umm-spec.migration.version.tool :as tool]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.test.umm-generators :as umm-gen]
   [cmr.umm-spec.umm-spec-core :as core]
   [cmr.umm-spec.versioning :as v]
   [com.gfredericks.test.chuck.clojure-test :refer [for-all]]))

(def tool-concept-1-0
  {:Name "USGS_TOOLS_LATLONG"
   :LongName "WRS-2 Path/Row to Latitude/Longitude Converter"
   :Type "Downloadable Tool"
   :Version "1.0"
   :Description "The USGS WRS-2 Path/Row to Latitude/Longitude Converter allows users to enter any Landsat path and row to get the nearest scene center latitude and longitude coordinates."
   :URL {:URLContentType "DistributionURL"
         :Type "DOWNLOAD SOFTWARE"
         :Description "Access the WRS-2 Path/Row to Latitude/Longitude Converter."
         :URLValue "http://www.scp.byu.edu/software/slice_response/Xshape_temp.html"}
   :ToolKeywords [{:ToolCategory "EARTH SCIENCE SERVICES"
                   :ToolTopic "DATA MANAGEMENT/DATA HANDLING"
                   :ToolTerm "DATA INTEROPERABILITY"
                   :ToolSpecificTerm "DATA REFORMATTING"}]
   :Organizations [{:Roles ["SERVICE PROVIDER"]
                    :ShortName "USGS/EROS"
                    :LongName "US GEOLOGICAL SURVEY EARTH RESOURCE OBSERVATION AND SCIENCE (EROS) LANDSAT CUSTOMER SERVICES"
                    :URLValue "http://www.usgs.gov"}]
   :SearchAction {:SearchActionElement "smart handoff search action"}
   :MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/umm/tool/v1.0"
                           :Name "UMM-T"
                           :Version "1.0"}})

(def tool-concept-1-1
  {:Name "USGS_TOOLS_LATLONG"
   :LongName "WRS-2 Path/Row to Latitude/Longitude Converter"
   :Type "Downloadable Tool"
   :Version "1.0"
   :Description "The USGS WRS-2 Path/Row to Latitude/Longitude Converter allows users to enter any Landsat path and row to get the nearest scene center latitude and longitude coordinates."
   :URL {:URLContentType "DistributionURL"
         :Type "DOWNLOAD SOFTWARE"
         :Description "Access the WRS-2 Path/Row to Latitude/Longitude Converter."
         :URLValue "http://www.scp.byu.edu/software/slice_response/Xshape_temp.html"}
   :ToolKeywords [{:ToolCategory "EARTH SCIENCE SERVICES"
                   :ToolTopic "DATA MANAGEMENT/DATA HANDLING"
                   :ToolTerm "DATA INTEROPERABILITY"
                   :ToolSpecificTerm "DATA REFORMATTING"}]
   :Organizations [{:Roles ["SERVICE PROVIDER"]
                    :ShortName "USGS/EROS"
                    :LongName "US GEOLOGICAL SURVEY EARTH RESOURCE OBSERVATION AND SCIENCE (EROS) LANDSAT CUSTOMER SERVICES"
                    :URLValue "http://www.usgs.gov"}]
   :PotentialAction {:Type "SearchAction"
                     :Target {:Type "EntryPoint",
                              :UrlTemplate "https://podaac-tools.jpl.nasa.gov/soto/#b=BlueMarble_ShadedRelief_Bathymetry&l={layers}&ve={bbox}&d={date}"
                              :HttpMethod [ "GET" ] }
                     :QueryInput [{:ValueName "layers"
                                   :Description "A comma-separated list of visualization layer ids, as defined by GIBS. These layers will be portrayed on the web application"
                                   :ValueRequired true
                                   :ValueType "https://wiki.earthdata.nasa.gov/display/GIBS/GIBS+API+for+Developers#GIBSAPIforDevelopers-LayerNaming"}
                                  {:ValueName "date"
                                   :ValueType "https://schema.org/startDate"}
                                  {:ValueName "bbox"
                                   :ValueType "https://schema.org/box"}]}
   :MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/umm/tool/v1.1"
                           :Name "UMM-T"
                           :Version "1.1"}})

;; when migrate from 1.1 to 1.1.1 RelatedURL-1_1-1 is converted to RelatedURLs-1_1_1-1
(def RelatedURLs-1_1_1-1
  [{:URLContentType "CollectionURL"
    :Type "DATA SET LANDING PAGE"}
   {:URLContentType "CollectionURL"
    :Type "EXTENDED METADATA"}
   {:URLContentType "CollectionURL"
    :Type "PROFESSIONAL HOME PAGE"}
   {:URLContentType "CollectionURL"
    :Type "PROJECT HOME PAGE"}
   {:URLContentType "PublicationURL"
    :Type "VIEW RELATED INFORMATION"}
   {:URLContentType "PublicationURL"
    :Type "VIEW RELATED INFORMATION"}
   {:URLContentType "PublicationURL"
    :Type "VIEW RELATED INFORMATION"}
   {:URLContentType "PublicationURL"
    :Type "VIEW RELATED INFORMATION"}
   {:URLContentType "PublicationURL"
    :Type "VIEW RELATED INFORMATION"}
   {:URLContentType "VisualizationURL"
    :Type "GET RELATED VISUALIZATION"}
   {:URLContentType "PublicationURL"
    :Type "VIEW RELATED INFORMATION"}
   {:URLContentType "VisualizationURL"
    :Type "GET RELATED VISUALIZATION"
    :SubType "MAP"}])

(def RelatedURLs-1_1-1
  [{:URLContentType "CollectionURL"
    :Type "DATA SET LANDING PAGE"
    :Subtype "any valid 1.1 subtype"}
   {:URLContentType "CollectionURL"
    :Type "EXTENDED METADATA"
    :Subtype "any valid 1.1 subtype"}
   {:URLContentType "CollectionURL"
    :Type "PROFESSIONAL HOME PAGE"
    :Subtype "any valid 1.1 subtype"}
   {:URLContentType "CollectionURL"
    :Type "PROJECT HOME PAGE"
    :Subtype "any valid 1.1 subtype"}
   {:URLContentType "CollectionURL"
    :Type "VIEW RELATED INFORMATION"
    :Subtype "any valid 1.1 subtype"}
   {:URLContentType "CollectionURL"
    :Type "GET RELATED VISUALIZATION"
    :Subtype "any valid 1.1 subtype"}
   {:URLContentType "CollectionURL"
    :Type "BROWSE"
    :Subtype "any valid 1.1 subtype"}
   {:URLContentType "PublicationURL"
    :Type "VIEW RELATED INFORMATION"
    :Subtype "GIOVANNI"}
   {:URLContentType "PublicationURL"
    :Type "other valid type"
    :Subtype "any valid 1.1 subtype"}
   {:URLContentType "VisualizationURL"
    :Type "GET RELATED VISUALIZATION"
    :Subtype "other subtype"}
   {:URLContentType "VisualizationURL"
    :Type "other valid type"
    :Subtype "any valid 1.1 subtype"}
   {:URLContentType "VisualizationURL"
    :Type "GET RELATED VISUALIZATION"
    :SubType "MAP"}])

;; when migrate from 1.1.1 to 1.1 RelatedURLs-1_1_1-2 is converted to RelatedURLs-1_1-2
(def RelatedURLs-1_1_1-2
  [{:URLContentType "CollectionURL"
    :Type "EXTENDED METADATA"
    :Subtype "DMR++"}
   {:URLContentType "PublicationURL"
    :Type "VIEW RELATED INFORMATION"
    :Subtype "DATA PRODUCT SPECIFICATION"}
   {:URLContentType "VisualizationURL"
    :Type "GET RELATED VISUALIZATION"
    :Subtype "SOTO"}
   {:URLContentType "VisualizationURL"
    :Type "Color Map"
    :Subtype "any valid 1.1.1 Subtype"}
   {:URLContentType "NotPublicationCollectionVisualizationURL"
    :Type "any valid 1.1.1 Type"
    :Subtype "any valid 1.1.1 Subtype"}])

(def RelatedURLs-1_1-2
  [{:URLContentType "CollectionURL"
    :Type "EXTENDED METADATA"}
   {:URLContentType "PublicationURL"
    :Type "VIEW RELATED INFORMATION"}
   {:URLContentType "VisualizationURL"
    :Type "GET RELATED VISUALIZATION"}
   {:URLContentType "VisualizationURL"
    :Type "GET RELATED VISUALIZATION"}
   {:URLContentType "PublicationURL"
    :Type "VIEW RELATED INFORMATION"}])

;; when migrate from 1.1 to 1.1.1,
;; URL-1_1-1-up is converted to URL-1_1_1-1-up
;; URL-1_1-2-up is converted to URL-1_1_1-2-up
;; URL-1_1-3-up is converted to URL-1_1_1-3-up
(def URL-1_1-1-up
  {:URLContentType "DistributionURL"
   :Type "GOTO WEB TOOL"
   :Subtype "MOBILE APP"})

(def URL-1_1_1-1-up
  {:URLContentType "DistributionURL"
   :Type "GOTO WEB TOOL"})

(def URL-1_1-2-up
  {:URLContentType "DistributionURL"
   :Type "DOWNLOAD SOFTWARE"
   :Subtype "LIVE ACCESS SERVER (LAS)"})

(def URL-1_1_1-2-up
  {:URLContentType "DistributionURL"
   :Type "DOWNLOAD SOFTWARE"})
 
(def URL-1_1-3-up
  {:URLContentType "DistributionURL"
   :Type "DOWNLOAD SOFTWARE"
   :Subtype "valid subtype in kms"})

(def URL-1_1_1-3-up
  {:URLContentType "DistributionURL"
   :Type "DOWNLOAD SOFTWARE"
   :Subtype "valid subtype in kms"})
    
;; when migrate from 1.1.1 to 1.1,
;; URL-1_1_1-1-down is converted to URL-1_1-1-down
;; URL-1_1_1-2-down is converted to URL-1_1-2-down
(def URL-1_1_1-1-down
  {:URLContentType "DistributionURL"
   :Type "DOWNLOAD SOFTWARE"
   :SubType "MOBILE APP"})

(def URL-1_1-1-down
  {:URLContentType "DistributionURL"
   :Type "DOWNLOAD SOFTWARE"
   :SubType "MOBILE APP"}) 

(def URL-1_1_1-2-down
  {:URLContentType "PublicationURL"
   :Type "VIEW RELATED INFORMATION"
   :Subtype "ALGORITHM DOCUMENTATION"})

(def URL-1_1-2-down
  {:URLContentType "DistributionURL"
   :Type "GOTO WEB TOOL"})

;; when migrate from 1.2.0 to 1.1.1,
;; RelatedURLs-1_2_0-1 is converted to RelatedURLs-1_1_1-2
(def RelatedURLs-1_2_0-1
  [{:URLContentType "CollectionURL"
    :Type "EXTENDED METADATA"
    :Subtype "DMR++"
    :Format "format1"
    :MimeType "mimetype1"}
   {:URLContentType "PublicationURL"
    :Type "VIEW RELATED INFORMATION"
    :Subtype "DATA PRODUCT SPECIFICATION"
    :Format "format1"
    :MimeType "mimetype1"}
   {:URLContentType "VisualizationURL"
    :Type "GET RELATED VISUALIZATION"
    :Subtype "SOTO"
    :Format "format1"
    :MimeType "mimetype1"}
   {:URLContentType "VisualizationURL"
    :Type "Color Map"
    :Subtype "any valid 1.1.1 Subtype"
    :Format "format1"
    :MimeType "mimetype1"}
   {:URLContentType "NotPublicationCollectionVisualizationURL"
    :Type "any valid 1.1.1 Type"
    :Subtype "any valid 1.1.1 Subtype"
    :Format "format1"
    :MimeType "mimetype1"}])

(deftest test-version-steps
  (with-bindings {#'cmr.umm-spec.versioning/versions {:tool ["1.0" "1.1"]}}
    (is (= [] (#'vm/version-steps :tool "1.1" "1.1")))
    (is (= [["1.0" "1.1"]] (#'vm/version-steps :tool "1.0" "1.1")))
    (is (= [["1.1" "1.0"]] (#'vm/version-steps :tool "1.1" "1.0")))))

(defspec all-migrations-produce-valid-umm-spec 100
  (for-all [umm-record   (gen/no-shrink umm-gen/umm-t-generator)
            dest-version (gen/elements (v/versions :tool))]
    (let [dest-media-type (str mt/umm-json "; version=" dest-version)
          metadata (core/generate-metadata (lkt/setup-context-for-test)
                                           umm-record dest-media-type)]
      (empty? (core/validate-metadata :tool dest-media-type metadata)))))

(deftest migrate-1_0-up-to-1_1
  (is (= (dissoc tool-concept-1-1 :PotentialAction)
         (vm/migrate-umm {} :tool "1.0" "1.1"
           tool-concept-1-0))))

(deftest migrate-1_1-down-to-1_0
  (is (= (dissoc tool-concept-1-0 :SearchAction)
         (vm/migrate-umm {} :tool "1.1" "1.0"
           tool-concept-1-1))))

(deftest migrate-1_1-up-to-1_1_1
  ;; RelatedURLs, URL and MetadataSpecificationare are migrated.
  (is (= {:RelatedURLs RelatedURLs-1_1_1-1
          :URL URL-1_1_1-1-up
          :MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/umm/tool/v1.1.1",
                                  :Name "UMM-T",
                                  :Version "1.1.1"}}
         (vm/migrate-umm {} :tool "1.1" "1.1.1"
           {:RelatedURLs RelatedURLs-1_1-1
            :URL URL-1_1-1-up})))
  (is (= {:RelatedURLs RelatedURLs-1_1_1-1
          :URL URL-1_1_1-2-up
          :MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/umm/tool/v1.1.1",
                                  :Name "UMM-T",
                                  :Version "1.1.1"}}
         (vm/migrate-umm {} :tool "1.1" "1.1.1"
           {:RelatedURLs RelatedURLs-1_1-1
            :URL URL-1_1-2-up})))
  (is (= {:RelatedURLs RelatedURLs-1_1_1-1
          :URL URL-1_1_1-3-up
          :MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/umm/tool/v1.1.1",
                                  :Name "UMM-T",
                                  :Version "1.1.1"}}
         (vm/migrate-umm {} :tool "1.1" "1.1.1"
           {:RelatedURLs RelatedURLs-1_1-1
            :URL URL-1_1-3-up}))))

(deftest migrate-1_1_1-down-to-1_1
  ;; RelatedURLs, URL and MetadataSpecification are migrated.
  (is (= {:RelatedURLs RelatedURLs-1_1-2
          :URL URL-1_1-1-down
          :MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/umm/tool/v1.1",
                                  :Name "UMM-T",
                                  :Version "1.1"}} 
         (vm/migrate-umm {} :tool "1.1.1" "1.1"
           {:RelatedURLs RelatedURLs-1_1_1-2
            :URL URL-1_1_1-1-down})))
  (is (= {:RelatedURLs RelatedURLs-1_1-2
          :URL URL-1_1-2-down
          :MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/umm/tool/v1.1",
                                  :Name "UMM-T",
                                  :Version "1.1"}}
         (vm/migrate-umm {} :tool "1.1.1" "1.1"
           {:RelatedURLs RelatedURLs-1_1_1-2
            :URL URL-1_1_1-2-down}))))

(deftest migrate-1_1_1-up-to-1_2_0
  ;; MetadataSpecificationare is migrated.
  (is (= {:RelatedURLs RelatedURLs-1_1_1-2
          :MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/umm/tool/v1.2.0",
                                  :Name "UMM-T",
                                  :Version "1.2.0"}}
         (vm/migrate-umm {} :tool "1.1.1" "1.2.0"
           {:RelatedURLs RelatedURLs-1_1_1-2}))))

(deftest migrate-1_2_0-down-to-1_1_1
  ;; RelatedURLs and MetadataSpecificationare are migrated.
  (is (= {:RelatedURLs RelatedURLs-1_1_1-2
          :MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/umm/tool/v1.1.1",
                                  :Name "UMM-T",
                                  :Version "1.1.1"}}
         (vm/migrate-umm {} :tool "1.2.0" "1.1.1"
           {:RelatedURLs RelatedURLs-1_2_0-1}))))
