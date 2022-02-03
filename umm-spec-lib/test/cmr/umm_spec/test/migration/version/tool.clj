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
