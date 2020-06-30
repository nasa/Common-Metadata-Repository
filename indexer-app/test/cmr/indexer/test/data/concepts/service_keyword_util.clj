(ns cmr.indexer.test.data.concepts.service-keyword-util
  "Functions for testing cmr.indexer.data.concepts.keyword-util namespace."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.indexer.data.concepts.service-keyword-util :as service-keyword-util]))

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
   :URL {:URLValue "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
         :Description "OPeNDAP Service"}
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
   :AncillaryKeywords ["Data Visualization" "Data Discovery"]
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
                           :LongName "US GEOLOGICAL SURVEY EARTH RESOURCE OBSERVATION AND SCIENCE (EROS) LANDSAT CUSTOMER SERVICES"}]})

(deftest extract-service-field-values
  (are3 [field-key values]
    (is (= values
           ((#'service-keyword-util/service-fields->fn-mapper field-key field-key) sample-umm-service-concept)))

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

    "URL field"
    :URL
    ["OPeNDAP Service" "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"]

    "ServiceKeywords field"
    :ServiceKeywords
    ["DATA ANALYSIS AND VISUALIZATION" nil nil "VISUALIZATION/IMAGE PROCESSING"
     "DATA ANALYSIS AND VISUALIZATION" nil nil nil nil nil nil "STATISTICAL APPLICATIONS"]

    "ServiceOrganizations field"
    :ServiceOrganizations
    [nil "LDPAAC" "SERVICE PROVIDER" "US GEOLOGICAL SURVEY EARTH RESOURCE OBSERVATION AND SCIENCE (EROS) LANDSAT CUSTOMER SERVICES"
     "USGS/EROS" "SERVICE PROVIDER"]))

(deftest concept-key->keywords
  (is (= ["OPeNDAP Service for AIRS Level-3 retrieval products"]
         (sort
          (service-keyword-util/concept-key->keywords
           sample-umm-service-concept :LongName))))
  (is (= ["OPeNDAP Service" "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"]
         (sort
          (service-keyword-util/concept-key->keywords
           sample-umm-service-concept :URL)))))

(deftest concept-keys->keywords
  (let [schema-keys [:LongName
                     :Name
                     :Version]]
    (is (= ["1.9" "AIRX3STD" "OPeNDAP Service for AIRS Level-3 retrieval products"]
           (sort
            (service-keyword-util/concept-keys->keywords
             sample-umm-service-concept schema-keys)))))
  (let [schema-keys [:LongName
                     :Name
                     :Version
                     :AncillaryKeywords
                     :ContactGroups
                     :ContactPersons
                     :URL
                     :ServiceKeywords
                     :ServiceOrganizations]]
    (is (= ["1.9" "AIRX3STD" "AUTHOR" "Alice" "Bob" "DATA ANALYSIS AND VISUALIZATION" "DATA ANALYSIS AND VISUALIZATION" "Data Discovery" "Data Visualization" "LDPAAC" "OPeNDAP Service" "OPeNDAP Service for AIRS Level-3 retrieval products" "SCIENCE CONTACT" "SERVICE PROVIDER" "SERVICE PROVIDER" "STATISTICAL APPLICATIONS" "TEAM SPOCK" "US GEOLOGICAL SURVEY EARTH RESOURCE OBSERVATION AND SCIENCE (EROS) LANDSAT CUSTOMER SERVICES" "USGS/EROS" "VISUALIZATION/IMAGE PROCESSING" "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"]
           (sort
            (service-keyword-util/concept-keys->keywords
             sample-umm-service-concept schema-keys))))))

(deftest concept-keys->keywords-text
  (let [schema-keys [:LongName
                     :Name
                     :Version]]
    (is (= "1 1.9 3 9 airs airx3std for level opendap opendap service for airs level-3 retrieval products products retrieval service"
           (service-keyword-util/concept-keys->keyword-text
            sample-umm-service-concept schema-keys))))
  (let [schema-keys [:Name
                     :ContactGroups]]
    (is (= "airx3std contact science science contact spock team team spock"
           (service-keyword-util/concept-keys->keyword-text
            sample-umm-service-concept schema-keys))))
  (let [schema-keys [:ContactPersons]]
    (is (= "alice author bob"
           (service-keyword-util/concept-keys->keyword-text
            sample-umm-service-concept schema-keys))))
  (let [schema-keys [:ServiceOrganizations]]
    (is (= "and customer earth eros geological landsat ldpaac observation provider resource science service service provider services survey us us geological survey earth resource observation and science (eros) landsat customer services usgs usgs/eros"
           (service-keyword-util/concept-keys->keyword-text
            sample-umm-service-concept schema-keys))))
  (let [schema-keys [:LongName
                     :Name
                     :Version
                     :AncillaryKeywords
                     :ContactGroups
                     :ContactPersons
                     :URL
                     :ServiceKeywords
                     :ServiceOrganizations]]
    (is (= "006 1 1.9 3 9 acdisc airs airx3std alice analysis and applications aqua author bob contact customer data data analysis and visualization data discovery data visualization discovery earth eosdis eros for geological gesdisc gov https https://acdisc.gesdisc.eosdis.nasa.gov/opendap/aqua_airs_level3/airx3std.006/ image landsat ldpaac level level3 nasa observation opendap opendap service opendap service for airs level-3 retrieval products processing products provider resource retrieval science science contact service service provider services spock statistical statistical applications survey team team spock us us geological survey earth resource observation and science (eros) landsat customer services usgs usgs/eros visualization visualization/image processing"
           (service-keyword-util/concept-keys->keyword-text
            sample-umm-service-concept schema-keys)))))
