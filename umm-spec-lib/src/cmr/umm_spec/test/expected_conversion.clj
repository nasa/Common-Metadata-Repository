(ns cmr.umm-spec.test.expected-conversion
  "This contains functions for manipulating the expected UMM record when taking a UMM record
  writing it to an XML format and parsing it back. Conversion from a UMM record into metadata
  can be lossy if some fields are not supported by that format"
  (:require
    [clj-time.format :as f]
    [clj-time.core :as t]
    [clojure.string :as str]
    [cmr.common.util :as util :refer [update-in-each]]
    [cmr.umm-spec.json-schema :as js]
    [cmr.umm-spec.models.umm-common-models :as cmn]
    [cmr.umm-spec.models.umm-service-models]
    [cmr.umm-spec.test.dif10-expected-conversion :as dif10]
    [cmr.umm-spec.test.dif9-expected-conversion :as dif9]
    [cmr.umm-spec.test.echo10-expected-conversion :as echo10]
    [cmr.umm-spec.test.expected-conversion-util :as conversion-util]
    [cmr.umm-spec.test.iso19115-expected-conversion :as iso19115]
    [cmr.umm-spec.test.iso-smap-expected-conversion :as iso-smap]
    [cmr.umm-spec.util :as su]))

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
                                :NumberOfInstruments 1
                                :OperationalModes ["on" "off"]
                                :Characteristics [{:Name "Signal to Noise Ratio"
                                                   :Description "Is that necessary?"
                                                   :DataType "float"
                                                   :Unit "dB"
                                                   :Value "10"}]
                                :ComposedOf [{:ShortName "ABC"
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
                                              :Value "Some kind of value"}
                                             {:Type "Some kind of type2"
                                              :Value "Some kind of value2"}]
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
                 :Type "CREATE"}
                {:Date (t/date-time 2013)
                 :Type "UPDATE"}]
    :Abstract "A very abstract collection"
    :VersionDescription "Best version ever"
    :DataLanguage "eng"
    :CollectionDataType "SCIENCE_QUALITY"
    :CollectionProgress "COMPLETE"
    :Projects [{:ShortName "project short_name"}]
    :ISOTopicCategories ["FARMING"
                         "INTELLIGENCE/MILITARY"
                         "GEOSCIENTIFIC INFORMATION"
                         "EXTRA TERRESTRIAL"]
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
                             :OnlineResource {:Linkage "http://www.foo.com"
                                              :Protocol "http"
                                              :ApplicationProfile "http"
                                              :Name "Resource Name"
                                              :Description "Resource Desc"
                                              :Function "function"}
                             :ISBN "1234567789"
                             :Author "author"
                             :Issue "issue"
                             :PublicationPlace "publication place"}
                            {:DOI {:DOI "identifier"
                                   :Authority "authority"}}
                            {:Title "some title"}]
    :TemporalKeywords ["temporal keyword 1" "temporal keyword 2"]
    :AncillaryKeywords ["ancillary keyword 1" "ancillary keyword 2"]
    :RelatedUrls [{:Description "Related url description"
                   :URL "http://www.foo.com"
                   :URLContentType "DistributionURL"
                   :Type "GET DATA"
                   :Subtype "ECHO"
                   :GetData {:Format "ascii"
                             :Size 10.0
                             :Unit "MB"
                             :Fees "fees"}}
                  {:Description "Related url 3 description "
                   :URL "http://www.foo.com"
                   :URLContentType "DistributionURL"
                   :Type "GET SERVICE"
                   :GetService {:MimeType "application/json"
                                :DataID "dataid"
                                :DataType "datatype"
                                :Protocol "HTTP"
                                :FullName "fullname"
                                :URI ["http://www.foo.com", "http://www.bar.com"]}}
                  {:Description "Related url 2 description"
                   :URL "http://www.foo.com"
                   :URLContentType "VisualizationURL"
                   :Type "GET RELATED VISUALIZATION"
                   :Subtype "GIBS"}]
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
    :MetadataDates [{:Date "2009-12-03T00:00:00.000Z"
                     :Type "CREATE"},
                    {:Date "2009-12-04T00:00:00.000Z"
                     :Type "UPDATE"}]
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
                            :DataType "INT"
                            :Description su/not-provided}]
    :ContactGroups [{:Roles ["Investigator"]
                     :Uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc888"
                     :ContactInformation {:RelatedUrls [{:Description "Contact group related url description"
                                                         :URL "http://www.contact.group.foo.com"
                                                         :URLContentType "DataContactURL"
                                                         :Type "HOME PAGE"}]
                                          :ServiceHours "Weekdays 9AM - 5PM"
                                          :ContactInstruction "sample contact group instruction"
                                          :ContactMechanisms [{:Type "Fax" :Value "301-851-1234"}]
                                          :Addresses [{:StreetAddresses ["5700 Rivertech Ct"]
                                                       :City "Riverdale"
                                                       :StateProvince "MD"
                                                       :PostalCode "20774"
                                                       :Country "U.S.A."}]}
                     :GroupName "NSIDC_IceBridge"}]
    :ContactPersons [{:Roles ["Data Center Contact" "Technical Contact" "Science Contact"]
                      :Uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"
                      :ContactInformation {:RelatedUrls [{:Description "Contact related url description"
                                                          :URL "http://www.contact.foo.com"
                                                          :URLContentType "DataContactURL"
                                                          :Type "HOME PAGE"}]
                                           :ServiceHours "Weekdays 9AM - 5PM"
                                           :ContactInstruction "sample contact instruction"
                                           :ContactMechanisms [{:Type "Telephone" :Value "301-851-1234"}
                                                               {:Type "Email" :Value "cmr@nasa.gov"}]
                                           :Addresses [{:StreetAddresses ["NASA GSFC, Code 610.2"]
                                                        :City "Greenbelt"
                                                        :StateProvince "MD"
                                                        :PostalCode "20771"
                                                        :Country "U.S.A."}]}
                      :FirstName "John"
                      :MiddleName "D"
                      :LastName "Smith"}]
    :DataCenters [{:Roles ["ORIGINATOR"]
                   :ShortName "LPDAAC"
                   :ContactPersons [{:Roles ["Data Center Contact" "Technical Contact" "Science Contact"]
                                     :Uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"
                                     :ContactInformation {:RelatedUrls [{:Description "Contact related url description"
                                                                         :URL "http://www.contact.foo.com"
                                                                         :URLContentType "DataContactURL"
                                                                         :Type "HOME PAGE"}]
                                                          :ServiceHours "Weekdays 9AM - 5PM"
                                                          :ContactInstruction "sample contact instruction"
                                                          :ContactMechanisms [{:Type "Telephone" :Value "301-851-1234"}
                                                                              {:Type "Email" :Value "cmr@nasa.gov"}]
                                                          :Addresses [{:StreetAddresses ["NASA GSFC, Code 610.2"]
                                                                       :City "Greenbelt"
                                                                       :StateProvince "MD"
                                                                       :PostalCode "20771"
                                                                       :Country "U.S.A."}]}
                                     :FirstName "John"
                                     :LastName "Smith"}]
                   :ContactInformation {:ContactMechanisms [{:Type "Twitter" :Value "@lpdaac"}]}}
                  {:Roles ["ARCHIVER" "DISTRIBUTOR"]
                   :ShortName "TNRIS"
                   :LongName "Texas Natural Resources Information System"
                   :Uuid "aa63353f-8686-4175-9296-f6685a04a6da"
                   :ContactPersons [{:Roles ["Data Center Contact" "Technical Contact" "Science Contact"]
                                     :Uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"
                                     :ContactInformation {:RelatedUrls [{:Description "Contact related url description"
                                                                         :URL "http://www.contact.shoes.com"
                                                                         :URLContentType "DataContactURL"
                                                                         :Type "HOME PAGE"}]
                                                          :ServiceHours "Weekdays 9AM - 5PM"
                                                          :ContactInstruction "sample contact instruction"
                                                          :ContactMechanisms [{:Type "Telephone" :Value "301-851-1234"}
                                                                              {:Type "Email" :Value "cmr@nasa.gov"}]
                                                          :Addresses [{:StreetAddresses ["NASA GSFC, Code 610.2"]
                                                                       :City "Greenbelt"
                                                                       :StateProvince "MD"
                                                                       :PostalCode "20771"
                                                                       :Country "U.S.A."}]}
                                     :FirstName "John"
                                     :MiddleName "D"
                                     :LastName "Smith"}]}
                  {:Roles ["ARCHIVER" "DISTRIBUTOR"]
                   :ShortName "NSIDC"
                   :Uuid "aa63353f-8686-4175-9296-f6685a04a6da"
                   :ContactInformation {:RelatedUrls [{:Description "Contact related url description"
                                                       :URL "http://www.contact.shoo.com"
                                                       :URLContentType "DataCenterURL"
                                                       :Type "HOME PAGE"}]
                                        :ServiceHours "Weekdays 9AM - 5PM"
                                        :ContactInstruction "sample contact instruction"
                                        :ContactMechanisms [{:Type "Telephone" :Value "301-851-1234"}
                                                            {:Type "Email" :Value "cmr@nasa.gov"}
                                                            {:Type "Fax" :Value "301-851-4321"}]
                                        :Addresses [{:StreetAddresses ["NASA GSFC, Code 610.2"]
                                                     :City "Greenbelt"
                                                     :StateProvince "MD"
                                                     :PostalCode "20771"
                                                     :Country "U.S.A."}]}
                   :ContactGroups [{:Roles ["Investigator"]
                                    :Uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc888"
                                    :ContactInformation {:RelatedUrls [{:Description "Contact group related url description"
                                                                        :URL "http://www.contact.group.foo.com"
                                                                        :URLContentType "DataContactURL"
                                                                        :Type "HOME PAGE"}]

                                                         :ServiceHours "Weekdays 9AM - 5PM"
                                                         :ContactInstruction "sample contact group instruction"
                                                         :ContactMechanisms [{:Type "Fax" :Value "301-851-1234"}]
                                                         :Addresses [{:StreetAddresses ["5700 Rivertech Ct"]
                                                                      :City "Riverdale"
                                                                      :StateProvince "MD"
                                                                      :PostalCode "20774"
                                                                      :Country "U.S.A."}]}
                                    :GroupName "NSIDC_IceBridge"}]}
                  {:Roles ["PROCESSOR"]
                   :ShortName "Processing Center"
                   :LongName "processor.processor"}]
    :CollectionCitations [{:Creator "Bob"
                           :Editor "Larry"
                           :Title "This is a title"
                           :SeriesName "Series Name"
                           :ReleaseDate (t/date-time 2000)
                           :ReleasePlace "Release Place"
                           :Publisher "Moe"
                           :Version "1"
                           :IssueIdentification "Issue Identification"
                           :DataPresentationForm "Data Presentation Form"
                           :OtherCitationDetails "Other Citation Details"
                           :OnlineResource {:Linkage "http://www.foo.com"
                                            :Name "Data Set Citation"
                                            :Description "Data Set Citation"}}]}))

(defmulti ^:private umm->expected-convert
  "Returns UMM collection that would be expected when converting the source UMM-C record into the
  destination XML format and parsing it back to a UMM-C record."
  (fn [umm-coll metadata-format]
    metadata-format))

(defmethod umm->expected-convert :default
  [umm-coll _]
  umm-coll)

(defmethod umm->expected-convert :echo10
  [umm-coll _]
  (echo10/umm-expected-conversion-echo10 umm-coll))

(defmethod umm->expected-convert :dif
  [umm-coll _]
  (dif9/umm-expected-conversion-dif9 umm-coll))

(defmethod umm->expected-convert :dif10
  [umm-coll _]
  (dif10/umm-expected-conversion-dif10 umm-coll))

(defmethod umm->expected-convert :iso19115
  [umm-coll _]
  (iso19115/umm-expected-conversion-iso19115 umm-coll))

(defmethod umm->expected-convert :iso-smap
  [umm-coll _]
  (iso-smap/umm-expected-conversion-iso-smap umm-coll))

;;; Unimplemented Fields

(def not-implemented-fields
  "This is a list of required but not implemented fields."
  #{:MetadataLanguage :SpatialInformation})

(defn- dissoc-not-implemented-fields
  "Removes not implemented fields since they can't be used for comparison"
  [record metadata-format]
  (reduce (fn [r field]
            (assoc r field nil))
          record
          not-implemented-fields))

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

(defn ignore-ids
  "Returns the given string with ids replaced with place holder, e.g.
   id=\"dd0b91b1b-da2d-4d8e-857e-0bb836ad2fbc\" is changed to id=\"placeholder\".
   This is used to strip the randomly generated id strings from the ISO19115 metadata during comparison."
  [x]
  (str/replace x #"id=\".*?\">" "id=\"placeholder\""))
