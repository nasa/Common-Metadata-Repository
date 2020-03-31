(ns cmr.indexer.test.data.concepts.keyword-util
  "Functions for testing cmr.indexer.data.concepts.keyword-util namespace."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.indexer.data.concepts.collection.keyword :as ckw]
   [cmr.indexer.data.concepts.keyword-util :as keyword-util]))

(def sample-umm-collection-concept
  "This sample UMM Collection data is a mish-mash of several examples, done this
  way simply to provide full testing coverage in a single record. It is not
  intended to represent an actual collection and should not be used for anything
  other than testing."
  {:Abstract "An abstract summary"
   :AdditionalAttributes [{:Name "ALBEDOFILEID"
                           :Description "ID of the kernel albedo table used."
                           :Value "aa-value-0"
                           :DataType "INT"}
                          {:Name "ASTERMapProjection"
                           :Description "The map projection of the granule"
                           :Value "aa-value-1"
                           :DataType "STRING"}]
   :AncillaryKeywords ["LP DAAC" "EOSDIS" "USGS/EROS" "ESIP" "USGS" "LPDAAC"]
   :CollectionCitations [{:Creator "Bowen Island Forest and Water Management Society (BIFWMS)"
                          :OtherCitationDetails (str "U.S. Geological Survey, 1993, Digital Elevation"
                                                     " Models--data users guide 5:[Reston, Virginia],"
                                                     " U.S. Geological Survey, 48 p.")}
                         {:Creator "Solanki, S.K., I.G. Usoskin, B. Kromer, M. Schussler and J. Beer"
                          :OtherCitationDetails "DOC/NOAA/NESDIS/NGDC > National Geophysical Data Center, NESDIS, NOAA, U.S. Department of Commerce"}
                         {:Creator "Dublin Transport Office"
                          :OtherCitationDetails "Full report in PDF is available online"}]
   :DataCenters [{:Roles ["ARCHIVER" "DISTRIBUTOR"]
                  :ShortName "IRIS/PASSCAL"
                  :LongName "PASSCAL Instrument Center, Incorporated Research Institutions for Seismology"
                  :Uuid "10000000-0000-4000-a000-000000000000"
                  :ContactGroups [{:Roles ["Data Center Contact" "Technical Contact"]
                                   :Uuid "00000000-0000-4000-a000-000000000000"
                                   :ContactInformation {:RelatedUrls
                                                        {:Description "A sample related url description."
                                                         :URLContentType "CollectionURL"
                                                         :Type "GET DATA"
                                                         :Subtype "ECHO"
                                                         :URL "example-related-url-one.com"
                                                         :GetData {:Format "ascii"
                                                                   :MimeType "application/json"
                                                                   :Size "0"
                                                                   :Unit "KB"
                                                                   :Fees "None"
                                                                   :Checksum "SHA1-checksum"}
                                                         :GetService {:Format "binary"
                                                                      :MimeType "application/pdf"
                                                                      :Protocol "HTTP"
                                                                      :FullName "ContactGroups/ContactInformation/RelatedUrls/GetService/FullName"
                                                                      :DataID "Contact group contact infomation related url Data ID"
                                                                      :URI ["URI one" "URI two"]}}
                                                        :ServiceHours "9:00 AM to 5:00 PM Monday - Friday."
                                                        :ContactInstruction "Sample contact instructions."
                                                        :ContactMechanisms [{:Type "Email"
                                                                             :Value "sample-email-one@anywhere.com"}
                                                                            {:Type "Mobile"
                                                                             :Value "555-555-5555"}]
                                                        :Addresses [{:StreetAddresses ["15 Minte Drive" "5 Perry Hall Lane"]
                                                                     :City "Baltimore"
                                                                     :StateProvince "Maryland"
                                                                     :Country "USA"
                                                                     :PostalCode "21236"}]}
                                   :GroupName "White Marsh Institute of Health"}]
                  :ContactPersons [{:Roles ["Technical Contact" "Science Contact"]
                                    :Uuid "20000000-0000-4000-a000-000000000000"
                                    :FirstName "John"
                                    :LastName "Doe"
                                    :ContactInformation {:RelatedUrls
                                                         {:Description "A sample related url description."
                                                          :URLContentType "PublicationURL"
                                                          :Type "GET SERVICE"
                                                          :Subtype "EDG"
                                                          :URL "http://example-two.com"
                                                          :GetData {:Format "MODIS Tile SIN"
                                                                    :MimeType "application/x-hdf"
                                                                    :Size "1"
                                                                    :Unit "MB"
                                                                    :Fees "1000"
                                                                    :Checksum "Checksum"}
                                                          :GetService {:Format "binary"
                                                                       :MimeType "application/pdf"
                                                                       :Protocol "HTTP"
                                                                       :FullName "John Doe"
                                                                       :URI ["uri-1" "uri-2"]}}
                                                         :ContactMechanisms [{:Type "Email"
                                                                              :Value "sample-email-two@example.com"}
                                                                             {:Type "Mobile"
                                                                              :Value "666-666-6666"}]
                                                         :Addresses [{:StreetAddresses ["4 Cherrywood Lane" "8100 Baltimore Avenue"]
                                                                      :City "College Park"
                                                                      :StateProvince "MD"
                                                                      :Country "America"
                                                                      :PostalCode "20770"}]}}]}]
   :DirectoryNames [{:ShortName "directory-shortname-one"
                     :LongName "directory-longname-one"}
                    {:ShortName "directory-shortname-two"
                     :LongName "directory-longname-two"}]
   :CollectionDataType "NEAR_REAL_TIME"
   :DOI {:DOI "Dummy-DOI"}
   :EntryTitle "The collection entry title."
   :ShortName "VIIRS"
   :LongName "Visible Infrared Imaging Radiometer suite."
   :ISOTopicCategories ["elevation" "GEOSCIENTIFIC INFORMATION" "OCEANS"]
   :LocationKeywords [{:Category "CONTINENT"
                       :Type "NORTH AMERICA"
                       :Subregion1 "UNITED STATES OF AMERICA"
                       :Subregion2 "MICHIGAN"
                       :Subregion3 "DETROIT"
                       :DetailedLocation "MOUNTAIN"}
                      {:Category "OCEAN"
                       :Type "ATLANTIC OCEAN"
                       :Subregion1 "NORTH ATLANTIC OCEAN"
                       :Subregion2 "GULF OF MEXICO"
                       :DetailedLocation "WATER"}]
   :ProcessingLevel {:Id "4"}
   :TemporalKeywords ["Composit" "Annual" "Day"]
   :TilingIdentificationSystems [{:TilingIdentificationSystemName "MISR"
                                  :Coordinate1 {:MinimumValue 0
                                                :MaximumValue 10}
                                  :Coordinate2 {:MinimumValue 100
                                                :MaximumValue 150}}
                                 {:TilingIdentificationSystemName "CALIPSO"
                                  :Coordinate1 {:MinimumValue -10
                                                :MaximumValue 10}
                                  :Coordinate2 {:MinimumValue -50
                                                :MaximumValue -25}}]
   :Version "001"
   :VersionDescription "The beginning version of a sample collection."
   :Platforms [{:Type "In Situ Land-based Platforms"
                :ShortName "SURFACE WATER WIER"
                :LongName "In-situ-longname"
                :Characteristics [{:Name "characteristic-name-one"
                                   :Description "characteristic-description-one"
                                   :Value "256"
                                   :Unit "Meters"
                                   :DataType "INT"}]
                :Instruments [{:ShortName "LIDAR"
                               :LongName "Light Detection and Ranging"
                               :Characteristics [{:Name "characteristic-name-two"
                                                  :Description "characteristic-description-two"
                                                  :Value "1024.5"
                                                  :Unit "Inches"
                                                  :DataType "FLOAT"}]}
                              {:ShortName "WCMS"
                               :LongName "Water Column Mapping System"}]}]
   :Projects [{:ShortName "EOSDIS"
               :LongName "Earth Observing System Data Information System"}
              {:ShortName "GTOS"
               :LongName "Global Terrestrial Observing System"}
              {:ShortName "ESI"
               :LongName "Environmental Sustainability Index"}]
   :RelatedUrls [{:Description "Related-url description."
                  :URLContentType "PublicationURL"
                  :Type "GET SERVICE"
                  :Subtype "EDG"
                  :URL "related-url-example.com"}
                 {:Description "A test related url."
                  :URLContentType "DataCenterURL"
                  :Type "HOME PAGE"
                  :Subtype "GENERAL DOCUMENTATION"
                  :URL "related-url-example-two.com"}]
   :ContactPersons [
                    {:Roles ["AUTHOR"]
                     :ContactInformation {
                                          :ContactMechanisms [
                                                              {:Type "Email"
                                                               :Value "ncdc.orders at noaa.gov"}
                                                              {:Type "Telephone"
                                                               :Value "+1 828-271-4800"}]
                                          :Addresses [
                                                      {:StreetAddresses ["151 Patton Avenue, Federal Building, Room 468"]
                                                       :City "Asheville"
                                                       :StateProvince "NC"
                                                       :Country "USA"
                                                       :PostalCode "28801-5001"}]}
                     :FirstName "Alice"
                     :MiddleName ""
                     :LastName "Bob"}]
   :ContactGroups [
                   {:Roles ["SCIENCE CONTACT"]
                    :GroupName "TEAM SPOCK"
                    :LongName "VULCAN YET LIVES"
                    :Uuid "007c89f8-39ca-4645-b31a-d06a0118e8b2"
                    :NonServiceOrganizationAffiliation "TEAM KIRK"
                    :ContactInformation {
                                         :ContactMechanisms
                                         [{:Type "Email"
                                           :Value "custserv at usgs.gov"}
                                          {:Type "Fax"
                                           :Value "605-594-6589"}
                                          {:Type "Telephone"
                                           :Value "605-594-6151"}]
                                         :Addresses [
                                                     {:StreetAddresses ["47914 252nd Street"]
                                                      :City "Sioux Falls"
                                                      :StateProvince "SD"
                                                      :Country "USA"
                                                      :PostalCode "57198-0001"}]}}]
   :ScienceKeywords [
                     {:Category "EARTH SCIENCE SERVICES"
                      :Topic "DATA ANALYSIS AND VISUALIZATION"
                      :Term "GEOGRAPHIC INFORMATION SYSTEMS"}
                     {:Category "ATMOSPHERE"
                      :Topic "ATMOSPHERIC WINDS"
                      :Term "SURFACE WINDS"
                      :VariableLevel1 "SPECTRAL/ENGINEERING"
                      :VariableLevel2 "MICROWAVE"
                      :VariableLevel3 "MICROWAVE IMAGERY"
                      :DetailedVariable "RADAR"}
                     {:Category "SCIENCE CAT 3"
                      :Topic "SCIENCE TOPIC 3"
                      :Term "SCIENCE TERM 3"}]
   :ArchiveAndDistributionInformation {:FileDistributionInformation [{:FormatType "Native",
                                                                      :AverageFileSize nil,
                                                                      :Fees nil,
                                                                      :Format "netCDF4",
                                                                      :TotalCollectionFileSize nil,
                                                                      :TotalCollectionFileSizeBeginDate nil,
                                                                      :TotalCollectionFileSizeUnit nil,
                                                                      :Description nil,
                                                                      :AverageFileSizeUnit nil,
                                                                      :Media nil}
                                                                     {:FormatType "Native",
                                                                      :AverageFileSize nil,
                                                                      :Fees nil,
                                                                      :Format "PDF",
                                                                      :TotalCollectionFileSize nil,
                                                                      :TotalCollectionFileSizeBeginDate nil,
                                                                      :TotalCollectionFileSizeUnit nil,
                                                                      :Description nil,
                                                                      :AverageFileSizeUnit nil,
                                                                      :Media nil}]}})

(def sample-umm-service-concept
  "This sample UMM Service data is a mish-mash of several examples, done this
  way simply to provide full testing coverage in a single record. It is not
  intended to represent an actual service and should not be used for anything
  other than testing."
  {:Name "AIRX3STD"
   :LongName "OPeNDAP Service for AIRS Level-3 retrieval products"
   :Type "OPeNDAP"
   :Version "1.9"
   :Description "AIRS Level-3 retrieval product created using AIRS IR, AMSU without HSB."
   :RelatedURLs [{:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
                  :Description "OPeNDAP Service"
                  :Type "GET SERVICE"
                  :Subtype "ACCESS WEB SERVICE"
                  :URLContentType "CollectionURL"}]
   :ContactPersons [
                    {:Roles ["AUTHOR"]
                     :ContactInformation {
                                          :ContactMechanisms [
                                                              {:Type "Email"
                                                               :Value "ncdc.orders at noaa.gov"}
                                                              {:Type "Telephone"
                                                               :Value "+1 828-271-4800"}]
                                          :Addresses [
                                                      {:StreetAddresses ["151 Patton Avenue, Federal Building, Room 468"]
                                                       :City "Asheville"
                                                       :StateProvince "NC"
                                                       :Country "USA"
                                                       :PostalCode "28801-5001"}]}
                     :FirstName "Alice"
                     :MiddleName ""
                     :LastName "Bob"}]
   :Platforms [
               {:ShortName "A340-600"
                :LongName "Airbus A340-600"
                :Instruments [
                              {:ShortName "SMWE4B"
                               :LongName "Senso-matic Wonder Eye 4B"}]}]
   :AncillaryKeywords ["Data Visualization" "Data Discovery"]
   :ScienceKeywords [
                     {:Category "EARTH SCIENCE SERVICES"
                      :Topic "DATA ANALYSIS AND VISUALIZATION"
                      :Term "GEOGRAPHIC INFORMATION SYSTEMS"}
                     {:Category "ATMOSPHERE"
                      :Topic "ATMOSPHERIC WINDS"
                      :Term "SURFACE WINDS"
                      :VariableLevel1 "SPECTRAL/ENGINEERING"
                      :VariableLevel2 "MICROWAVE"
                      :VariableLevel3 "MICROWAVE IMAGERY"
                      :DetailedVariable "RADAR"}
                     {:Category "SCIENCE CAT 3"
                      :Topic "SCIENCE TOPIC 3"
                      :Term "SCIENCE TERM 3"}]
   :ServiceKeywords [
                     {:ServiceCategory "DATA ANALYSIS AND VISUALIZATION"
                      :ServiceTopic "VISUALIZATION/IMAGE PROCESSING"}
                     {:ServiceCategory "DATA ANALYSIS AND VISUALIZATION"}
                     {:ServiceTopic "STATISTICAL APPLICATIONS"}]
   :ContactGroups [
                   {:Roles ["SCIENCE CONTACT"]
                    :GroupName "TEAM SPOCK"
                    :LongName "VULCAN YET LIVES"
                    :Uuid "007c89f8-39ca-4645-b31a-d06a0118e8b2"
                    :NonServiceOrganizationAffiliation "TEAM KIRK"
                    :ContactInformation {
                                         :ContactMechanisms
                                         [{:Type "Email"
                                           :Value "custserv at usgs.gov"}
                                          {:Type "Fax"
                                           :Value "605-594-6589"}
                                          {:Type "Telephone"
                                           :Value "605-594-6151"}]
                                         :Addresses [
                                                     {:StreetAddresses ["47914 252nd Street"]
                                                      :City "Sioux Falls"
                                                      :StateProvince "SD"
                                                      :Country "USA"
                                                      :PostalCode "57198-0001"}]}}]
   :ServiceOrganizations [
                          {:Roles ["SERVICE PROVIDER"]
                           :ShortName "LDPAAC"}
                          {:Roles ["SERVICE PROVIDER"]
                           :ShortName "USGS/EROS"
                           :LongName "US GEOLOGICAL SURVEY EARTH RESOURCE OBSERVATION AND SCIENCE (EROS) LANDSAT CUSTOMER SERVICES"
                           :Uuid "005c89f8-39ca-4645-b31a-d06a0118d7a1"
                           :ContactPersons [{
                                             :Roles [ "PUBLISHER"]
                                             :ContactInformation
                                             {:ContactMechanisms
                                              [{:Type "Email"
                                                :Value "custserv at usgs.gov"}
                                               {:Type "Fax"
                                                :Value "605-594-6589"}
                                               {:Type "Telephone"
                                                :Value "605-594-6151"}]
                                              :Addresses
                                              [{:StreetAddresses ["47914 252nd Street"]
                                                :City "Sioux Falls"
                                                :StateProvince "SD"
                                                :Country "USA"
                                                :PostalCode "57198-0001"}]}
                                             :FirstName "Carol"
                                             :MiddleName "D."
                                             :LastName "Eve"}]}]})

(deftest extract-collection-field-values
  (are3 [field-key values]
    (is (= values
           ((#'keyword-util/field-extract-fn field-key) sample-umm-collection-concept)))

    "Abstract field"
    :Abstract
    "An abstract summary"

    "DOI field"
    :DOI
    "Dummy-DOI"

    "EntryTitle field"
    :EntryTitle
    "The collection entry title."

    "LongName field"
    :LongName
    "Visible Infrared Imaging Radiometer suite."

    "ProcessingLevel field"
    :ProcessingLevel
    "4"

    "ShortName field"
    :ShortName
    "VIIRS"

    "Version field"
    :Version
    "001"

    "VersionDescription field"
    :VersionDescription
    "The beginning version of a sample collection."

    "AdditionalAttributes field"
    :AdditionalAttributes
    ["ALBEDOFILEID" "ID of the kernel albedo table used."
     "ASTERMapProjection" "The map projection of the granule"]

    "AncillaryKeywords field"
    :AncillaryKeywords
    ["LP DAAC" "EOSDIS" "USGS/EROS" "ESIP" "USGS" "LPDAAC"]

    "CollectionCitations field"
    :CollectionCitations
    ["Bowen Island Forest and Water Management Society (BIFWMS)"
     "U.S. Geological Survey, 1993, Digital Elevation Models--data users guide 5:[Reston, Virginia], U.S. Geological Survey, 48 p."
     "Solanki, S.K., I.G. Usoskin, B. Kromer, M. Schussler and J. Beer"
     "DOC/NOAA/NESDIS/NGDC > National Geophysical Data Center, NESDIS, NOAA, U.S. Department of Commerce"
     "Dublin Transport Office"
     "Full report in PDF is available online"]

    "CollectionDataType field"
    :CollectionDataType
    ["near_real_time" "nrt" "near real time","near-real time" "near-real-time" "near real-time"]

    "ContactGroups field"
    :ContactGroups
    ["TEAM SPOCK" "SCIENCE CONTACT"]

    "ContactMechanisms field"
    :ContactMechanisms
    ["ncdc.orders at noaa.gov" "custserv at usgs.gov" "sample-email-one@anywhere.com" "sample-email-two@example.com"]

    "ContactPersons field"
    :ContactPersons
    ["Alice" "Bob" "AUTHOR"]

    "DataCenters field"
    :DataCenters
    ["John" "Doe" "Technical Contact" "Science Contact" "White Marsh Institute of Health"
     "Data Center Contact" "Technical Contact" "IRIS/PASSCAL"]

    "ISOTopicCategories field"
    :ISOTopicCategories
    ["elevation" "GEOSCIENTIFIC INFORMATION" "OCEANS"]

    "LocationKeywords field"
    :LocationKeywords
    ["CONTINENT" "NORTH AMERICA" "UNITED STATES OF AMERICA" "MICHIGAN" "DETROIT" "MOUNTAIN"
     "OCEAN" "ATLANTIC OCEAN" "NORTH ATLANTIC OCEAN" "GULF OF MEXICO" "WATER"]

    "CollectionPlatforms field"
    :CollectionPlatforms
    ["characteristic-name-one" "characteristic-description-one" "256" "characteristic-name-two"
     "characteristic-description-two" "1024.5" "LIDAR" "WCMS" "SURFACE WATER WIER"]

    "Projects field"
    :Projects
    ["Earth Observing System Data Information System" "EOSDIS"
     "Global Terrestrial Observing System" "GTOS"
     "Environmental Sustainability Index" "ESI"]

    "RelatedUrls field"
    :RelatedUrls
    ["Related-url description." "EDG" "GET SERVICE" "related-url-example.com" "PublicationURL"
     "A test related url." "GENERAL DOCUMENTATION" "HOME PAGE" "related-url-example-two.com"
     "DataCenterURL"]

    "ScienceKeywords field"
    :ScienceKeywords
    ["EARTH SCIENCE SERVICES" nil "GEOGRAPHIC INFORMATION SYSTEMS" "DATA ANALYSIS AND VISUALIZATION"
     nil nil nil "ATMOSPHERE" "RADAR" "SURFACE WINDS" "ATMOSPHERIC WINDS" "SPECTRAL/ENGINEERING"
     "MICROWAVE" "MICROWAVE IMAGERY" "SCIENCE CAT 3" nil "SCIENCE TERM 3" "SCIENCE TOPIC 3" nil nil nil]

    "TilingIdentificationSystems field"
    :TilingIdentificationSystems
    ["MISR" "CALIPSO"]

    "TemporalKeywords field"
    :TemporalKeywords
    ["Composit" "Annual" "Day"]

    "Test getting the formats out of Archive and Distribution Information. The
     ArchiveFileInformation is nil, so it is testing that too."
    :ArchiveAndDistributionInformation
    ["netCDF4", "PDF"]))

(deftest extract-service-field-values
  (are3 [field-key values]
    (is (= values
           ((#'keyword-util/field-extract-fn field-key) sample-umm-service-concept)))

    "LongName field"
    :LongName
    "OPeNDAP Service for AIRS Level-3 retrieval products"

    "Name field"
    :Name
    "AIRX3STD"

    "Version field"
    :Version
    "1.9"

    "AncillaryKeywords field"
    :AncillaryKeywords
    ["Data Visualization" "Data Discovery"]

    "ContactGroups field"
    :ContactGroups
    ["TEAM SPOCK" "SCIENCE CONTACT"]

    "ContactPersons field"
    :ContactPersons
    ["Alice" "Bob" "AUTHOR"]

    "Platforms field"
    :Platforms
    ["Airbus A340-600" "A340-600" "Senso-matic Wonder Eye 4B" "SMWE4B"]

    "RelatedURLs field"
    :RelatedURLs
    ["OPeNDAP Service" "ACCESS WEB SERVICE" "GET SERVICE" "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/" "CollectionURL"]

    "ScienceKeywords field"
    :ScienceKeywords
    ["EARTH SCIENCE SERVICES" nil "GEOGRAPHIC INFORMATION SYSTEMS" "DATA ANALYSIS AND VISUALIZATION"
     nil nil nil "ATMOSPHERE" "RADAR" "SURFACE WINDS" "ATMOSPHERIC WINDS" "SPECTRAL/ENGINEERING"
     "MICROWAVE" "MICROWAVE IMAGERY" "SCIENCE CAT 3" nil "SCIENCE TERM 3" "SCIENCE TOPIC 3" nil nil nil]

    "ServiceKeywords field"
    :ServiceKeywords
    ["DATA ANALYSIS AND VISUALIZATION" nil nil "VISUALIZATION/IMAGE PROCESSING"
     "DATA ANALYSIS AND VISUALIZATION" nil nil nil nil nil nil "STATISTICAL APPLICATIONS"]

    "ServiceOrganizations field"
    :ServiceOrganizations
    [nil "LDPAAC" "SERVICE PROVIDER" "US GEOLOGICAL SURVEY EARTH RESOURCE OBSERVATION AND SCIENCE (EROS) LANDSAT CUSTOMER SERVICES"
     "USGS/EROS" "Carol" "Eve" "PUBLISHER" "SERVICE PROVIDER"]))

(deftest concept-key->keywords
  (is (= ["OPeNDAP Service for AIRS Level-3 retrieval products"]
         (sort
          (keyword-util/concept-key->keywords
           sample-umm-service-concept :LongName))))
  (is (= ["ACCESS WEB SERVICE" "CollectionURL" "GET SERVICE" "OPeNDAP Service" "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"]
         (sort
          (keyword-util/concept-key->keywords
           sample-umm-service-concept :RelatedURLs))))
  (is (= ["ATMOSPHERE" "ATMOSPHERIC WINDS" "DATA ANALYSIS AND VISUALIZATION" "EARTH SCIENCE SERVICES" "GEOGRAPHIC INFORMATION SYSTEMS" "MICROWAVE" "MICROWAVE IMAGERY" "RADAR" "SCIENCE CAT 3" "SCIENCE TERM 3" "SCIENCE TOPIC 3" "SPECTRAL/ENGINEERING" "SURFACE WINDS"]
         (sort
          (keyword-util/concept-key->keywords
           sample-umm-service-concept :ScienceKeywords)))))

(deftest concept-key->keyword-text
  (is (= (str "006 access access web service acdisc airs airx3std aqua "
              "collectionurl eosdis gesdisc get get service gov https "
              "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/aqua_airs_level3/airx3std.006/ "
              "level3 nasa opendap opendap service service web")
        (keyword-util/concept-key->keyword-text
         sample-umm-service-concept :RelatedURLs)))
  (is (= (str "3 analysis and atmosphere atmospheric atmospheric winds cat "
              "data data analysis and visualization earth earth science "
              "services engineering geographic geographic information systems "
              "imagery information microwave microwave imagery radar science "
              "science cat 3 science term 3 science topic 3 services spectral "
              "spectral/engineering surface surface winds systems term topic "
              "visualization winds")
        (keyword-util/concept-key->keyword-text
         sample-umm-service-concept :ScienceKeywords))))

(deftest concept-keys->keywords
  (let [schema-keys [:LongName
                     :Name
                     :Version]]
    (is (= ["1.9" "AIRX3STD" "OPeNDAP Service for AIRS Level-3 retrieval products"]
           (sort
            (keyword-util/concept-keys->keywords
             sample-umm-service-concept schema-keys)))))
  (let [schema-keys [:LongName
                     :Name
                     :Version
                     :AncillaryKeywords
                     :ContactGroups
                     :ContactPersons
                     :Platforms
                     :RelatedURLs
                     :ScienceKeywords
                     :ServiceKeywords
                     :ServiceOrganizations]]
    (is (= ["1.9" "A340-600" "ACCESS WEB SERVICE" "AIRX3STD" "ATMOSPHERE" "ATMOSPHERIC WINDS" "AUTHOR" "Airbus A340-600" "Alice" "Bob" "Carol" "CollectionURL" "DATA ANALYSIS AND VISUALIZATION" "DATA ANALYSIS AND VISUALIZATION" "DATA ANALYSIS AND VISUALIZATION" "Data Discovery" "Data Visualization" "EARTH SCIENCE SERVICES" "Eve" "GEOGRAPHIC INFORMATION SYSTEMS" "GET SERVICE" "LDPAAC" "MICROWAVE" "MICROWAVE IMAGERY" "OPeNDAP Service" "OPeNDAP Service for AIRS Level-3 retrieval products" "PUBLISHER" "RADAR" "SCIENCE CAT 3" "SCIENCE CONTACT" "SCIENCE TERM 3" "SCIENCE TOPIC 3" "SERVICE PROVIDER" "SERVICE PROVIDER" "SMWE4B" "SPECTRAL/ENGINEERING" "STATISTICAL APPLICATIONS" "SURFACE WINDS" "Senso-matic Wonder Eye 4B" "TEAM SPOCK" "US GEOLOGICAL SURVEY EARTH RESOURCE OBSERVATION AND SCIENCE (EROS) LANDSAT CUSTOMER SERVICES" "USGS/EROS" "VISUALIZATION/IMAGE PROCESSING" "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"]
           (sort
            (keyword-util/concept-keys->keywords
             sample-umm-service-concept schema-keys))))))

(deftest concept-keys->keyword-text
  (let [schema-keys [:LongName
                     :Name
                     :Version]]
    (is (= "1 1.9 3 9 airs airx3std for level opendap opendap service for airs level-3 retrieval products products retrieval service"
           (keyword-util/concept-keys->keyword-text
            sample-umm-service-concept schema-keys))))
  (let [schema-keys [:Name
                     :ContactGroups]]
    (is (= "airx3std contact science science contact spock team team spock"
           (keyword-util/concept-keys->keyword-text
            sample-umm-service-concept schema-keys))))
  (let [schema-keys [:ContactPersons]]
    (is (= "alice author bob"
           (keyword-util/concept-keys->keyword-text
            sample-umm-service-concept schema-keys))))
  (let [schema-keys [:Platforms]]
    (is (= "4b 600 a340 a340-600 airbus airbus a340-600 eye matic senso senso-matic wonder eye 4b smwe4b wonder"
           (keyword-util/concept-keys->keyword-text
            sample-umm-service-concept schema-keys))))
  (let [schema-keys [:ServiceOrganizations]]
    (is (= "and carol customer earth eros eve geological landsat ldpaac observation provider publisher resource science service service provider services survey us us geological survey earth resource observation and science (eros) landsat customer services usgs usgs/eros"
           (keyword-util/concept-keys->keyword-text
            sample-umm-service-concept schema-keys))))
  (let [schema-keys [:LongName
                     :Name
                     :Version
                     :AncillaryKeywords
                     :ContactGroups
                     :ContactPersons
                     :Platforms
                     :RelatedURLs
                     :ScienceKeywords
                     :ServiceKeywords
                     :ServiceOrganizations]]
    (is (= "006 1 1.9 3 4b 600 9 a340 a340-600 access access web service acdisc airbus airbus a340-600 airs airx3std alice analysis and applications aqua atmosphere atmospheric atmospheric winds author bob carol cat collectionurl contact customer data data analysis and visualization data discovery data visualization discovery earth earth science services engineering eosdis eros eve eye for geographic geographic information systems geological gesdisc get get service gov https https://acdisc.gesdisc.eosdis.nasa.gov/opendap/aqua_airs_level3/airx3std.006/ image imagery information landsat ldpaac level level3 matic microwave microwave imagery nasa observation opendap opendap service opendap service for airs level-3 retrieval products processing products provider publisher radar resource retrieval science science cat 3 science contact science term 3 science topic 3 senso senso-matic wonder eye 4b service service provider services smwe4b spectral spectral/engineering spock statistical statistical applications surface surface winds survey systems team team spock term topic us us geological survey earth resource observation and science (eros) landsat customer services usgs usgs/eros visualization visualization/image processing web winds wonder"
           (keyword-util/concept-keys->keyword-text
            sample-umm-service-concept schema-keys)))))
