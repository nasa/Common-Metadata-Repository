(ns cmr.umm-spec.umm-to-xml-mappings.serf
  "Defines mappings from a UMM record into SERF XML"
  (:require [cmr.umm-spec.xml.gen :refer :all]
            [camel-snake-kebab.core :as csk]
            [cmr.umm-spec.xml-to-umm-mappings.serf :as utx]
            [clojure.set :as set]))

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
    [:Parameters
     [:Service_Category (:Category sk)]
     [:Service_Topic (:Topic sk)]
     [:Service_Term (:Term sk)]
     [:Service_Specific_Name (:ServiceSpecificName sk)]]))

(defn- create-science-parameters
  "Creates a SERF Science Parameter representation of a UMM-S Science Keyword element"
  [s]
  (for [sk (:ScienceKeywords s)]
    [:Parameters
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
  (get (cmr.common.util/map-values #(map :Value %) (group-by :Type contacts)) type))

(defn- contact-to-serf
  "Converts a UMM-S Contact to the appropriate SERF Personnel elements as a vector"
  [contacts]
  (for [type ["phone" "email" "fax"]
        value (extract-contacts-by-type contacts type)]
    [(keyword (clojure.string/capitalize type) ) value]))

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
      (let [organization (:OrganizationName party)]
        [:Short_Name (:ShortName organization)]
        [:Long_Name (:LongName organization)])
      [:Service_Organization_Url (extract-service-organization-url (:RelatedUrls party))] 
      [:Personnel (create-service-provider-personnel responsibilities)]]]))

(def inserted-metadata
  "Inserted Metadata by CMR to account for missing fields"
  #{"Metadata_Name" "Metadata_Version" "IDN_Node_Short_Name"})

(defn umm-s-to-serf-xml
  "Returns SERF XML structure from UMM collection record s."
  [s]
  (xml
    [:SERF
     serf-xml-namespaces
     [:Entry_ID (:EntryId s)]
     [:Entry_Title (:EntryTitle s)]
     [:Service_Citation (:ServiceCitation s)]
     ;;TODO: CMR-2298 needs to be resolved before we can properly implement this
     [:Personnel (create-root-personnel (:Responsibilities s))] 
     [:Service_Parameter (create-service-parameters s)]
     [:Science_Parameters (create-science-parameters s)]
     (for [topic-category (:ISOTopicCategories s)]
       [:ISO_Topic_Category topic-category])
     (for [ak (:AncillaryKeywords s)]
       [:Keyword ak])
     (for [platform (:Platforms s) 
           :when (not= (:ShortName platform) "Not provided")]
       [:Source_Name
        [:Short_Name (:ShortName platform)]
        [:Long_Name (:LongName platform)]])
     (for [platform (:Platforms s)
           instrument (:Instruments platform)]
       [:Sensor_Name
        [:Short_Name (:ShortName instrument)]
        [:Long_Name (:LongName instrument)]])
     (for [service-project (:Projects s)]
       [:Project service-project])
     [:Quality (:Quality s)]
     [:AccessConstraints (:AccessContstraints s)]
     [:UseConstraints (:UseConstraints s)]
     [:Service_Language (:ServiceLanguage s)]
     (for [distribution (:Distributions s)]
       [:Distribution distribution])
     ;;Multimedia Samples should go here in order but we don't have a way to distinguish them from 
     ;;Related URLs. 
     (for [reference (:PublicationReferences s)]
       [:Reference reference])
     (create-service-provider (:Responsibilities s))
     [:Summary 
      [:Abstract (:Abstract s)] 
      [:Purpose (:Purpose s)]]
     (for [related-url (:RelatedUrls s)] 
       [:Related_URL related-url])
     (for [ma (:MetadataAssociations s)]
       [:Parent_SERF 
        [:Entry_Id [:Short_Name (:EntryId ma)]
         [:Version (:Version ma)]]
        [:Description (:Description ma)]
        [:Type (:Type ma)]])
     [:IDN_Node 
      [:Short_Name  
       (:Value (first (filter #(= "IDN_Node_Short_Name" (:Name %)) (:AdditionalAttributes s))))]]
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