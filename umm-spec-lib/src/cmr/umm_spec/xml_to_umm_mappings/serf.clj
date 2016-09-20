(ns cmr.umm-spec.xml-to-umm-mappings.serf
  "Defines mappings from SERF XML into UMM records"
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as string]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.date-util :as date]
   [cmr.umm-spec.json-schema :as js]
   [cmr.umm-spec.util :as su :refer [without-default-value-of not-provided]]))

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
  [doc apply-default?]
  (let [platforms (parse-just-platforms doc)
        instruments (parse-instruments doc)]
    (if (= 1 (count platforms))
      (map #(assoc % :Instruments instruments) platforms)
      (if instruments
        (conj platforms {:ShortName (when apply-default? not-provided)
                         :LongName (when apply-default? not-provided)
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
               :Date (date/without-default (value-of md-dates-el tag))}))))

(def serf-roles->umm-roles
  "Maps SERF roles to UMM roles"
  {"SERVICE PROVIDER CONTACT" "RESOURCEPROVIDER"
   "TECHNICAL CONTACT" "POINTOFCONTACT"
   "SERF AUTHOR" "AUTHOR"})

(defn- parse-service-citations
  "Parse SERF Service Citations into UMM-S"
  [doc]
  (for [service-citation (select doc "/SERF/Service_Citation")]
    (into {} (map (fn [x]
                    (if (keyword? x)
                      [(csk/->PascalCaseKeyword x) (value-of service-citation (str x))]
                      x))
                  [[:Version (value-of service-citation "Edition")]
                   [:RelatedUrl {:URLs [(value-of service-citation "URL")]}]
                   :Title
                   [:Creator (value-of service-citation "Originators")]
                   :ReleaseDate
                   [:Publisher (value-of service-citation "Provider")]]))))

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
                   [:ISBN (su/format-isbn (value-of pub-ref "ISBN"))]
                   [:DOI {:DOI (value-of pub-ref "DOI")}]
                   [:RelatedUrl
                    {:URLs (values-at pub-ref "Online_Resource")}]
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
  [doc apply-default?]
  (let [actual-urls (parse-actual-related-urls doc)
        multimedia-urls (parse-multimedia-samples doc)]
    (if-let [related-urls (seq (concat actual-urls multimedia-urls))]
      related-urls
      [su/not-provided-related-url])))

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
             :UpdateDate (date/without-default (value-of aa "Update_Date"))
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
  [doc {:keys [apply-default?]}]
  {:EntryId (value-of doc "/SERF/Entry_ID")
   :EntryTitle (value-of doc "/SERF/Entry_Title")
   :Abstract (value-of doc "/SERF/Summary/Abstract")
   :Purpose (value-of doc "/SERF/Summary/Purpose")
   :ServiceLanguage (value-of doc "/SERF/Service_Language")
   :RelatedUrls (parse-related-urls doc apply-default?)
   :ServiceCitation (parse-service-citations doc)
   :Quality (value-of doc "/SERF/Quality")
   :UseConstraints (value-of doc "/SERF/Use_Constraints")
   :AccessConstraints {:Description (value-of doc "/SERF/Access_Constraints") :Value nil}
   :MetadataAssociations (parse-metadata-associations doc)
   :PublicationReferences (parse-publication-references doc)
   :ISOTopicCategories (values-at doc "/SERF/ISO_Topic_Category")
   :Platforms (parse-platforms doc apply-default?)
   :Distributions (parse-distributions doc)
   :AdditionalAttributes (parse-additional-attributes doc)
   :AncillaryKeywords (values-at doc "/SERF/Keyword")
   :Projects (parse-projects doc)
   :MetadataDates (parse-data-dates doc)
   :ServiceKeywords (parse-service-keywords doc)
   :ScienceKeywords (parse-science-keywords doc)})

(defn serf-xml-to-umm-s
  "Returns UMM-S service record from a SERF XML document. The :apply-default? option
  tells the parsing code to set the default values for fields when parsing the metadata into umm."
  [metadata options]
  (js/coerce js/umm-s-schema (parse-serf-xml metadata options)))
