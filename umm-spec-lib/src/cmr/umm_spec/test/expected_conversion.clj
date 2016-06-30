(ns cmr.umm-spec.test.expected-conversion
  "This contains functions for manipulating the expected UMM record when taking a UMM record
  writing it to an XML format and parsing it back. Conversion from a UMM record into metadata
  can be lossy if some fields are not supported by that format"
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.string :as str]
            [cmr.common.util :as util :refer [update-in-each]]
            [cmr.umm-spec.util :as su]
            [cmr.umm-spec.iso19115-2-util :as iso-util]
            [cmr.umm-spec.iso-keywords :as kws]
            [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.date-util :as du]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as cmn]
            [cmr.spatial.mbr :as m]
            ;; Required for loading service models for testing
            [cmr.umm-spec.models.service]
            [cmr.umm-spec.related-url :as ru-gen]
            [cmr.umm-spec.umm-to-xml-mappings.dif10 :as dif10]
            [cmr.umm-spec.umm-to-xml-mappings.echo10.spatial :as echo10-spatial-gen]
            [cmr.umm-spec.xml-to-umm-mappings.echo10.spatial :as echo10-spatial-parse]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.additional-attribute :as iso-aa]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2 :as iso]
            [cmr.umm-spec.location-keywords :as lk]
            [cmr.umm-spec.test.location-keywords-helper :as lkt]))

(def serf-organization-role
  "UMM-S Role that corresponds to SERVICE PROVIDER CONTACT role in SERF"
  "RESOURCEPROVIDER")

(def example-collection-record
  "An example record with fields supported by most formats."
  (js/parse-umm-c
    {:Platforms [{:ShortName "Platform 1"
                  :LongName "Example Platform Long Name 1"
                  :Type "Aircraft"
                  :Characteristics [{:Name "OrbitalPeriod"
                                     :Description "Orbital period in decimal minutes."
                                     :DataType "float"
                                     :Unit "Minutes"
                                     :Value "96.7"}]
                  :Instruments [{:ShortName "An Instrument"
                                 :LongName "The Full Name of An Instrument v123.4"
                                 :Technique "Two cans and a string"
                                 :NumberOfSensors 1
                                 :OperationalModes ["on" "off"]
                                 :Characteristics [{:Name "Signal to Noise Ratio"
                                                    :Description "Is that necessary?"
                                                    :DataType "float"
                                                    :Unit "dB"
                                                    :Value "10"}]
                                 :Sensors [{:ShortName "ABC"
                                            :LongName "Long Range Sensor"
                                            :Characteristics [{:Name "Signal to Noise Ratio"
                                                               :Description "Is that necessary?"
                                                               :DataType "float"
                                                               :Unit "dB"
                                                               :Value "10"}]
                                            :Technique "Drunken Fist"}]}]}]
     :TemporalExtents [{:TemporalRangeType "temp range"
                        :PrecisionOfSeconds 3
                        :EndsAtPresentFlag false
                        :RangeDateTimes [{:BeginningDateTime (t/date-time 2000)
                                          :EndingDateTime (t/date-time 2001)}
                                         {:BeginningDateTime (t/date-time 2002)
                                          :EndingDateTime (t/date-time 2003)}]}]
     :ProcessingLevel {:Id "3"
                       :ProcessingLevelDescription "Processing level description"}
     :ScienceKeywords [{:Category "EARTH SCIENCE" :Topic "top" :Term "ter"}
                       {:Category "EARTH SCIENCE SERVICES" :Topic "topic" :Term "term"
                        :VariableLevel1 "var 1" :VariableLevel2 "var 2"
                        :VariableLevel3 "var 3" :DetailedVariable "detailed"}]
     :LocationKeywords [{:Category "CONTINENT"
                         :Type "AFRICA"
                         :Subregion1 "CENTRAL AFRICA"
                         :Subregion2 "ANGOLA"
                         :Subregion3 nil}
                        {:Category "CONTINENT"
                         :Type "Somewhereville"
                         :DetailedLocation "Detailed Somewhereville"}]
     :SpatialKeywords ["ANGOLA" "Somewhereville"]
     :SpatialExtent {:GranuleSpatialRepresentation "GEODETIC"
                     :HorizontalSpatialDomain {:ZoneIdentifier "Danger Zone"
                                               :Geometry {:CoordinateSystem "GEODETIC"
                                                          :BoundingRectangles [{:NorthBoundingCoordinate 45.0 :SouthBoundingCoordinate -81.0 :WestBoundingCoordinate 25.0 :EastBoundingCoordinate 30.0}]}}
                     :VerticalSpatialDomains [{:Type "Some kind of type"
                                               :Value "Some kind of value"}]
                     :OrbitParameters {:SwathWidth 2.0
                                       :Period 96.7
                                       :InclinationAngle 94.0
                                       :NumberOfOrbits 2.0
                                       :StartCircularLatitude 50.0}}
     :TilingIdentificationSystems [{:TilingIdentificationSystemName "Tiling System Name"
                                     :Coordinate1 {:MinimumValue 1.0
                                                   :MaximumValue 10.0}
                                     :Coordinate2 {:MinimumValue 1.0
                                                   :MaximumValue 10.0}}]
     :AccessConstraints {:Description "Restriction Comment: Access constraints"
                         :Value "0"}
     :UseConstraints "Restriction Flag: Use constraints"
     :Distributions [{:Sizes [{:Size 15.0 :Unit "KB"}]
                      :DistributionMedia "8 track"
                      :DistributionFormat "Animated GIF"
                      :Fees "Gratuit-Free"}
                     {:Sizes [{:Size 1.0 :Unit "MB"}]
                      :DistributionMedia "Download"
                      :DistributionFormat "Bits"
                      :Fees "0.99"}]
     :EntryTitle "The entry title V5"
     :ShortName "Short"
     :Version "V5"
     :DataDates [{:Date (t/date-time 2012)
                  :Type "CREATE"}]
     :Abstract "A very abstract collection"
     :DataLanguage "English"
     :CollectionDataType "SCIENCE_QUALITY"
     :Projects [{:ShortName "project short_name"}]
     :Quality "Pretty good quality"
     :PublicationReferences [{:PublicationDate (t/date-time 2015)
                              :OtherReferenceDetails "Other reference details"
                              :Series "series"
                              :Title "title"
                              :DOI {:DOI "doi:xyz"
                                    :Authority "DOI"}
                              :Pages "100"
                              :Edition "edition"
                              :ReportNumber "25"
                              :Volume "volume"
                              :Publisher "publisher"
                              :RelatedUrl {:URLs ["www.foo.com" "www.shoo.com"]}
                              :ISBN "ISBN"
                              :Author "author"
                              :Issue "issue"
                              :PublicationPlace "publication place"}
                             {:DOI {:DOI "identifier"
                                    :Authority "authority"}}
                             {:Title "some title"}]
     :TemporalKeywords ["temporal keyword 1" "temporal keyword 2"]
     :AncillaryKeywords ["ancillary keyword 1" "ancillary keyword 2"]
     :RelatedUrls [{:Description "Related url description"
                    :Relation ["GET DATA" "sub type"]
                    :URLs ["www.foo.com", "www.shoo.com"]
                    :Title "related url title"
                    :MimeType "mime type"}
                   {:Description "Related url 3 description "
                    :Relation ["Some type" "sub type"]
                    :URLs ["www.foo.com"]}
                   {:Description "Related url 2 description"
                    :Relation ["GET RELATED VISUALIZATION"]
                    :URLs ["www.foo.com"]
                    :FileSize {:Size 10.0 :Unit "MB"}}]
     :MetadataAssociations [{:Type "SCIENCE ASSOCIATED"
                             :Description "Associated with a collection"
                             :EntryId "AssocEntryId"
                             :Version "V8"},
                            {:Type "INPUT"
                             :Description "Some other collection"
                             :EntryId "AssocEntryId2"
                             :Version "V2"}
                            {:Type nil
                             :Description nil
                             :EntryId "AssocEntryId3"
                             :Version nil}
                            {:Type "INPUT"
                             :EntryId "AssocEntryId4"}]
     :AdditionalAttributes [{:Group "Accuracy"
                             :Name "PercentGroundHit"
                             :DataType "FLOAT"
                             :Description "Percent of data for this granule that had a detected ground return of the transmitted laser pulse."
                             :MeasurementResolution "1"
                             :ParameterRangeBegin "0.0"
                             :ParameterRangeEnd "100.0"
                             :ParameterUnitsOfMeasure "Percent"
                             :UpdateDate "2015-10-22"
                             :Value "50"
                             :ParameterValueAccuracy "1"
                             :ValueAccuracyExplanation "explaination for value accuracy"}
                            {:Name "aa-name"
                             :DataType "INT"}]
     :Organizations [{:Role "ORIGINATOR"
                      :Party {:ServiceHours "24/7"
                              :OrganizationName {:ShortName "org 1"
                                                 :LongName "longname"}
                              :Addresses [{:StreetAddresses ["23 abc st"]
                                           :City "city"}]}}
                     {:Role "POINTOFCONTACT"
                      :Party {:Person {:LastName "person 1"}
                              :RelatedUrls [{:Description "Organization related url description"
                                             :Relation ["Some type" "sub type"]
                                             :URLs ["www.foo.com"]}]}}
                     {:Role "DISTRIBUTOR"
                      :Party {:OrganizationName {:ShortName "org 2"}
                              :Contacts [{:Type "email" :Value "abc@foo.com"}]}}
                     {:Role "PROCESSOR"
                      :Party {:OrganizationName {:ShortName "org 3"}}}]
     :Personnel [{:Role "POINTOFCONTACT"
                  :Party {:Person {:LastName "person 2"}}}]}))



(def example-service-record
  "An example record with fields supported by most formats."
  (js/coerce js/umm-s-schema
             {:MetadataDates [{:Date "2009-12-03T00:00:00.000Z"
                               :Type "CREATE"},
                              {:Date "2009-12-04T00:00:00.000Z"
                               :Type "UPDATE"}]
              :ServiceLanguage "English"
              :AccessConstraints {:Description "Access Constraint"}
              :Responsibilities [{:Party {:Person {:FirstName "FIRSTNAME"
                                                   :LastName "LASTNAME"}
                                          :Contacts [{:Type "email"
                                                      :Value "FIRSTNAME.LASTNAME@nasa.gov"}
                                                     {:Type "phone"
                                                      :Value "301-555-5555"}
                                                     {:Type "phone"
                                                      :Value "301-777-5555"}
                                                     {:Type "fax"
                                                      :Value "301-555-5678"}]
                                          :Addresses [{:StreetAddresses ["NASA/GSFC Code 610.2"]
                                                       :City "Greenbelt"
                                                       :StateProvince "Maryland"
                                                       :PostalCode "20771"
                                                       :Country "USA"}]}
                                  :Role "POINTOFCONTACT"}
                                 {:Party {:Person {:FirstName "FIRSTNAME"
                                                   :LastName "LASTNAME"}
                                          :Contacts [{:Type "email"
                                                      :Value "FIRSTNAME.LASTNAME@nasa.gov"}
                                                     {:Type "phone",
                                                      :Value "301-555-5555"}
                                                     {:Type "phone"
                                                      :Value "301-777-5555"}
                                                     {:Type "fax",
                                                      :Value "301-555-5678"}]
                                          :Addresses [{:StreetAddresses ["NASA/GSFC Code 610.2"]
                                                       :City "Greenbelt"
                                                       :StateProvince "Maryland"
                                                       :PostalCode "20771"
                                                       :Country "USA"}]}
                                  :Role "AUTHOR"}
                                 {:Party {:OrganizationName {:ShortName "NASA/GSFC/SED/ESD/GCDC/GESDISC"
                                                             :LongName "Goddard Earth Sciences Data and Information Services Center (formerly Goddard DAAC), Global Change Data Center, Earth Sciences Division, Science and Exploration Directorate, Goddard Space Flight Center, NASA"}
                                          :Person {:FirstName "FIRSTNAME"
                                                   :LastName "LASTNAME"}
                                          :Contacts [{:Type "email"
                                                      :Value "FIRSTNAME.LASTNAME@nasa.gov"}
                                                     {:Type "phone"
                                                      :Value "301-555-5555"}
                                                     {:Type "fax"
                                                      :Value "301-555-5555"}]
                                          :Addresses [{:StreetAddresses ["NASA GSFC, Code 610.2"]
                                                       :City "Greenbelt"
                                                       :StateProvince "MD"
                                                       :PostalCode "20771"
                                                       :Country "U.S.A."}]
                                          :RelatedUrls [{:URLs ["http://disc.gsfc.nasa.gov/"]}]}
                                  :Role "RESOURCEPROVIDER"}]
              :ISOTopicCategories ["CLIMATOLOGY/METEOROLOGY/ATMOSPHERE"
                                   "ENVIRONMENT"
                                   "IMAGERY/BASE MAPS/EARTH COVER"]
              :Abstract "This is one of the GES DISC's OGC Web Coverage Service (WCS) instances which provides Level 3 Gridded atmospheric data products derived from the Atmospheric Infrared Sounder (AIRS) on board NASA's Aqua spacecraft."
              :ServiceCitation [{:Creator "NASA Goddard Earth Sciences (GES) Data and Information Services Center (DISC)"
                                 :Title "OGC Web Coverage Service (WCS) for accessing Atmospheric Infrared Sounder (AIRS) Data"}]
              :RelatedUrls [{:Description "\n   This Web Coverage Service (WCS) is one of the multiple GES DISC data service instances used to provide gridded Level 3 Atmospheric Infrared Sounder (AIRS) data products. Accessing to this URL will result in a brief description of coverages (i.e., data layers or variables), or a getCapabilities response. A client can request more detailed information about the served coverages by sending a describeCoverage request to the server. Finally, a client can request actual data using a getCoverage request. \n"
                             :Relation ["GET SERVICE" "GET WEB COVERAGE SERVICE (WCS)"]
                             :URLs ["http://acdisc.sci.gsfc.nasa.gov/daac-bin/wcsAIRSL3?Service=WCS&Version=1.0.0&Request=getCapabilities"]}]
              :ServiceKeywords [{:Category "EARTH SCIENCE SERVICES"
                                 :Topic "WEB SERVICES"
                                 :Term "DATA APPLICATION SERVICES"}
                                {:Category "EARTH SCIENCE SERVICES"
                                 :Topic "WEB SERVICES"
                                 :Term "DATA APPLICATION SERVICES"}
                                {:Category "EARTH SCIENCE SERVICES"
                                 :Topic "WEB SERVICES"
                                 :Term "DATA PROCESSING SERVICES"}
                                {:Category "EARTH SCIENCE SERVICES"
                                 :Topic "WEB SERVICES"
                                 :Term "INFORMATION MANAGEMENT SERVICES"}]
              :MetadataAssociations [{:EntryId "Test Parent SERF V 5"}]
              :AdditionalAttributes [{:Group "gov.nasa.gsfc.gcmd"
                                      :Value "2015-11-20 16:04:57"
                                      :Name "metadata.extraction_date"}
                                     {:Group "gov.nasa.gsfc.gcmd"
                                      :Value "8.1"
                                      :Name "metadata.keyword_version"}
                                     {:Name "Metadata_Name"
                                      :Description "Root SERF Metadata_Name Object"
                                      :Value "CEOS IDN SERF"}
                                     {:Name "Metadata_Version"
                                      :Description "Root SERF Metadata_Version Object"
                                      :Value "VERSION 9.7.1"}
                                     {:Name "IDN_Node"
                                      :Description "Root SERF IDN_Node Object"
                                      :Value "USA/NASA|IDN Test Node"}
                                     {:Name "IDN_Node"
                                      :Description "Root SERF IDN_Node Object"
                                      :Value "USA/NASA|IDN Test Node 2"}]
              :EntryId "NASA_GES_DISC_AIRS_Atmosphere_Data_Web_Coverage_Service"
              :ScienceKeywords [{:Category "EARTH SCIENCE"
                                 :Topic "ATMOSPHERE"
                                 :Term "AEROSOLS"}
                                {:Category "EARTH SCIENCE"
                                 :Topic "ATMOSPHERE"
                                 :Term "AIR QUALITY"}
                                {:Category "EARTH SCIENCE"
                                 :Topic "ATMOSPHERE"
                                 :Term "ATMOSPHERIC CHEMISTRY"}
                                {:Category "EARTH SCIENCE"
                                 :Topic "ATMOSPHERE"
                                 :Term "ATMOSPHERIC TEMPERATURE"}
                                {:Category "EARTH SCIENCE"
                                 :Topic "ATMOSPHERE"
                                 :Term "ATMOSPHERIC WATER VAPOR"}
                                {:Category "EARTH SCIENCE"
                                 :Topic "ATMOSPHERE"
                                 :Term "ATMOSPHERIC WINDS"}
                                {:Category "EARTH SCIENCE"
                                 :Topic "ATMOSPHERE"
                                 :Term "CLOUDS"}
                                {:Category "EARTH SCIENCE"
                                 :Topic "ATMOSPHERE"
                                 :Term "PRECIPITATION"}
                                {:Category "EARTH SCIENCE"
                                 :Topic "ATMOSPHERE"
                                 :Term "ATMOSPHERIC RADIATION"}
                                {:Category "EARTH SCIENCE"
                                 :Topic "ATMOSPHERE"
                                 :Term "ATMOSPHERIC PRESSURE"}]
              :EntryTitle "OGC Web Coverage Service (WCS) for accessing Atmospheric Infrared Sounder (AIRS) Data"
              :Distributions [{ :DistributionMedia "Digital",
                               :DistributionSize "<=728MB per request",
                               :DistributionFormat "HTTP",
                               :Fees "None"}]
              :Platforms [{:ShortName "AQUA"
                           :LongName "Earth Observing System, AQUA"
                           :Instruments [{:LongName "Airborne Electromagnetic Profiler"
                                          :ShortName "AEM"}
                                         {:ShortName "AIRS"
                                          :LongName "Atmospheric Infrared Sounder"}
                                         {:ShortName "AERS"
                                          :LongName "Atmospheric/Emitted Radiation Sensor"}]}]
              :Projects  [{ :ShortName "EOS"
                           :LongName "Earth Observing System"}
                          {:ShortName "EOSDIS"
                           :LongName "Earth Observing System Data Information System"}
                          {:ShortName "ESIP"
                           :LongName "Earth Science Information Partners Program"}
                          {:ShortName "OGC/WCS",
                           :LongName "Open Geospatial Consortium/Web Coverage Service"}]}))

(defn- prune-empty-maps
  "If x is a map, returns nil if all of the map's values are nil, otherwise returns the map with
  prune-empty-maps applied to all values. If x is a collection, returns the result of keeping the
  non-nil results of calling prune-empty-maps on each value in x."
  [x]
  (cond
    (map? x) (let [pruned (reduce (fn [m [k v]]
                                    (assoc m k (prune-empty-maps v)))
                                  x
                                  x)]
               (when (seq (keep val pruned))
                 pruned))
    (vector? x) (when-let [pruned (prune-empty-maps (seq x))]
                  (vec pruned))
    (seq? x)    (seq (keep prune-empty-maps x))
    :else x))

(defmulti ^:private umm->expected-convert
  "Returns UMM collection that would be expected when converting the source UMM-C record into the
  destination XML format and parsing it back to a UMM-C record."
  (fn [umm-coll metadata-format]
    metadata-format))

(defmethod umm->expected-convert :default
  [umm-coll _]
  umm-coll)

;;; Utililty Functions

(defn fixup-dif10-data-dates
  "Returns DataDates seq as it would be parsed from ECHO and DIF 10 XML document."
  [data-dates]
  (when (seq data-dates)
    (let [date-types (group-by :Type data-dates)]
      (filter some?
              (for [date-type ["CREATE" "UPDATE" "REVIEW" "DELETE"]]
                (last (sort-by :Date (get date-types date-type))))))))

(defn fixup-echo10-data-dates
  [data-dates]
  (seq
    (remove #(= "REVIEW" (:Type %))
            (fixup-dif10-data-dates data-dates))))

(defn single-date->range
  "Returns a RangeDateTimeType for a single date."
  [date]
  (cmn/map->RangeDateTimeType {:BeginningDateTime date
                               :EndingDateTime    date}))

(defn split-temporals
  "Returns a seq of temporal extents with a new extent for each value under key
  k (e.g. :RangeDateTimes) in each source temporal extent."
  [k temporal-extents]
  (reduce (fn [result extent]
            (if-let [values (get extent k)]
              (concat result (map #(assoc extent k [%])
                                  values))
              (concat result [extent])))
          []
          temporal-extents))

(defn- date-time->date
  "Returns the given datetime to a date."
  [date-time]
  (some->> date-time
           (f/unparse (f/formatters :date))
           (f/parse (f/formatters :date))))

;;; Format-Specific Translation Functions

(defn- echo10-expected-fees
  "Returns the fees if it is a number string, i.e., can be converted to a decimal, otherwise nil."
  [fees]
  (when fees
    (try
      (format "%9.2f" (Double. fees))
      (catch NumberFormatException e))))

(defn- echo10-expected-distributions
  "Returns the ECHO10 expected distributions for comparing with the distributions in the UMM-C
  record. ECHO10 only has one Distribution, so here we just pick the first one."
  [distributions]
  (some-> distributions
          first
          (assoc :Sizes nil :DistributionMedia nil)
          (update-in [:Fees] echo10-expected-fees)
          su/convert-empty-record-to-nil
          vector))

;; ECHO 10

(defn fix-echo10-dif10-polygon
  "Because the generated points may not be in valid UMM order (closed and CCW), we need to do some
  fudging here."
  [gpolygon]
  (let [fix-points (fn [points]
                     (-> points
                         su/closed-counter-clockwise->open-clockwise
                         su/open-clockwise->closed-counter-clockwise))]
    (-> gpolygon
        (update-in [:Boundary :Points] fix-points)
        (update-in-each [:ExclusiveZone :Boundaries] update-in [:Points] fix-points))))

(def relation-set #{"GET DATA"
                    "GET RELATED VISUALIZATION"
                    "VIEW RELATED INFORMATION"})

(defn- expected-echo10-related-urls
  [related-urls]
  (seq (for [related-url related-urls
             :let [[rel] (:Relation related-url)]
             url (:URLs related-url)]
         (-> related-url
             (assoc :Title nil :URLs [url])
             (update-in [:FileSize] (fn [file-size]
                                      (when (and file-size
                                                 (= rel "GET RELATED VISUALIZATION"))
                                        (when-let [byte-size (ru-gen/convert-to-bytes
                                                               (:Size file-size) (:Unit file-size))]
                                          (assoc file-size :Size (/ (int byte-size) 1024) :Unit "KB")))))
             (update-in [:Relation] (fn [[rel]]
                                      (when (relation-set rel)
                                        [rel])))))))

(defn- geometry-with-coordinate-system
  "Returns the geometry with default CoordinateSystem added if it doesn't have a CoordinateSystem."
  [geometry]
  (when geometry
    (update-in geometry [:CoordinateSystem] #(if % % "CARTESIAN"))))

(defn- expected-echo10-spatial-extent
  "Returns the expected ECHO10 SpatialExtent for comparison with the umm model."
  [spatial-extent]
  (let [spatial-extent (prune-empty-maps spatial-extent)]
    (if (get-in spatial-extent [:HorizontalSpatialDomain :Geometry])
      (update-in spatial-extent
                 [:HorizontalSpatialDomain :Geometry]
                 geometry-with-coordinate-system)
      spatial-extent)))

(defn fix-location-keyword-conversion
  "Takes a non-kms keyword and converts it to the expected value"
  [location-keywords]
  ;;Convert the Location Keyword to a leaf.
  (let [leaf-values (lk/location-keywords->spatial-keywords location-keywords)
        translated-values (lk/translate-spatial-keywords
                           (lkt/setup-context-for-test lkt/sample-keyword-map) leaf-values)]
    ;;If the keyword exists in the hierarchy
    (seq (map #(umm-c/map->LocationKeywordType %) translated-values))))

(defmethod umm->expected-convert :echo10
  [umm-coll _]
  (-> umm-coll
      (update-in [:TemporalExtents] (comp seq (partial take 1)))
      (update-in [:DataDates] fixup-echo10-data-dates)
      (assoc :DataLanguage nil)
      (assoc :Quality nil)
      (assoc :UseConstraints nil)
      (assoc :PublicationReferences nil)
      (assoc :AncillaryKeywords nil)
      (assoc :ISOTopicCategories nil)
      (assoc :Personnel nil)
      (assoc :Organizations [su/not-provided-organization])
      (update-in [:ProcessingLevel] su/convert-empty-record-to-nil)
      (update-in [:Distributions] echo10-expected-distributions)
      (update-in-each [:SpatialExtent :HorizontalSpatialDomain :Geometry :GPolygons]
                      fix-echo10-dif10-polygon)
      (update-in [:SpatialExtent] expected-echo10-spatial-extent)
      (update-in-each [:AdditionalAttributes] assoc :Group nil :MeasurementResolution nil
                      :ParameterUnitsOfMeasure nil :ParameterValueAccuracy nil
                      :ValueAccuracyExplanation nil :UpdateDate nil)
      (update-in-each [:Projects] assoc :Campaigns nil)
      (update-in [:RelatedUrls] expected-echo10-related-urls)
      ;; We can't restore Detailed Location because it doesn't exist in the hierarchy.
      (update-in [:LocationKeywords] fix-location-keyword-conversion)
      ;; CMR 2716 Getting rid of SpatialKeywords but keeping them for legacy purposes.
      (assoc :SpatialKeywords nil)
      (assoc :PaleoTemporalCoverages nil)))

;; DIF 9

(defn dif9-temporal
  "Returns the expected value of a parsed DIF 9 UMM record's :TemporalExtents. All dates under
  SingleDateTimes are converted into ranges and concatenated with all ranges into a single
  TemporalExtentType."
  [temporal-extents]
  (let [singles (mapcat :SingleDateTimes temporal-extents)
        ranges (mapcat :RangeDateTimes temporal-extents)
        all-ranges (concat ranges
                           (map single-date->range singles))]
    (when (seq all-ranges)
      [(cmn/map->TemporalExtentType
         {:RangeDateTimes all-ranges})])))

(defn dif-publication-reference
  "Returns the expected value of a parsed DIF 9 publication reference"
  [pub-ref]
  (-> pub-ref
      (update-in [:DOI] (fn [doi] (when doi (assoc doi :Authority nil))))
      (update-in [:RelatedUrl]
                 (fn [related-url]
                   (when related-url (assoc related-url
                                            :URLs (seq (remove nil? [(first (:URLs related-url))]))
                                            :Description nil
                                            :Relation nil
                                            :Title nil
                                            :MimeType nil
                                            :FileSize nil))))))

(defn- expected-related-urls-for-dif-serf
  "Expected Related URLs for DIF and SERF concepts"
  [related-urls]
  (seq (for [related-url related-urls]
         (assoc related-url :Title nil :FileSize nil :MimeType nil))))

(defn- expected-dif-instruments
  "Returns the expected DIF instruments for the given instruments"
  [instruments]
  (seq (map #(assoc % :Characteristics nil :Technique nil :NumberOfSensors nil :Sensors nil
                    :OperationalModes nil) instruments)))

(defn- expected-dif-platform
  "Returns the expected DIF platform for the given platform"
  [platform]
  (-> platform
      (assoc :Type nil :Characteristics nil)
      (update-in [:Instruments] expected-dif-instruments)))

(defn- expected-dif-platforms
  "Returns the expected DIF parsed platforms for the given platforms."
  [platforms]
  (let [platforms (seq (map expected-dif-platform platforms))]
    (if (= 1 (count platforms))
      platforms
      (if-let [instruments (seq (mapcat :Instruments platforms))]
        (conj (map #(assoc % :Instruments nil) platforms)
              (cmn/map->PlatformType {:ShortName su/not-provided
                                      :LongName su/not-provided
                                      :Instruments instruments}))
        platforms))))

(defn- expected-dif-spatial-extent
  "Returns the expected DIF parsed spatial extent for the given spatial extent."
  [spatial]
  (let [spatial (-> spatial
                    (assoc :SpatialCoverageType "HORIZONTAL"
                           :OrbitParameters nil
                           :VerticalSpatialDomains nil)
                    (update-in [:HorizontalSpatialDomain] assoc
                               :ZoneIdentifier nil)
                    (update-in [:HorizontalSpatialDomain :Geometry] assoc
                               :CoordinateSystem "CARTESIAN"
                               :Points nil
                               :Lines nil
                               :GPolygons nil)
                    (update-in-each [:HorizontalSpatialDomain :Geometry :BoundingRectangles] assoc
                                    :CenterPoint nil))]
    (if (seq (get-in spatial [:HorizontalSpatialDomain :Geometry :BoundingRectangles]))
      spatial
      (assoc spatial :SpatialCoverageType nil :HorizontalSpatialDomain nil))))

(defmethod umm->expected-convert :dif
  [umm-coll _]
  (-> umm-coll
      ;; DIF 9 only supports entry-id in metadata associations
      (update-in-each [:MetadataAssociations] assoc :Type nil :Description nil :Version nil)
      ;; DIF 9 does not support tiling identification system
      (assoc :TilingIdentificationSystems nil)
      (assoc :Personnel nil) ;; Implement this as part of CMR-1841
      (assoc :Organizations [su/not-provided-organization])
      ;; DIF 9 does not support DataDates
      (assoc :DataDates [su/not-provided-data-date])
      ;; DIF 9 sets the UMM Version to 'Not provided' if it is not present in the DIF 9 XML
      (assoc :Version (or (:Version umm-coll) su/not-provided))
      (update-in [:TemporalExtents] dif9-temporal)
      (update-in [:SpatialExtent] expected-dif-spatial-extent)
      (update-in [:Distributions] su/remove-empty-records)
      ;; DIF 9 does not support Platform Type or Characteristics. The mapping for Instruments is
      ;; unable to be implemented as specified.
      (update-in [:Platforms] expected-dif-platforms)
      (update-in [:ProcessingLevel] su/convert-empty-record-to-nil)
      (update-in-each [:AdditionalAttributes] assoc :ParameterRangeBegin nil :ParameterRangeEnd nil
                      :MeasurementResolution nil :ParameterUnitsOfMeasure nil
                      :ParameterValueAccuracy nil :ValueAccuracyExplanation nil)
      (update-in-each [:Projects] assoc :Campaigns nil :StartDate nil :EndDate nil)
      (update-in-each [:PublicationReferences] dif-publication-reference)
      (update-in [:RelatedUrls] expected-related-urls-for-dif-serf)
      ;;CMR-2716 SpatialKeywords are being replaced by LocationKeywords.
      (assoc :SpatialKeywords nil)))

;; DIF 10
(defn dif10-platform
  [platform]
  ;; Only a limited subset of platform types are supported by DIF 10.
  (assoc platform :Type (get dif10/platform-types (:Type platform))))

(defn- dif10-processing-level
  [processing-level]
  (-> processing-level
      (assoc :ProcessingLevelDescription nil)
      (assoc :Id (get dif10/product-levels (:Id processing-level)))
      su/convert-empty-record-to-nil))

(defn dif10-project
  [proj]
  (-> proj
      ;; DIF 10 only has at most one campaign in Project Campaigns
      (update-in [:Campaigns] #(when (first %) [(first %)]))
      ;; DIF10 StartDate and EndDate are date rather than datetime
      (update-in [:StartDate] date-time->date)
      (update-in [:EndDate] date-time->date)))

(defn- filter-dif10-metadata-associations
  "Removes metadata associations with type \"LARGER CITATIONS WORKS\" since this type is not
  allowed in DIF10."
  [mas]
  (seq (filter #(not= (:Type %) "LARGER CITATION WORKS")
               mas)))

(defn- fix-dif10-matadata-association-type
  "Defaults metadata association type to \"SCIENCE ASSOCIATED\"."
  [ma]
  (update-in ma [:Type] #(or % "SCIENCE ASSOCIATED")))

(defn- expected-dif10-related-urls
  [related-urls]
  (seq (for [related-url related-urls]
         (assoc related-url :Title nil :FileSize nil :MimeType nil))))

(defn- expected-dif10-spatial-extent
  [spatial-extent]
  (-> spatial-extent
      (update-in [:HorizontalSpatialDomain :Geometry] geometry-with-coordinate-system)
      (update-in-each [:HorizontalSpatialDomain :Geometry :GPolygons] fix-echo10-dif10-polygon)
      prune-empty-maps))

(defmethod umm->expected-convert :dif10
  [umm-coll _]
  (-> umm-coll
      (update-in [:MetadataAssociations] filter-dif10-metadata-associations)
      (update-in-each [:MetadataAssociations] fix-dif10-matadata-association-type)
      (assoc :Personnel nil) ;; Implement this as part of CMR-1841
      (assoc :Organizations [su/not-provided-organization])
      (update-in [:SpatialExtent] expected-dif10-spatial-extent)
      (update-in [:DataDates] fixup-dif10-data-dates)
      (update-in [:Distributions] su/remove-empty-records)
      (update-in-each [:Platforms] dif10-platform)
      (update-in-each [:AdditionalAttributes] assoc :Group nil :UpdateDate nil
                      :MeasurementResolution nil :ParameterUnitsOfMeasure nil
                      :ParameterValueAccuracy nil :ValueAccuracyExplanation nil)
      (update-in [:ProcessingLevel] dif10-processing-level)
      (update-in-each [:Projects] dif10-project)
      (update-in [:PublicationReferences] prune-empty-maps)
      (update-in-each [:PublicationReferences] dif-publication-reference)
      (update-in [:RelatedUrls] expected-related-urls-for-dif-serf)
      ;; DIF 10 required element
      (update-in [:Abstract] #(or % su/not-provided))
      ;; CMR-2716 SpatialKeywords are replaced by LocationKeywords
      (assoc :SpatialKeywords nil)))

(defn- default-serf-required-additional-attributes
  "Populate a default not-provided value for additional attributes if none exist"
  [aas attribute-name]
  (if (seq (filter #(= attribute-name (:Name %)) aas))
    aas
    (conj aas (cmn/map->AdditionalAttributeType {:Name attribute-name
                                                 :Description (format "Root SERF %s Object" attribute-name)
                                                 :Value su/not-provided}))))

(defn- default-serf-additional-attributes
  "Modifies attributes in serf from expected-conversion"
  [aa]
  (-> aa
      (select-keys [:Description :Name :Value :Group :UpdateDate :DataType :Value])
      (assoc :Name (get aa :Name su/not-provided))
      (cmn/map->AdditionalAttributeType)))

(defn- fix-serf-aa-update-date-format
  "Fixes SERF update-date format to conform to a specific rule"
  [aa]
  (if-let [u-date (:UpdateDate aa)]
    (assoc aa :UpdateDate (t/date-time (t/year u-date) (t/month u-date) (t/day u-date)))
    aa))

(defn- fix-expected-serf-additional-attributes
  "Check and see if Metadata_Name and Metadata_Version are in serf additional attributes.
  If not, you need to inject them so that a comparison will work"
  [aas]
  (-> aas
      (default-serf-required-additional-attributes "Metadata_Name")
      (default-serf-required-additional-attributes "Metadata_Version")))

(defn- convert-serf-additional-attributes
  [additional-attributes]
  (fix-expected-serf-additional-attributes
    (vec
      (for [attribute additional-attributes]
        (-> attribute
            default-serf-additional-attributes
            fix-serf-aa-update-date-format)))))

(defn- expected-serf-contacts
  [contacts]
  (seq (filter #(#{"email" "phone" "fax"} (:Type %)) contacts)))

(defn- make-sure-organization-exists
  "Make sure a UMM-S Responsibilities element contains exactly one role that can correlate
  to a SERF 'SERVICE PROVIDER CONTACT' role"
  [resps]
  (if (some #(= serf-organization-role (:Role %)) resps)
    (concat (remove #(= serf-organization-role (:Role %)) resps)
            (take 1 (filter #(= serf-organization-role (:Role %)) resps)))
    (conj resps
          (cmn/map->ResponsibilityType
            {:Role serf-organization-role
             :Party (cmn/map->PartyType
                      {:OrganizationName
                       (cmn/map->OrganizationNameType
                         {:ShortName su/not-provided})
                       :Person (cmn/map->PersonType
                                 {:LastName su/not-provided})})}))))

(defn- expected-person
  [person]
  (when-let [{:keys [FirstName MiddleName LastName]} person]
    (-> person
        (assoc :Uuid nil :FirstName nil :MiddleName nil)
        (assoc :LastName (str/join
                           " " (remove nil? [FirstName MiddleName LastName]))))))

(defn- serf-expected-person
  [person]
  (-> person
      (assoc
        :Uuid nil
        :FirstName (:FirstName person)
        :MiddleName (:MiddleName person)
        :LastName (or (:LastName person) su/not-provided))
      cmn/map->PersonType))

(defn- remove-organization-role-and-related-urls
  "Removes an organization-role and related-urls if present from a UMM-S Responsibility"
  [resp]
  (-> resp
      (update-in [:Party :Person] serf-expected-person)
      (assoc-in [:Party :OrganizationName] nil)
      (assoc-in [:Party :RelatedUrls] nil)))

(defn- fix-organization-name-in-party
  "Modifies generated UMM-S Responsibility to conform to SERF rules"
  [resp]
  (let [{:keys [Role Party]} resp]
    ;; SERF only recognizes OrganizationName under a RESOURCEPROVIDER role.
    (if (= serf-organization-role Role)
      (-> resp
          (update-in [:Party :Person] (fn [p] (or p (cmn/map->PersonType {:LastName su/not-provided}))))
          (update-in [:Party :OrganizationName] (fn [o] (or o
                                                            (cmn/map->OrganizationNameType
                                                              {:ShortName su/not-provided
                                                               :Uuid nil}))))
          (assoc-in [:Party :OrganizationName :Uuid] nil)
          (assoc-in [:Party :Person :Uuid] nil))
      (let [resp (remove-organization-role-and-related-urls resp)]
        (if (seq (:Person (:Party resp)))
          resp
          (assoc-in resp [:Party] (cmn/map->PersonType {:LastName su/not-provided})))))))

(defn- serf-expected-addresses
  "Modify UMM-S Addresses to conform to SERF rules"
  [addresses]
  (when-let [address (first addresses)]
    (-> address
        (assoc :StreetAddresses (seq (take 1 (:StreetAddresses address))))
        list)))

(defn- remove-party-elements-not-in-serf
  "Removes elements in a party element that are not in SERF"
  [party]
  (-> party
      (assoc :ContactInstructions nil :ServiceHours nil)
      (update-in [:Addresses] serf-expected-addresses)))

(defn- expected-related-urls-for-serf-party
  [related-urls]
  (when-let [related-url (first related-urls)]
    [(cmn/map->RelatedUrlType {:URLs (take 1 (:URLs related-url))})]))

(defn- expected-serf-responsibility
  [resp]
  (-> resp
      (update-in [:Party :RelatedUrls] expected-related-urls-for-serf-party)
      fix-organization-name-in-party
      (update-in [:Party :Contacts] expected-serf-contacts)
      (update-in [:Party] remove-party-elements-not-in-serf)))

(defn- filter-unused-serf-datetypes
  [dates]
  (remove #(= "DELETE" (:Type %)) dates))

(defn- filter-unique-serf-dates
  [dates]
  (let [dates-by-type (group-by :Type dates)]
    (keep #(first (get dates-by-type %))
          ["CREATE" "UPDATE" "REVIEW"])))


(defn- expected-metadata-dates-for-serf
  [dates]
  (-> dates
      filter-unused-serf-datetypes
      filter-unique-serf-dates
      seq))

(defn- fix-publication-reference-url
  [some-url]
  (when some-url
    (cmn/map->RelatedUrlType {:URLs (->> some-url :URLs (take 1))})))

(defn- expected-serf-service-citation
  [citation]
  (assoc citation
         :DOI nil
         :ReleasePlace nil
         :SeriesName nil
         :DataPresentationForm nil
         :IssueIdentification nil
         :Editor nil
         :ReleaseDate nil
         :OtherCitationDetails nil
         :RelatedUrl (fix-publication-reference-url (:RelatedUrl citation))))

(defn remove-empty-objects
  "Required to remove some extraneous mappings from ResourceCitation that are not used
  in ServiceCitation for the comparison engine."
  [objects]
  (filter #(some val %) objects))

(defn- fix-serf-doi
  [pubref]
  (if (:DOI pubref)
    (assoc-in pubref [:DOI :Authority] nil)
    pubref))

(defn- fix-access-constraints
  [access-constraint]
  (if access-constraint
    (assoc access-constraint :Value nil)
    access-constraint))

(defn fix-serf-project
  [project]
  (assoc project :EndDate nil :StartDate nil :Campaigns nil))

(defn fix-metadata-associations
  [metadata-association]
  (if-let [ma (seq (take 1 metadata-association))]
    ma
    metadata-association))

(defmethod umm->expected-convert :serf
  [umm-service _]
  (-> umm-service
      (update-in [:Responsibilities] make-sure-organization-exists)
      (update-in-each [:Responsibilities] expected-serf-responsibility)
      (update-in [:AdditionalAttributes] convert-serf-additional-attributes)
      (update-in [:RelatedUrls] expected-related-urls-for-dif-serf)
      (update-in [:MetadataDates] expected-metadata-dates-for-serf)
      (update-in-each [:ServiceCitation] expected-serf-service-citation)
      (update-in [:ServiceCitation] remove-empty-objects)
      (update-in [:ServiceCitation] seq)
      (update-in-each [:Projects] fix-serf-project)
      (update-in [:AccessConstraints] fix-access-constraints)
      (update-in-each [:MetadataAssociations] assoc :Description nil :Type nil :Version nil)
      (update-in [:MetadataAssociations] fix-metadata-associations)
      (update-in-each [:PublicationReferences] fix-serf-doi)
      (update-in-each [:PublicationReferences] update-in [:RelatedUrl] fix-publication-reference-url)
      (assoc :Platforms nil)))

;; ISO 19115-2

(defn propagate-first
  "Returns coll with the first element's value under k assoc'ed to each element in coll.

  Example: (propagate-first :x [{:x 1} {:y 2}]) => [{:x 1} {:x 1 :y 2}]"
  [k coll]
  (let [v (get (first coll) k)]
    (for [x coll]
      (assoc x k v))))

(defn sort-by-date-type-iso
  "Returns temporal extent records to match the order in which they are generated in ISO XML."
  [extents]
  (let [ranges (filter :RangeDateTimes extents)
        singles (filter :SingleDateTimes extents)]
    (seq (concat ranges singles))))

(defn- fixup-iso-ends-at-present
  "Updates temporal extents to be true only when they have both :EndsAtPresentFlag = true AND values
  in RangeDateTimes, otherwise nil."
  [temporal-extents]
  (for [extent temporal-extents]
    (let [ends-at-present (:EndsAtPresentFlag extent)
          rdts (seq (:RangeDateTimes extent))]
      (-> extent
          (update-in-each [:RangeDateTimes]
                          update-in [:EndingDateTime] (fn [x]
                                                        (when-not ends-at-present
                                                          x)))
          (assoc :EndsAtPresentFlag
                 (when (and rdts ends-at-present)
                   true))))))

(defn- fixup-comma-encoded-values
  [temporal-extents]
  (for [extent temporal-extents]
    (update-in extent [:TemporalRangeType] (fn [x]
                                             (when x
                                               (iso-util/sanitize-value x))))))

(defn expected-iso-19115-2-temporal
  [temporal-extents]
  (->> temporal-extents
       (propagate-first :PrecisionOfSeconds)
       (propagate-first :TemporalRangeType)
       fixup-comma-encoded-values
       fixup-iso-ends-at-present
       (split-temporals :RangeDateTimes)
       (split-temporals :SingleDateTimes)
       sort-by-date-type-iso))

(defn iso-19115-2-publication-reference
  "Returns the expected value of a parsed ISO-19115-2 publication references"
  [pub-refs]
  (seq (for [pub-ref pub-refs
             :when (and (:Title pub-ref) (:PublicationDate pub-ref))]
         (-> pub-ref
             (assoc :ReportNumber nil :Volume nil :RelatedUrl nil :PublicationPlace nil)
             (update-in [:DOI] (fn [doi] (when doi (assoc doi :Authority nil))))
             (update-in [:PublicationDate] date-time->date)))))

(defn- expected-iso-19115-2-distributions
  "Returns the expected ISO19115-2 distributions for comparison."
  [distributions]
  (some->> distributions
           su/remove-empty-records
           vec))

(defn- expected-iso-19115-2-related-urls
  [related-urls]
  (seq (for [related-url related-urls
             url (:URLs related-url)]
         (-> related-url
             (assoc :Title nil :MimeType nil :FileSize nil :URLs [url])
             (update-in [:Relation]
                        (fn [[rel]]
                          (when (relation-set rel)
                            [rel])))))))

(defn- fix-iso-vertical-spatial-domain-values
  [vsd]
  (let [fix-val (fn [x]
                  (when x
                    ;; Vertical spatial domain values are encoded in a comma-separated string in ISO
                    ;; XML, so the values must be updated to match what we expect in the resulting
                    ;; XML document.
                    (iso-util/sanitize-value x)))]
    (-> vsd
        (update-in [:Type] fix-val)
        (update-in [:Value] fix-val))))

(defn update-iso-spatial
  [spatial-extent]
  (-> spatial-extent
      (assoc-in [:HorizontalSpatialDomain :ZoneIdentifier] nil)
      (update-in-each [:HorizontalSpatialDomain :Geometry :BoundingRectangles] assoc :CenterPoint nil)
      (update-in-each [:HorizontalSpatialDomain :Geometry :Lines] assoc :CenterPoint nil)
      (update-in-each [:HorizontalSpatialDomain :Geometry :GPolygons] assoc :CenterPoint nil)
      (update-in [:VerticalSpatialDomains] #(take 1 %))
      (update-in-each [:VerticalSpatialDomains] fix-iso-vertical-spatial-domain-values)
      prune-empty-maps))

(defn- expected-contacts
  "Returns the contacts with type phone or email"
  [contacts]
  (seq (filter #(.contains #{"phone" "email"} (:Type %)) contacts)))

(defn- update-with-expected-party
  "Update the given organization or personnel with expected person in the party"
  [party]
  (-> party
      (update-in [:Party :Person] expected-person)
      (update-in [:Party :Contacts] expected-contacts)
      (update-in [:Party :OrganizationName] (fn [org-name]
                                              (when org-name
                                                (assoc org-name :LongName nil :Uuid nil))))
      (update-in [:Party :Addresses] (fn [x]
                                       (when-let [address (first x)]
                                         [address])))
      (update-in [:Party :RelatedUrls] (fn [x]
                                         (when-let [related-url (first x)]
                                           (-> related-url
                                               (assoc :Title nil
                                                      :FileSize nil :Relation nil
                                                      :MimeType nil)
                                               (update-in [:URLs] (fn [urls] [(first urls)]))
                                               vector))))))

(defn- expected-responsibility
  [responsibility]
  (-> responsibility
      (update-in-each [:Party :RelatedUrls] assoc :Relation nil)
      update-with-expected-party))

(defn- expected-responsibilities
  [responsibilities allowed-roles]
  (let [resp-by-role (group-by :Role responsibilities)
        resp-by-role (update-in resp-by-role ["DISTRIBUTOR"] #(take 1 %))]
    (seq (map expected-responsibility
              (mapcat resp-by-role allowed-roles)))))

(defn- group-metadata-assocations
  [mas]
  (let [{input-types true other-types false} (group-by (fn [ma] (= "INPUT" (:Type ma))) mas)]
    (seq (concat other-types input-types))))

(defn- update-iso-topic-categories
  "Update ISOTopicCategories values to a default value if it's not one of the specified values."
  [categories]
  (seq (map iso/iso-topic-value->sanitized-iso-topic-category categories)))

(defn- normalize-bounding-rectangle
  [{:keys [WestBoundingCoordinate NorthBoundingCoordinate
           EastBoundingCoordinate SouthBoundingCoordinate
           CenterPoint]}]
  (let [{:keys [west north east south]} (m/mbr WestBoundingCoordinate
                                               NorthBoundingCoordinate
                                               EastBoundingCoordinate
                                               SouthBoundingCoordinate)]
    (cmn/map->BoundingRectangleType
     {:CenterPoint CenterPoint
      :WestBoundingCoordinate west
      :NorthBoundingCoordinate north
      :EastBoundingCoordinate east
      :SouthBoundingCoordinate south})))

(def bounding-rectangles-path
  "The path in UMM to bounding rectangles."
  [:SpatialExtent :HorizontalSpatialDomain :Geometry :BoundingRectangles])

(defn fix-bounding-rectangles
  "Bounding rectangles in UMM JSON during conversion will be passed to the MBR namespace which does
   some normalization on them. The result is still the same area but the values will not be identical."
  [umm]
  (if-let [brs (seq (get-in umm bounding-rectangles-path))]
    (assoc-in umm bounding-rectangles-path (mapv normalize-bounding-rectangle brs))
    umm))

(defmethod umm->expected-convert :iso19115
  [umm-coll _]
  (-> umm-coll
      fix-bounding-rectangles
      (update-in [:SpatialExtent] update-iso-spatial)
      ;; ISO only supports a single tiling identification system
      (update-in [:TilingIdentificationSystems] #(seq (take 1 %)))
      (update-in [:TemporalExtents] expected-iso-19115-2-temporal)
      ;; The following platform instrument properties are not supported in ISO 19115-2
      (update-in-each [:Platforms] update-in-each [:Instruments] assoc
                      :NumberOfSensors nil
                      :OperationalModes nil)
      (assoc :CollectionDataType nil)
      (update-in [:DataLanguage] #(or % "eng"))
      (update-in [:ProcessingLevel] su/convert-empty-record-to-nil)
      (update-in [:Distributions] expected-iso-19115-2-distributions)
      (update-in-each [:Projects] assoc :Campaigns nil :StartDate nil :EndDate nil)
      (update-in [:PublicationReferences] iso-19115-2-publication-reference)
      (update-in [:RelatedUrls] expected-iso-19115-2-related-urls)
      (update-in-each [:AdditionalAttributes] assoc :UpdateDate nil)
      (update-in [:Personnel]
                 expected-responsibilities ["POINTOFCONTACT"])
      (update-in [:Organizations]
                 expected-responsibilities ["POINTOFCONTACT" "ORIGINATOR" "DISTRIBUTOR" "PROCESSOR"])
      (update-in [:MetadataAssociations] group-metadata-assocations)
      (update-in [:ISOTopicCategories] update-iso-topic-categories)
      (update-in [:LocationKeywords] fix-location-keyword-conversion)
      (assoc :SpatialKeywords nil)
      (assoc :PaleoTemporalCoverages nil)))

;; ISO-SMAP
(defn- normalize-smap-instruments
  "Collects all instruments across given platforms and returns a seq of platforms with all
  instruments under each one."
  [platforms]
  (let [all-instruments (seq (mapcat :Instruments platforms))]
    (for [platform platforms]
      (assoc platform :Instruments all-instruments))))

(defn- expected-smap-iso-spatial-extent
  "Returns the expected SMAP ISO spatial extent"
  [spatial-extent]
  (when (get-in spatial-extent [:HorizontalSpatialDomain :Geometry :BoundingRectangles])
    (-> spatial-extent
        (assoc :SpatialCoverageType "HORIZONTAL" :GranuleSpatialRepresentation "GEODETIC")
        (assoc :VerticalSpatialDomains nil :OrbitParameters nil)
        (assoc-in [:HorizontalSpatialDomain :ZoneIdentifier] nil)
        (update-in [:HorizontalSpatialDomain :Geometry]
                   assoc :CoordinateSystem "GEODETIC" :Points nil :GPolygons nil :Lines nil)
        (update-in-each [:HorizontalSpatialDomain :Geometry :BoundingRectangles] assoc :CenterPoint nil)
        prune-empty-maps)))

(defn- expected-smap-data-dates
  "Returns the expected ISO SMAP DataDates."
  [data-dates]
  (if data-dates
    data-dates
    [(cmn/map->DateType {:Type "CREATE" :Date du/parsed-default-date})]))

(defmethod umm->expected-convert :iso-smap
  [umm-coll _]
  (let [original-brs (get-in umm-coll bounding-rectangles-path)
        umm-coll (umm->expected-convert umm-coll :iso19115)
        umm-coll (if (seq original-brs)
                   (assoc-in umm-coll bounding-rectangles-path original-brs)
                   umm-coll)]
    (-> umm-coll
        (update-in [:SpatialExtent] expected-smap-iso-spatial-extent)
        (update-in [:DataDates] expected-smap-data-dates)
        ;; ISO SMAP does not support the PrecisionOfSeconds field.
        (update-in-each [:TemporalExtents] assoc :PrecisionOfSeconds nil)
        ;; Implement this as part of CMR-2057
        (update-in-each [:TemporalExtents] assoc :TemporalRangeType nil)
        ;; Fields not supported by ISO-SMAP
        (assoc :MetadataAssociations nil) ;; Not supported for ISO SMAP
        (assoc :Personnel nil) ;; Implement this as part of CMR-1841
        (assoc :Organizations [su/not-provided-organization])
        (assoc :UseConstraints nil)
        (assoc :AccessConstraints nil)
        (assoc :SpatialKeywords nil)
        (assoc :TemporalKeywords nil)
        (assoc :CollectionDataType nil)
        (assoc :AdditionalAttributes nil)
        (assoc :ProcessingLevel (umm-c/map->ProcessingLevelType {:Id su/not-provided}))
        (assoc :Distributions nil)
        (assoc :Projects nil)
        (assoc :PublicationReferences nil)
        (assoc :AncillaryKeywords nil)
        (assoc :RelatedUrls [su/not-provided-related-url])
        (assoc :ISOTopicCategories nil)
        ;; Because SMAP cannot account for type, all of them are converted to Spacecraft.
        ;; Platform Characteristics are also not supported.
        (update-in-each [:Platforms] assoc :Type "Spacecraft" :Characteristics nil)
        ;; The following instrument fields are not supported by SMAP.
        (update-in-each [:Platforms] update-in-each [:Instruments] assoc
                        :Characteristics nil
                        :OperationalModes nil
                        :NumberOfSensors nil
                        :Sensors nil
                        :Technique nil)
        ;; ISO-SMAP checks on the Category of theme descriptive keywords to determine if it is
        ;; science keyword.
        (update-in [:ScienceKeywords]
                   (fn [sks]
                     (seq
                      (filter #(.contains kws/science-keyword-categories (:Category %)) sks))))
        (update-in [:Platforms] normalize-smap-instruments)
        (assoc :LocationKeywords nil)
        (assoc :PaleoTemporalCoverages nil))))

;;; Unimplemented Fields

(def not-implemented-fields
  "This is a list of required but not implemented fields."
  #{:CollectionCitations :MetadataDates :MetadataLanguage
    :DirectoryNames :MetadataLineages :SpatialInformation})

(defn- dissoc-not-implemented-fields
  "Removes not implemented fields since they can't be used for comparison"
  [record metadata-format]
  (if (contains? #{:serf} metadata-format)
    record
    (reduce (fn [r field]
              (assoc r field nil))
            record
            not-implemented-fields)))

;;; Public API

(defn convert
  "Returns input UMM-C/S record transformed according to the specified transformation for
  metadata-format."
  ([umm-record metadata-format]
   (if (contains? #{:umm-json} metadata-format)
     umm-record
     (-> umm-record
         (umm->expected-convert metadata-format)
         (dissoc-not-implemented-fields metadata-format))))
  ([umm-record src dest]
   (-> umm-record
       (convert src)
       (convert dest))))
