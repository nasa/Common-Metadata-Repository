(ns cmr.umm-spec.test.expected-conversion
  "This contains functions for manipulating the expected UMM record when taking a UMM record
  writing it to an XML format and parsing it back. Conversion from a UMM record into metadata
  can be lossy if some fields are not supported by that format"
  (:require
   [clj-time.core :as t]
   [clj-time.format :as f]
   [clojure.string :as string]
   [cmr.common.util :as util :refer [update-in-each]]
   [cmr.common-app.config :as common-config]
   [cmr.umm-spec.json-schema :as js]
   [cmr.umm-spec.migration.version.core :as core]
   [cmr.umm-spec.models.umm-collection-models :as umm-coll-models]
   [cmr.umm-spec.models.umm-common-models :as cmn]
   [cmr.umm-spec.models.umm-service-models]
   [cmr.umm-spec.test.dif10-expected-conversion :as dif10]
   [cmr.umm-spec.test.dif9-expected-conversion :as dif9]
   [cmr.umm-spec.test.echo10-expected-conversion :as echo10]
   [cmr.umm-spec.test.expected-conversion-util :as conversion-util]
   [cmr.umm-spec.test.iso-smap-expected-conversion :as iso-smap]
   [cmr.umm-spec.test.iso19115-expected-conversion :as iso19115]
   [cmr.umm-spec.util :as su]
   [cmr.umm-spec.versioning :as vers]))

(def example-collection-record-edn-version-1-0
  "An example record with fields supported by most formats in version 1.0!."
  {:CollectionCitations [{:SeriesName "Series Name"
                          :Creator "Bob"
                          :ReleasePlace "Release Place"
                          :Title "This is a title"
                          :Publisher "Moe"
                          :ReleaseDate (t/date-time 2000)
                          :RelatedUrl {:URLs ["http://www.foo.com"]
                                       :Title "Data Set Citation"
                                       :Description "Data Set Citation"
                                       :Relation ["VIEW RELATED INFORMATION" "Citation"]
                                       :MimeType "text/html"}
                          :IssueIdentification "Issue Identification"
                          :Editor "Larry"
                          :DataPresentationForm "Data Presentation Form"
                          :Version "1"
                          :OtherCitationDetails "Other Citation Details"}]
   :MetadataDates [{:Date "2009-12-03T00:00:00.000Z"
                    :Type "CREATE"}
                   {:Date "2009-12-04T00:00:00.000Z"
                    :Type "UPDATE"}]
   :SpatialKeywords ["ANGOLA" "Detailed" "Somewhereville"]
   :ISOTopicCategories ["FARMING" "INTELLIGENCE/MILITARY" "GEOSCIENTIFIC INFORMATION" "EXTRA TERRESTRIAL"]
   :ShortName "Short"
   :TilingIdentificationSystem {:TilingIdentificationSystemName "MISR"
                                :Coordinate1 {:MinimumValue 1.0
                                              :MaximumValue 10.0}
                                :Coordinate2 {:MinimumValue 1.0
                                              :MaximumValue 10.0}}
   :Abstract "A very abstract collection"
   :Personnel [{:Role "POINTOFCONTACT"
                :Party {:Person {:Uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"
                                 :FirstName "John"
                                 :MiddleName "D"
                                 :LastName "Smith"}
                        :ServiceHours "Weekdays 9AM - 5PM"
                        :ContactInstructions "sample contact instruction"
                        :Contacts [{:Type "Telephone"
                                    :Value "301-851-1234"}
                                   {:Type "Email"
                                    :Value "cmr@nasa.gov"}]
                        :Addresses [{:StreetAddresses ["NASA GSFC" "Code 610.2"]
                                     :City "Greenbelt"
                                     :StateProvince "MD"
                                     :Country "U.S.A."
                                     :PostalCode "20771"}]
                        :RelatedUrls [{:Description "Contact related url description"
                                       :URLs ["http://www.contact.foo.com"]}]}}]
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
                            :RelatedUrl {:URLs ["http://www.foo.com"]
                                         :Title "Resource Name"
                                         :Description "Resource Desc"
                                         :Relation ["VIEW RELATED INFORMATION" "Citation"]
                                         :MimeType "text/html"}
                            :ISBN "1234567789"
                            :Author "author"
                            :Issue "issue"
                            :PublicationPlace "publication place"}
                           {:DOI {:DOI "identifier"
                                  :Authority "authority"}}
                           {:Title "some title"}]
   :RelatedUrls [{:Description "Related url description"
                  :URLs ["http://www.foo.com?a=1&ver=5"]
                  :FileSize {:Size 10.0
                             :Unit "MB"}
                  :Relation ["GET DATA" "EARTHDATA SEARCH"]}
                 {:Description "Related url 3 description"
                  :URLs ["http://www.foo.com"]
                  :MimeType "application/json"
                  :Relation ["GET SERVICE"]}
                 {:Description "Related url 2 description"
                  :URLs ["http://www.foo.com"]
                  :Relation ["GET RELATED VISUALIZATION" "GIBS"]}]
   :DataDates [{:Date (t/date-time 2012)
                :Type "CREATE"}
               {:Date (t/date-time 2013)
                :Type "UPDATE"}]
   :Organizations [{:Role "ORIGINATOR"
                    :Party {:OrganizationName {:ShortName "LPDAAC"}
                            :Contacts [{:Type "Twitter"
                                        :Value "@lpdaac"}]}}
                   {:Role "POINTOFCONTACT"
                    :Party {:OrganizationName {:ShortName "TNRIS"
                                               :LongName "Texas Natural Resources Information System"}}}
                   {:Role "POINTOFCONTACT"
                    :Party {:OrganizationName {:ShortName "NSIDC"}
                            :ServiceHours "Weekdays 9AM - 5PM"
                            :ContactInstructions "sample contact instruction"
                            :Contacts [{:Type "Telephone"
                                        :Value "301-851-1234"}
                                       {:Type "Email"
                                        :Value "cmr@nasa.gov"}
                                       {:Type "Fax"
                                        :Value "301-851-4321"}]
                            :Addresses [{:StreetAddresses ["NASA GSFC" "Code 610.2"]
                                         :City "Greenbelt"
                                         :StateProvince "MD"
                                         :Country "U.S.A."
                                         :PostalCode "20771"}]
                            :RelatedUrls [{:Description "Contact related url description"
                                           :URLs ["http://www.contact.shoo.com"]}]}}
                   {:Role "PROCESSOR"
                    :Party {:OrganizationName {:ShortName "Processing Center"
                                               :LongName "processor.processor"}}}]
   :AccessConstraints {:Description "Restriction Comment: Access constraints"
                       :Value 0.0}
   :SpatialExtent {:SpatialCoverageType "HORIZONTAL"
                   :HorizontalSpatialDomain {:ZoneIdentifier "Danger Zone"
                                             :Geometry {:CoordinateSystem "GEODETIC"
                                                        :BoundingRectangles [{:WestBoundingCoordinate 25.0
                                                                              :NorthBoundingCoordinate 45.0
                                                                              :EastBoundingCoordinate 30.0
                                                                              :SouthBoundingCoordinate -81.0}]}}
                   :VerticalSpatialDomains [{:Type "Atmosphere Layer"
                                             :Value "Some kind of value"}
                                            {:Type "Maximum Depth"
                                             :Value "Some kind of value2"}]
                   :OrbitParameters {:SwathWidth 2.0
                                     :Period 96.7
                                     :InclinationAngle 94.0
                                     :NumberOfOrbits 2.0
                                     :StartCircularLatitude 50.0}
                   :GranuleSpatialRepresentation "GEODETIC"}
   :AdditionalAttributes [{:Group "Accuracy"
                           :ParameterUnitsOfMeasure "Percent"
                           :ParameterValueAccuracy "1"
                           :MeasurementResolution "1"
                           :ParameterRangeBegin "0.0"
                           :ValueAccuracyExplanation "explaination for value accuracy"
                           :Value "50"
                           :Name "PercentGroundHit"
                           :Description "Percent of data for this granule that had a detected ground return of the transmitted laser pulse."
                           :UpdateDate "2015-10-22T00:00:00.000Z"
                           :ParameterRangeEnd "100.0"
                           :DataType "FLOAT"}
                          {:Name "aa-name"
                           :Description su/not-provided
                           :DataType "INT"}]
   :ScienceKeywords [{:Category "EARTH SCIENCE" :Topic "top" :Term "ter"}
                     {:Category "EARTH SCIENCE SERVICES" :Topic "topic" :Term "term"
                      :VariableLevel1 "var 1" :VariableLevel2 "var 2"
                      :VariableLevel3 "var 3" :DetailedVariable "detailed"}]
   :Quality "Pretty good quality"
   :EntryTitle "The entry title V5"
   :Distributions [{:DistributionMedia "Online"
                    :Sizes [{:Size 1
                             :Unit "MB"}]
                    :DistributionFormat "netCDF-4"
                    :Fees "US $1.00 per CD-ROM"}
                   {:DistributionMedia "Online"
                    :Sizes [{:Size 1.5
                             :Unit "MB"}]
                    :DistributionFormat "netCDF-5"}]
   :CollectionProgress "COMPLETE"
   :SpatialInformation {:SpatialCoverageType "HORIZONTAL"
                        :HorizontalCoordinateSystem {:GeodeticModel {:HorizontalDatumName "North American Datum 1983"
                                                                     :EllipsoidName "GRS 1980"
                                                                     :SemiMajorAxis 6378137
                                                                     :DenominatorOfFlatteningRatio 298.257222101}
                                                     :GeographicCoordinateSystem {:GeographicCoordinateUnits "Decimal Degrees"
                                                                                  :LongitudeResolution 0.5
                                                                                  :LatitudeResolution 0.5}}}
   :CollectionDataType "SCIENCE_QUALITY"
   :TemporalKeywords ["temporal keyword 1" "temporal keyword 2"]
   :AncillaryKeywords ["ancillary keyword 1" "ancillary keyword 2"]
   :ProcessingLevel {:ProcessingLevelDescription "Processing level description"
                     :Id "3"}
   :Platforms [{:ShortName "Platform 1"
                :LongName "Example Platform Long Name 1"
                :Type "Aircraft"
                :Characteristics [{:Name "OrbitalPeriod"
                                   :Description "Orbital period in decimal minutes."
                                   :DataType "FLOAT"
                                   :Unit "Minutes"
                                   :Value "96.7"}]
                   :Instruments [{:ShortName "An Instrument"
                                  :LongName "The Full Name of An Instrument v123.4"
                                  :Technique "Two cans and a string"
                                  :NumberOfSensors 1
                                  :OperationalModes ["on" "off"]
                                  :Characteristics [{:Name "Signal to Noise Ratio"
                                                     :Description "Is that necessary?"
                                                     :DataType "FLOAT"
                                                     :Unit "dB"
                                                     :Value "10"}]
                                  :Sensors [{:ShortName "ABC"
                                             :LongName "Long Range Sensor"
                                             :Characteristics [{:Name "Signal to Noise Ratio"
                                                                :Description "Is that necessary?"
                                                                :DataType "FLOAT"
                                                                :Unit "dB"
                                                                :Value "10"}]
                                             :Technique "Drunken Fist"}]}]}]
   :Projects [{:ShortName "project short_name"}]
   :Version "V5"
   :TemporalExtents [{:PrecisionOfSeconds 3
                      :EndsAtPresentFlag false
                      :RangeDateTimes [{:BeginningDateTime (t/date-time 2000)
                                        :EndingDateTime (t/date-time 2001)}
                                       {:BeginningDateTime (t/date-time 2002)
                                        :EndingDateTime (t/date-time 2003)}]}]
   :MetadataAssociations [{:Type "SCIENCE ASSOCIATED"
                           :Description "Associated with a collection"
                           :EntryId "AssocEntryId"
                           :Version "V8"}
                          {:Type "INPUT"
                           :Description "Some other collection"
                           :EntryId "AssocEntryId2"
                           :Version "V2"}
                          {:EntryId "AssocEntryId3"}
                          {:Type "INPUT"
                           :EntryId "AssocEntryId4"}]
   :DataLanguage "eng"})

(def example-collection-record-edn
  "An example record with fields supported by most formats."
  {:Platforms [{:ShortName "Platform 1"
                :LongName "Example Platform Long Name 1"
                :Type "Aircraft"
                :Characteristics [{:Name "OrbitalPeriod"
                                   :Description "Orbital period in decimal minutes."
                                   :DataType "FLOAT"
                                   :Unit "Minutes"
                                   :Value "96.7"}]
                :Instruments [{:ShortName "An Instrument"
                               :LongName "The Full Name of An Instrument v123.4"
                               :Technique "Two cans and a string"
                               :NumberOfInstruments 1
                               :OperationalModes ["on" "off"]
                               :Characteristics [{:Name "Signal to Noise Ratio"
                                                  :Description "Is that necessary?"
                                                  :DataType "FLOAT"
                                                  :Unit "dB"
                                                  :Value "10"}]
                               :ComposedOf [{:ShortName "ABC"
                                             :LongName "Long Range Sensor"
                                             :Characteristics [{:Name "Signal to Noise Ratio"
                                                                :Description "Is that necessary?"
                                                                :DataType "FLOAT"
                                                                :Unit "dB"
                                                                :Value "10"}]
                                             :Technique "Drunken Fist"}]}]}]
   :TemporalExtents [{:PrecisionOfSeconds 3
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
   :SpatialInformation {:SpatialCoverageType "HORIZONTAL"}
   :SpatialExtent {:GranuleSpatialRepresentation "GEODETIC"
                   :SpatialCoverageType "HORIZONTAL"
                   :HorizontalSpatialDomain {:ZoneIdentifier "Danger Zone"
                                              :Geometry {:CoordinateSystem "GEODETIC"
                                                         :BoundingRectangles [{:NorthBoundingCoordinate 45.0 :SouthBoundingCoordinate -81.0 :WestBoundingCoordinate 25.0 :EastBoundingCoordinate 30.0}]}

                                             :ResolutionAndCoordinateSystem
                                             {:HorizontalDataResolution
                                               (umm-coll-models/map->HorizontalDataResolutionType
                                                 {:NonGriddedResolutions [(umm-coll-models/map->HorizontalDataResolutionNonGriddedType
                                                                            {:XDimension 0.5
                                                                             :YDimension 0.5
                                                                             :Unit "Decimal Degrees"})]
                                                  :NonGriddedRangeResolutions [(umm-coll-models/map->HorizontalDataResolutionNonGriddedRangeType
                                                                                 {:MinimumXDimension 1
                                                                                  :MaximumXDimension 2
                                                                                  :ViewingAngleType "At Nadir"
                                                                                  :ScanDirection "Along Track"
                                                                                  :Unit "Meters"})]
                                                  :GriddedResolutions [(umm-coll-models/map->HorizontalDataResolutionGriddedType
                                                                         {:XDimension 0.2
                                                                          :Unit "Kilometers"})]})
                                              :Description "ResolutionAndCoordinateSystem Description value."
                                              :GeodeticModel (umm-coll-models/map->GeodeticModelType
                                                               {:HorizontalDatumName "North American Datum 1983"
                                                                :EllipsoidName "GRS 1980"
                                                                :SemiMajorAxis 6378137
                                                                :DenominatorOfFlatteningRatio 298.257222101})}}
                   :VerticalSpatialDomains [{:Type "Atmosphere Layer"
                                             :Value "Some kind of value"}
                                            {:Type "Maximum Depth"
                                             :Value "Some kind of value2"}]
                   :OrbitParameters {:SwathWidth 2.0
                                     :SwathWidthUnit "Kilometer"
                                     :OrbitPeriod 96.7
                                     :OrbitPeriodUnit "Decimal Minute"
                                     :InclinationAngle 94.0
                                     :InclinationAngleUnit "Degree"
                                     :NumberOfOrbits 2.0
                                     :StartCircularLatitude 50.0
                                     :StartCircularLatitudeUnit "Degree"}}
   :TilingIdentificationSystems [{:TilingIdentificationSystemName "MISR"
                                   :Coordinate1 {:MinimumValue 1.0
                                                 :MaximumValue 10.0}
                                   :Coordinate2 {:MinimumValue 1.0
                                                 :MaximumValue 10.0}}]
   :AccessConstraints {:Description "Restriction Comment: Access constraints"
                       :Value "0"}
   :UseConstraints (umm-coll-models/map->UseConstraintsType
                     {:Description "example-collection-record Description"
                      :LicenseURL (cmn/map->OnlineResourceType
                                    {:Linkage "http://example-collection-record.com"})})

   :ArchiveAndDistributionInformation {:FileArchiveInformation
                                       [{:Format "Binary"
                                         :FormatType "Native"
                                         :AverageFileSize 3
                                         :AverageFileSizeUnit "MB"
                                         :TotalCollectionFileSize 1095
                                         :TotalCollectionFileSizeUnit "MB"
                                         :Description
                                         "These files are very difficult to use. If you want to use the binary data then contact us. These files are archived in CUMULUS."}
                                        {:Format "netCDF-4"
                                         :FormatType "Supported"
                                         :AverageFileSize 1
                                         :AverageFileSizeUnit "MB"
                                         :TotalCollectionFileSize 365
                                         :TotalCollectionFileSizeUnit "MB"
                                         :Description
                                         "These files are archived in CUMULUS."}]
                                       :FileDistributionInformation
                                       [{:FormatType "Supported"
                                         :AverageFileSize 1
                                         :Fees "US $1.00 per CD-ROM"
                                         :Format "netCDF-4"
                                         :TotalCollectionFileSize 365
                                         :TotalCollectionFileSizeUnit "MB"
                                         :Description
                                         "These files are available on line or CD-ROMS can be ordered for a fee."
                                         :AverageFileSizeUnit "MB"
                                         :Media ["Online" "CD-ROM"]}
                                        {:Format "netCDF-5"
                                         :FormatType "Supported"
                                         :Media ["Online"]
                                         :AverageFileSize 1.5
                                         :AverageFileSizeUnit "MB"
                                         :TotalCollectionFileSize 548
                                         :TotalCollectionFileSizeUnit "MB"
                                         :Description
                                         "These files may take longer to download as they are only available on demand."}]}
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
   :DOI {:MissingReason "Not Applicable"
         :Explanation "This is an explanation."}
   :AssociatedDOIs [{:DOI "10.4567/DOI1"
                     :Title "Associated Test DOI 1"
                     :Authority "https://doi.org"}
                    {:DOI "10.4567/DOI2"
                     :Title "Associated Test DOI 2"
                     :Authority "https://doi.org"}]
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
                  :URL "http://www.foo.com?a=1&ver=5"
                  :URLContentType "DistributionURL"
                  :Type "GET DATA"
                  :Subtype "Earthdata Search"
                  :GetData {:Format "ascii"
                            :MimeType "application/json"
                            :Checksum "checksum"
                            :Size 10.0
                            :Unit "MB"
                            :Fees "fees"}}
                 {:Description "Related url 3 description "
                  :URL "http://www.foo.com"
                  :URLContentType "DistributionURL"
                  :Type "USE SERVICE API"
                  :GetService {:MimeType "application/json"
                               :DataID "dataid"
                               :DataType "datatype"
                               :Protocol "HTTP"
                               :FullName "fullname"
                               :Format "ascii"
                               :URI ["http://www.foo.com", "http://www.bar.com"]}}
                 {:Description "Related url 2 description"
                  :URL "http://www.foo.com"
                  :URLContentType "VisualizationURL"
                  :Type "GET RELATED VISUALIZATION"
                  :Subtype "WORLDVIEW"}
                 {:Description "Related url 4 description"
                  :URL "http://www.foo.com"
                  :URLContentType "DistributionURL"
                  :Type "GET CAPABILITIES"
                  :Subtype "OpenSearch"
                  :GetData {:Format "ASCII"
                            :Size 0.0,
                            :Unit "KB"
                            :MimeType "application/opensearchdescription+xml"}}]
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
                           :UpdateDate "2015-10-22T00:00:00Z"
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
                                           :Description "Data Set Citation"}}]
   :StandardProduct false
   :MetadataSpecification (umm-coll-models/map->MetadataSpecificationType
                           {:URL (str "https://cdn.earthdata.nasa.gov/umm/collection/v"
                                       vers/current-collection-version),
                            :Name "UMM-C"
                            :Version vers/current-collection-version})})

(def example-collection-record
  "An example record with fields supported by most formats."
  (js/parse-umm-c example-collection-record-edn))

(def example-collection-record-no-swath
  "An example record with missing SwathWidth fields."
  (update-in example-collection-record [:SpatialExtent]
             assoc :OrbitParameters {:OrbitPeriod 96.7
                                     :OrbitPeriodUnit "Decimal Minute"
                                     :InclinationAngle 94.0
                                     :InclinationAngleUnit "Degree"
                                     :NumberOfOrbits 2.0
                                     :StartCircularLatitude 50.0
                                     :StartCircularLatitudeUnit "Degree"
                                     :Footprints [{:Footprint 2.0
                                                   :FootprintUnit "Kilometer"
                                                   :Description "footprint"}
                                                  {:Footprint 3.0
                                                   :FootprintUnit "Meter"
                                                   :Description "footprint"}]}))

(def curr-ingest-ver-example-collection-record
  (core/migrate-umm nil :collection
                    vers/current-collection-version
                    (common-config/collection-umm-version) example-collection-record))

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
  (-> x
      (string/replace #"id=\".*?\"" "id=\"placeholder\"")
      (string/replace #"xlink:href=\".*?\"" "id=\"placeholder\"")))
