(ns cmr.umm-spec.xml-to-umm-mappings.serf
  "Defines mappings from SERF XML into UMM records"
  (:require [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.simple-xpath :refer [select]]
            [camel-snake-kebab.core :as csk]
            [clojure.string :as string]
            [cmr.umm-spec.xml.parse :refer :all]
            [cmr.umm-spec.util :refer [without-default-value-of not-provided]]
            [cmr.umm-spec.date-util :as date]))

(defn- parse-short-name-long-name
  "Parses a common mapping for UMM-CMN data structures"
  [doc path]
  (seq (for [elem (select doc path)]
         {:ShortName (value-of elem "Short_Name")
          :LongName (value-of elem "Long_Name")})))

(defn- parse-instruments
  "Parses an Sensor_Name element of a SERF document and creates a UMM-S Instruments element"
  [doc]
  (parse-short-name-long-name doc "/SERF/Sensor_Name"))

(defn- parse-just-platforms
  "Parses a doc for a SERF representation of platforms only."
  [doc]
  (parse-short-name-long-name doc "/SERF/Source_Name"))

(defn- parse-platforms
  "Parses a SERF Platform element into a UMM-S Platform"
  [doc]
  (let [platforms (parse-just-platforms doc)
        instruments (parse-instruments doc)]
    (if (= 1 (count platforms))
      (map #(assoc % :Instruments instruments) platforms)
      (if instruments
        (conj platforms {:ShortName not-provided
                         :LongName not-provided
                         :Instruments instruments})
        platforms))))

(defn- parse-projects
  "Parses the Project elements of a SERF record and creates a UMM-S representation"
  [doc]
  (parse-short-name-long-name doc "/SERF/Project"))

(defn- parse-data-dates
  "Returns seq of UMM-CMN DataDates parsed from SERF document."
  [doc]
  (let [[md-dates-el] (select doc "/SERF")
        tag-types [["SERF_Creation_Date"      "CREATE"]
                   ["Last_SERF_Revision_Date" "UPDATE"]
                   ["Future_SERF_Review_Date" "REVIEW"]]]
    (filter :Date
            (for [[tag date-type] tag-types]
              {:Type date-type
               :Date (date/not-default (value-of md-dates-el tag))}))))

(def serf-roles->umm-roles
  "Maps SERF roles to UMM roles"
  {"SERVICE PROVIDER CONTACT" "RESOURCEPROVIDER"
   "TECHNICAL CONTACT" "POINTOFCONTACT"
   "SERF AUTHOR" "AUTHOR"})

(defn- parse-contacts
  "Constructs a UMM Contacts element from a SERF Personnel element"
  [person]
  (for [type ["email" "phone" "fax"]
        value (values-at person (string/capitalize type))]
    {:Type type :Value value}))

(defn- parse-service-organization-urls
  "Parse a Service Organization URL element into a RelatedURL map"
  [service-provider role]
  (when (= role "RESOURCEPROVIDER")
    [{:URLs (values-at service-provider "Service_Organization_URL")
      :Description "SERVICE_ORGANIZATION_URL"}]))

(defn- parse-party
  "Constructs a UMM Party element from a SERF Personnel element and a SERF Service_Provider element"
  [person organization service-provider role]
  {:OrganizationName
   (when (= role "RESOURCEPROVIDER") {:ShortName (value-of organization "Short_Name")
                                              :LongName (value-of organization "Long_Name")})
   :Person {:FirstName (value-of person "First_Name")
            :MiddleName (value-of person "Middle_Name")
            :LastName (value-of person "Last_Name")}
   :Contacts (parse-contacts person)
   :Addresses [{:StreetAddresses (values-at person "Contact_Address/Address")
                :City (value-of person "Contact_Address/City")
                :StateProvince (value-of person "Contact_Address/Province_or_State")
                :PostalCode (value-of person "Contact_Address/Postal_Code")
                :Country (value-of person "Contact_Address/Country")}]
   :RelatedUrls (parse-service-organization-urls service-provider role)})

(defn- parse-personnel
  "Parse the personnel object of the SERF XML"
  [doc]
  (let [root-personnel (select doc "/SERF/Personnel")
        service-provider-personnel (select doc "/SERF/Service_Provider/Personnel")
        [organization] (select doc "/SERF/Service_Provider/Service_Organization")
        [service-provider] (select doc "/SERF/Service_Provider")
        personnel (concat root-personnel service-provider-personnel)]
    (for [person personnel
          role (values-at person "Role")]
      (let [translated-role (or (get serf-roles->umm-roles role) role)]
      ;;TODO: CMR-2298 Fix Responsibilities to have multiple roles. Then adjust accordingly below.
      {:Role translated-role
       :Party (parse-party person organization service-provider translated-role)}))))

(defn- parse-service-citations
  "Parse SERF Service Citations into UMM-S"
  [doc]
  (for [service-citation (select doc "/SERF/Service_Citation")]
    (into {} (map (fn [x]
                    (if (keyword? x)
                      [(csk/->PascalCaseKeyword x) (value-of service-citation (str x))]
                      x))
                  [[:Version (value-of service-citation "Edition")]
                   [:RelatedUrl (value-of service-citation "URL")]
                   :Title
                   [:Creator (value-of service-citation "Originators")]
                   :Editor
                   :SeriesName
                   :ReleaseDate
                   :ReleasePlace
                   :IssueIdentification
                   [:Publisher (value-of service-citation "Provider")]
                   :IssueIdentification
                   :DataPresentationForm
                   :OtherCitationDetails
                   [:DOI {:DOI (value-of service-citation "Persistent_Identifier/Identifier")}]]))))

(defn- parse-publication-references
  "Parse SERF Publication References into UMM-S"
  [doc]
  (for [pub-ref (select doc "/SERF/Reference")]
    (into {} (map (fn [x]
                    (if (keyword? x)
                      [(csk/->PascalCaseKeyword x) (value-of pub-ref (str x))]
                      x))
                  [:Author
                   :Publication_Date
                   :Title
                   :Series
                   :Edition
                   :Volume
                   :Issue
                   :Report_Number
                   :Publication_Place
                   :Publisher
                   :Pages
                   [:ISBN (value-of pub-ref "ISBN")]
                   (when (= (value-of pub-ref "Persistent_Identifier/Type") "DOI")
                     [:DOI {:DOI (value-of pub-ref "Persistent_Identifier/Identifier")}])
                   [:RelatedUrl
                    {:URLs (seq
                             (remove nil? [(value-of pub-ref "Online_Resource")]))}]
                   :Other_Reference_Details]))))

(defn- parse-actual-related-urls
  "Parse a SERF RelatedURL element into a map"
  [doc]
  (for [related-url (select doc "/SERF/Related_URL")]
    {:URLs (values-at related-url "URL")
     :Description (value-of related-url "Description")
     :Relation [(value-of related-url "URL_Content_Type/Type")
                (value-of related-url "URL_Content_Type/Subtype")]}))

(defn- parse-multimedia-samples
  "Parse a SERF Multimedia Sample element into a RelatedURL map"
  [doc]
  (for [multimedia-sample (select doc "/SERF/Multimedia_Sample")]
    {:URLs (values-at multimedia-sample "URL")
     :MimeType (value-of multimedia-sample "Format")
     :Description (value-of multimedia-sample "Description")}))

(defn- parse-related-urls
  "Parse SERF Related URLs and Multimedia Samples into a UMM RelatedUrls object"
  [doc]
  (let [actual-urls (parse-actual-related-urls doc)
        multimedia-urls (parse-multimedia-samples doc)]
    (concat actual-urls multimedia-urls)))

(defn- parse-metadata-associations
  "Parse a SERF document and return a UMM-S Metadata Associations element"
  [doc]
  [{:EntryId (value-of doc "/SERF/Parent_SERF")}])

(defn- parse-distributions
  "Parse a SERF document for Distribution elements and returns an UMM-S Distribution element"
  [doc]
  (for [dist (select doc "/SERF/Distribution")]
    {:DistributionMedia (value-of dist "Distribution_Media")
     :DistributionSize (value-of dist "Distribution_Size")
     :DistributionFormat (value-of dist "Distribution_Format")
     :Fees (value-of dist "Fees")}))

(defn- parse-additional-attributes
  "Parse a SERF document for Extended Metadata Elements and returns a UMM-S Additional Attrib elem"
  [doc]
  (concat (for [aa (select doc "/SERF/Extended_Metadata/Metadata")]
            {:Group (value-of aa "Group")
             :Name (value-of aa "Name")
             :DataType (value-of aa "Type")
             :Description (without-default-value-of aa "Description")
             :UpdateDate (date/not-default (value-of aa "Update_Date"))
             :Value (value-of aa "Value")})
          [{:Name "Metadata_Name"
            :Description "Root SERF Metadata_Name Object"
            :Value (value-of doc "/SERF/Metadata_Name")}
           {:Name "Metadata_Version"
            :Description "Root SERF Metadata_Version Object"
            :Value (value-of doc "/SERF/Metadata_Version")}]
           (for [idn-node (select doc "/SERF/IDN_Node")]
             {:Name "IDN_Node"
              :Description "Root SERF IDN_Node Object"
              :Value (clojure.string/join
                       [(value-of idn-node "Short_Name")
                        "|"
                        (value-of idn-node "Long_Name")])})))

(defn- parse-service-keywords
  "Parses a SERF document for Service Keyword elements and returns a UMM-S Service Keyword element"
  [doc]
  (for [sk (select doc "/SERF/Service_Parameters")]
    {:Category (value-of sk "Service_Category")
     :Topic (value-of sk "Service_Topic")
     :Term (value-of sk "Service_Term")
     :ServiceSpecificName (value-of sk "Service_Specific_Name")}))

(defn- parse-science-keywords
  "Parses a SERF document for Science Keyword elements and returns a UMM-S Science Keyword element"
  [doc]
  (for [sk (select doc "/SERF/Science_Parameters")]
    {:Category (value-of sk "Science_Category")
     :Topic (value-of sk "Science_Topic")
     :Term (value-of sk "Science_Term")
     :VariableLevel1 (value-of sk "Science_Variable_Level_1")
     :VariableLevel2 (value-of sk "Science_Variable_Level_2")
     :VariableLevel3 (value-of sk "Science_Variable_Level_3")
     :DetailedVariable (value-of sk "Science_Detailed_Variable")}))

(defn parse-serf-xml
  "Returns collection map from a SERF XML document."
  [doc]
  {:EntryId (value-of doc "/SERF/Entry_ID")
   :EntryTitle (value-of doc "/SERF/Entry_Title")
   :Abstract (value-of doc "/SERF/Summary/Abstract")
   :Purpose (value-of doc "/SERF/Summary/Purpose")
   :ServiceLanguage (value-of doc "/SERF/Service_Language")
   :Responsibilities (parse-personnel doc)
   :RelatedUrls (parse-related-urls doc)
   :ServiceCitation (parse-service-citations doc)
   :Quality (value-of doc "/SERF/Quality")
   :UseConstraints (value-of doc "/SERF/Use_Constraints")
   :AccessConstraints {:Description (value-of doc "/SERF/Access_Constraints")}
   :MetadataAssociations (parse-metadata-associations doc)
   :PublicationReferences (parse-publication-references doc)
   :ISOTopicCategories (values-at doc "/SERF/ISO_Topic_Category")
   :Platforms (parse-platforms doc)
   :Distributions (parse-distributions doc)
   :AdditionalAttributes (parse-additional-attributes doc)
   :AncillaryKeywords (values-at doc "/SERF/Keyword")
   :Projects (parse-projects doc)
   :MetadataDates (parse-data-dates doc)
   :ServiceKeywords (parse-service-keywords doc)
   :ScienceKeywords (parse-science-keywords doc)})

(defn serf-xml-to-umm-s
  "Returns UMM-S service record from a SERF XML document."
  [metadata]
  (js/coerce js/umm-s-schema (parse-serf-xml metadata)))
