(ns cmr.umm-spec.umm-to-xml-mappings.serf
  "Defines mappings from a UMM record into SERF XML"
  (:require [cmr.umm-spec.xml.gen :refer :all]
            [camel-snake-kebab.core :as csk]
            [cmr.umm-spec.xml-to-umm-mappings.serf :as utx]
            [cmr.umm-spec.util :refer [without-default-value-of not-provided]]
            [clojure.set :as set]
            [cmr.common.util :as util]
            [clojure.string :as str]
            [clojure.string :as str]))

(def serf-xml-namespaces
  "Contains a map of the SERF namespaces used when generating SERF XML"
  {:xmlns "http://gcmd.gsfc.nasa.gov/Aboutus/xml/serf/"
   :xmlns:serf "http://gcmd.gsfc.nasa.gov/Aboutus/xml/serf/"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
   :xsi:schemaLocation "http://gcmd.gsfc.nasa.gov/Aboutus/xml/serf/ http://gcmd.gsfc.nasa.gov/Aboutus/xml/serf/serf_v9.9.3.xsd"})

(def umm-roles->serf-roles 
  "Maps UMM Roles to SERF roles"
  (set/map-invert utx/serf-roles->umm-roles))

(defn- create-service-parameters
  "Creates a SERF Service Parameter representation of a UMM-S Service Keyword element"
  [s]
  (for [sk (:ServiceKeywords s)]
    [:Service_Parameters 
     [:Service_Category (:Category sk)]
     [:Service_Topic (:Topic sk)]
     [:Service_Term (:Term sk)]
     [:Service_Specific_Name (:ServiceSpecificName sk)]]))


(defn- create-science-parameters
  "Creates a SERF Science Parameter representation of a UMM-S Science Keyword element"
  [s]
  (for [sk (:ScienceKeywords s)]
    [:Science_Parameters
     [:Science_Category (:Category sk)]
     [:Science_Topic (:Topic sk)]
     [:Science_Term (:Term sk)]
     [:Science_Variable_Level_1 (:VariableLevel1 sk)]
     [:Science_Variable_Level_2 (:VariableLevel2 sk)]
     [:Science_Variable_Level_3 (:VariableLevel3 sk)]
     [:Science_Detailed_Variable (:DetailedVariable sk)]]))

(defn- extract-contacts-by-type
  "Returns a map of contact types to a list of the values."
  [contacts type]
  (get (util/map-values #(map :Value %) (group-by :Type contacts)) type))

(defn- contact-to-serf
  "Converts a UMM-S Contact to the appropriate SERF Personnel elements as a vector"
  [contacts]
  (for [type ["email" "phone" "fax"]
        value (extract-contacts-by-type contacts type)]
    [(keyword (str/capitalize type) ) value]))

(defn- address-to-serf 
  "Converts a UMM-S Addresses to the appropriate SERF Personnel elements as a vector.
  Only takes the first address as required by the schema." 
  [addresses]
  (let [address (first addresses)]
    [:Contact_Address 
     [:Address (concat (:StreetAddresses address))]
     [:City (:City address)]
     [:Province_or_State (:StateProvince address)]
     [:Postal_Code (:PostalCode address)]
     [:Country (:Country address)]]))

(defn- create-root-personnel
  "Converts a UMM-S Responsibility to a SERF Personnel element"
  [responsibilities] 
  (for [responsibility responsibilities
        :let [{{:keys [Contacts Addresses Person]} :Party} responsibility]
        :when (not= (:Role responsibility) "RESOURCEPROVIDER")]
    [:Personnel 
     [:Role (get umm-roles->serf-roles (:Role responsibility))]
     [:First_Name (:FirstName Person)]
     [:Middle_Name (:MiddleName Person)]
     [:Last_Name (:LastName Person)]
     (contact-to-serf Contacts)
     (address-to-serf Addresses)]))

(defn- create-service-provider-personnel
  "Converts a UMM-S Responsibility to a SERF Personnel element"
  [responsibilities] 
  (for [responsibility responsibilities
        :let [{{:keys [Contacts Addresses Person]} :Party} responsibility]
        :when (= (:Role responsibility) "RESOURCEPROVIDER")]
    [:Personnel 
     [:Role (get umm-roles->serf-roles (:Role responsibility))]
     [:First_Name (:FirstName Person)]
     [:Middle_Name (:MiddleName Person)]
     [:Last_Name (:LastName Person)]
     (contact-to-serf Contacts)
     (address-to-serf Addresses)]))

(defn- extract-service-organization-url
  "Extracts a SERF Service_Organization_URL from a UMM-S Party RelatedURL Element if one exists."
  [related-urls]
  (first (:URLs (first (filter #(="SERVICE_ORGANIZATION_URL" (:Description %)) related-urls)))))

(defn- create-service-provider
  "Converts a UMM-S Responsibilities element to a SERF Service Provider element" 
  [responsibilities]
  (let [responsibility (first (filter #(= "RESOURCEPROVIDER" (:Role %)) responsibilities))
        party (:Party responsibility)]
    [:Service_Provider 
     [:Service_Organization 
      [:Short_Name (:ShortName (:OrganizationName party))]
      [:Long_Name (:LongName (:OrganizationName party))]]
     [:Service_Organization_URL (extract-service-organization-url (:RelatedUrls party))] 
     (create-service-provider-personnel responsibilities)]))

(defn- create-distributions
  "Creates a SERF Distributions element from a UMM-S Distributions Element"
  [s]
  (for [distribution (:Distributions s)]
    [:Distribution [:Distribution_Media (:DistributionMedia distribution)]
     [:Distribution_Size (:DistributionSize distribution)]
     [:Distribution_Format (:DistributionFormat distribution)]
     [:Fees (:Fees distribution)]]))

(defn- create-related-urls
  "Creates a SERF Related URL element from a UMM-S Document"
  [s]
  (for [related-url (:RelatedUrls s)] 
    [:Related_URL [:URL_Content_Type 
                   [:Type (:Type (:ContentType related-url))]
                   [:Subtype (:Subtype (:ContentType related-url))]]
     (for [url (:URLs related-url)]
       [:URL url])
     [:Description (:Description related-url)]]))

(defn- create-service-citations
  "Creates a SERF Service Citation element from a UMM-S Service Citation" 
  [s]
  (for [service-citation (:ServiceCitation s)]
    [:Service_Citation 
     [:Originators (:Creator service-citation)]
     [:Title (:Title service-citation)]
     [:Provider (:Publisher service-citation)]
     [:Edition (:Version service-citation)]
     [:URL (:RelatedURL service-citation)]]))

(defn- create-sensors
  "Creates SERF Sensor Elements from a UMM-S Instruments mapping"
  [s]
  (for [platform (:Platforms s)
        instrument (:Instruments platform)]
    [:Sensor_Name
     [:Short_Name (:ShortName instrument)]
     [:Long_Name (:LongName instrument)]]))

(defn- create-source-names
  "Creates SERF Source Name elements from a UMM-S Platforms mapping"
  [s]
  (for [platform (:Platforms s) 
           :when (not= (:ShortName platform) not-provided)]
       [:Source_Name
        [:Short_Name (:ShortName platform)]
        [:Long_Name (:LongName platform)]]))

(defn- create-projects 
  "Creates SERF Project elements from a UMM-S Projects mapping"
  [s]
  (for [service-project (:Projects s)]
    [:Project 
     [:Short_Name (:ShortName service-project)]
     [:Long_Name (:LongName service-project)]]))

(defn- create-idn-node 
  "Creates a SERF IDN_Node element from a UMM-S AdditionalAttributes mapping"
  [s]
  (for [idn-node (filter #(= "IDN_Node" (:Name %)) (:AdditionalAttributes s))
        :let [[:node-short-name :node-long-name] (str/split (:Value idn-node) #"\|")]]
    [:IDN_Node [:Short_Name node-short-name]
     [:Long_Name node-long-name]]))

(def inserted-metadata
  "Inserted Metadata by CMR to account for missing fields"
  #{"Metadata_Name" "Metadata_Version" "IDN_Node"})

(defn umm-s-to-serf-xml
  "Returns SERF XML structure from UMM collection record s."
  [s]
  (xml
    [:SERF
     serf-xml-namespaces
     [:Entry_ID (:EntryId s)]
     [:Entry_Title (:EntryTitle s)]
     (create-service-citations s)
     ;;TODO: CMR-2298 needs to be resolved before we can properly implement this
     (create-root-personnel (:Responsibilities s))
     (create-service-parameters s)
     (create-science-parameters s)
     (for [topic-category (:ISOTopicCategories s)]
       [:ISO_Topic_Category topic-category])
     (for [ak (:AncillaryKeywords s)]
       [:Keyword ak])
     (create-sensors s)
     (create-source-names s)
     (create-projects s)
     [:Quality (:Quality s)]
     [:AccessConstraints (:AccessContstraints s)]
     [:UseConstraints (:UseConstraints s)]
     [:Service_Language (:ServiceLanguage s)]
     (create-distributions s)
     ;;Multimedia Samples should go here in order but we don't have a way to distinguish them from 
     ;;Related URLs. 
     (for [reference (:PublicationReferences s)]
       [:Reference reference])
     (create-service-provider (:Responsibilities s))
     [:Summary 
      [:Abstract (:Abstract s)] 
      [:Purpose (:Purpose s)]]
     (create-related-urls s)
     (for [ma (:MetadataAssociations s)]
       [:Parent_SERF (:EntryId ma)])
     (create-idn-node s)
     [:Metadata_Name 
      (:Value (first (filter #(= "Metadata_Name" (:Name %)) (:AdditionalAttributes s))))]
     [:Metadata_Version 
      (:Value (first (filter #(= "Metadata_Version" (:Name %)) (:AdditionalAttributes s))))]
     [:SERF_Creation_Date 
      (:Date (first (filter #(= "CREATE" (:Type %)) (:MetadataDates s))))]
     [:Last_SERF_Revision_Date (:Date (first (filter #(= "UPDATE" (:Type %)) (:MetadataDates s))))]
     [:Extended_Metadata 
      (for [metadata  (:AdditionalAttributes s)
            :when (not (inserted-metadata (:Name metadata))) ]
        [:Metadata (elements-from metadata :Group :Name :Value )])]]))