(ns cmr.umm-spec.umm-to-xml-mappings.serf
  "Defines mappings from a UMM record into SERF XML"
  (:require [cmr.common.xml.gen :refer :all]
            [camel-snake-kebab.core :as csk]
            [cmr.umm-spec.xml-to-umm-mappings.serf :as utx]
            [cmr.umm-spec.util :refer [without-default-value-of not-provided]]
            [clojure.set :as set]
            [cmr.common.util :as util]
            [clojure.string :as str]
            [clojure.string :as str]
            [clj-time.format :as time-format]))

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
    [:Related_URL
     (when-let [[type subtype] (:Relation related-url)]
       [:URL_Content_Type
        [:Type type]
        [:Subtype subtype]])
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
     [:URL (first (:URLs (:RelatedUrl service-citation)))]]))

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
        :let [[node-short-name node-long-name] (str/split (:Value idn-node) #"\|")]]
    [:IDN_Node [:Short_Name (or node-short-name not-provided)]
     [:Long_Name node-long-name]]))

(def inserted-metadata
  "Inserted Metadata by CMR to account for missing fields"
  #{"Metadata_Name" "Metadata_Version" "IDN_Node"})

(defn- create-publication-references
  "Creates a SERF Publication_References element from a UMM-S PublicationReferences object"
  [pub-refs]
  (for [pub-ref pub-refs]
    [:Reference
     [:Author (:Author pub-ref)]
     [:Publication_Date (:PublicationDate pub-ref)]
     [:Title (:Title pub-ref)]
     [:Series (:Series pub-ref)]
     [:Edition (:Edition pub-ref)]
     [:Volume (:Volume pub-ref)]
     [:Issue (:Issue pub-ref)]
     [:Report_Number (:ReportNumber pub-ref)]
     [:Publication_Place (:PublicationPlace pub-ref)]
     [:Publisher (:Publisher pub-ref)]
     [:Pages (:Pages pub-ref)]
     [:ISBN (:ISBN pub-ref)]
     [:DOI (:DOI (:DOI pub-ref))]
     [:Online_Resource (-> pub-ref :OnlineResource :Linkage)]
     [:Other_Reference_Details (:OtherReferenceDetails pub-ref)]]))

(defn umm-s-to-serf-xml
  "Returns SERF XML structure from UMM collection record s."
  [s]
  (xml
    [:SERF
     serf-xml-namespaces
     [:Entry_ID (:EntryId s)]
     [:Entry_Title (:EntryTitle s)]
     (create-service-citations s)
     (create-service-parameters s)
     (create-science-parameters s)
     (for [topic-category (:ISOTopicCategories s)]
       [:ISO_Topic_Category topic-category])
     (for [ak (:AncillaryKeywords s)]
       [:Keyword ak])
     ;;Removing Instruments and Platforms conversion until we can get better parsing code
     ;;CMR-2369 will allow us to do round-tripping.
     ;;(create-sensors s)
     ;;(create-source-names s)
     (create-projects s)
     [:Quality (:Quality s)]
     [:Access_Constraints (:Description (:AccessConstraints s))]
     [:Use_Constraints (:UseConstraints s)]
     [:Service_Language (:ServiceLanguage s)]
     (create-distributions s)
     ;;Multimedia Samples should go here in order but we don't have a way to distinguish them from
     ;;Related URLs.
     (create-publication-references (:PublicationReferences s))
     [:Service_Provider
      [:Service_Organization
       [:Short_Name not-provided]]
      [:Personnel
       [:Role (get umm-roles->serf-roles "RESOURCEPROVIDER")]
       [:Last_Name not-provided]]]
     [:Summary
      [:Abstract (:Abstract s)]
      [:Purpose (:Purpose s)]]
     (create-related-urls s)
     [:Parent_SERF (:EntryId (first (:MetadataAssociations s)))]
     (create-idn-node s)
     [:Metadata_Name
      (or (:Value (first (filter #(= "Metadata_Name" (:Name %)) (:AdditionalAttributes s)))) not-provided)]
     [:Metadata_Version
      (or (:Value (first (filter #(= "Metadata_Version" (:Name %)) (:AdditionalAttributes s)))) not-provided)]
     [:SERF_Creation_Date
      (:Date (first (filter #(= "CREATE" (:Type %)) (:MetadataDates s))))]
     [:Last_SERF_Revision_Date (:Date (first (filter #(= "UPDATE" (:Type %)) (:MetadataDates s))))]
     [:Future_SERF_Review_Date (:Date (first (filter #(= "REVIEW" (:Type %)) (:MetadataDates s))))]
     [:Extended_Metadata
      (for [metadata  (:AdditionalAttributes s)
            :when (not (inserted-metadata (:Name metadata)))]
        [:Metadata [:Group (:Group metadata)]
         [:Name (or (:Name metadata) not-provided)]
         [:Description (:Description metadata)]
         [:Type (:DataType metadata)]
         [:Update_Date (when-let [date (:UpdateDate metadata)]
                         (time-format/unparse (time-format/formatters :date) date))]
         [:Value (:Value metadata)]])]]))
