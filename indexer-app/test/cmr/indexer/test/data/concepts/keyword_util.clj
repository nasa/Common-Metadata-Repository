(ns cmr.indexer.test.data.concepts.keyword-util
  "Functions for testing cmr.indexer.test.data.concepts.keyword-util namespace."
  (:require
   [clojure.test :refer :all]
   [cmr.indexer.data.concepts.keyword-util :as keyword-util]))

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
   :RelatedURL {
     :URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
     :Description "OPeNDAP Service"
     :Type "GET SERVICE"
     :Subtype "ACCESS WEB SERVICE"
     :URLContentType "CollectionURL"}
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

(deftest fields->fn-mapper-single-valued
  (is (= "OPeNDAP Service for AIRS Level-3 retrieval products"
         ((:LongName keyword-util/fields->fn-mapper) sample-umm-service-concept)))
  (is (= "AIRX3STD"
         ((:Name keyword-util/fields->fn-mapper) sample-umm-service-concept)))
  (is (= "1.9"
         ((:Version keyword-util/fields->fn-mapper) sample-umm-service-concept))))

(deftest fields->fn-mapper-multi-valued
  (is (= ["Data Visualization" "Data Discovery"]
         ((:AncillaryKeywords keyword-util/fields->fn-mapper) sample-umm-service-concept)))
  (is (= ["TEAM SPOCK" "SCIENCE CONTACT"]
         ((:ContactGroups keyword-util/fields->fn-mapper) sample-umm-service-concept)))
  (is (= ["Alice" "Bob" "AUTHOR"]
         ((:ContactPersons keyword-util/fields->fn-mapper) sample-umm-service-concept)))
  (is (= ["Airbus A340-600" "A340-600" "Senso-matic Wonder Eye 4B" "SMWE4B"]
         ((:Platforms keyword-util/fields->fn-mapper) sample-umm-service-concept)))
  (is (= ["OPeNDAP Service" "ACCESS WEB SERVICE" "GET SERVICE" "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/" "CollectionURL"]
         ((:RelatedURL keyword-util/fields->fn-mapper) sample-umm-service-concept)))
  (is (= ["EARTH SCIENCE SERVICES" nil "GEOGRAPHIC INFORMATION SYSTEMS" "DATA ANALYSIS AND VISUALIZATION" nil nil nil "ATMOSPHERE" "RADAR" "SURFACE WINDS" "ATMOSPHERIC WINDS" "SPECTRAL/ENGINEERING" "MICROWAVE" "MICROWAVE IMAGERY" "SCIENCE CAT 3" nil "SCIENCE TERM 3" "SCIENCE TOPIC 3" nil nil nil]
         ((:ScienceKeywords keyword-util/fields->fn-mapper) sample-umm-service-concept)))
  (is (= ["DATA ANALYSIS AND VISUALIZATION" nil nil "VISUALIZATION/IMAGE PROCESSING" "DATA ANALYSIS AND VISUALIZATION" nil nil nil nil nil nil "STATISTICAL APPLICATIONS"]
         ((:ServiceKeywords keyword-util/fields->fn-mapper) sample-umm-service-concept)))
  (is (= [nil "LDPAAC" "SERVICE PROVIDER" "US GEOLOGICAL SURVEY EARTH RESOURCE OBSERVATION AND SCIENCE (EROS) LANDSAT CUSTOMER SERVICES" "USGS/EROS" "Carol" "Eve" "PUBLISHER" "SERVICE PROVIDER"]
         ((:ServiceOrganizations keyword-util/fields->fn-mapper) sample-umm-service-concept))))

(deftest concept-key->keywords
  (is (= ["OPeNDAP Service for AIRS Level-3 retrieval products"]
         (sort
          (keyword-util/concept-key->keywords
           sample-umm-service-concept :LongName))))
  (is (= ["ACCESS WEB SERVICE" "CollectionURL" "GET SERVICE" "OPeNDAP Service" "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"]
         (sort
          (keyword-util/concept-key->keywords
           sample-umm-service-concept :RelatedURL))))
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
         sample-umm-service-concept :RelatedURL)))
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
                     :RelatedURL
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
                     :RelatedURL
                     :ScienceKeywords
                     :ServiceKeywords
                     :ServiceOrganizations]]
    (is (= "006 1 1.9 3 4b 600 9 a340 a340-600 access access web service acdisc airbus airbus a340-600 airs airx3std alice analysis and applications aqua atmosphere atmospheric atmospheric winds author bob carol cat collectionurl contact customer data data analysis and visualization data discovery data visualization discovery earth earth science services engineering eosdis eros eve eye for geographic geographic information systems geological gesdisc get get service gov https https://acdisc.gesdisc.eosdis.nasa.gov/opendap/aqua_airs_level3/airx3std.006/ image imagery information landsat ldpaac level level3 matic microwave microwave imagery nasa observation opendap opendap service opendap service for airs level-3 retrieval products processing products provider publisher radar resource retrieval science science cat 3 science contact science term 3 science topic 3 senso senso-matic wonder eye 4b service service provider services smwe4b spectral spectral/engineering spock statistical statistical applications surface surface winds survey systems team team spock term topic us us geological survey earth resource observation and science (eros) landsat customer services usgs usgs/eros visualization visualization/image processing web winds wonder"
           (keyword-util/concept-keys->keyword-text
            sample-umm-service-concept schema-keys)))))
