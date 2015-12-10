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
  [doc path]
  (seq (for [elem (select doc path)]
         {:ShortName (value-of elem "Short_Name")
          :LongName (value-of elem "Long_Name")})))

(defn- parse-instruments
  [doc]
  (parse-short-name-long-name doc "/SERF/Sensor_Name"))

(defn- parse-just-platforms
  "Parses a doc for a SERF representation of platforms only."
  [doc]
  (parse-short-name-long-name doc "/SERF/Source_Name"))

(defn- parse-platforms
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
  [doc]
  (for [proj (select doc "/SERF/Project")]
    {:ShortName (value-of proj "Short_Name")
     :LongName (value-of proj "Long_Name")
     :Campaigns (values-at proj "Campaign")
     :StartDate (date-at proj "Start_Date")
     :EndDate (date-at proj "End_Date")}))

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

(def serf-roles->umm-roles {:ServiceProviderContact "RESOURCEPROVIDER" 
                            :TechnicalContact "POINTOFCONTACT" 
                            :SerfAuthor "AUTHOR"} )

(defn- parse-party 
  "Constructs a UMM Party element from a SERF Personnel element and a SERF Service_Provider element"
  [person organization]
  {:OrganizationName {:ShortName (value-of organization "Short_Name")
                      :LongName (value-of organization "Long_Name")}
   :Person {:FirstName (value-of person "First_Name")
            :MiddleName (value-of person "Middle_Name")
            :LastName (value-of person "Last_Name")
            }
   :Contacts [{:Type "email" :Value (value-of person "Email")} 
              {:Type "phone" :Value (value-of person "Phone")}
              {:Type "fax"   :Value (value-of person "Fax")}
              ]
   :Addresses [{:StreetAddresses [(value-of person "Contact_Address/Address")]
                :City (value-of person "Contact_Address/City")
                :StateProvince (value-of person "Contact_Address/Province_or_State")
                :PostalCode (value-of person "Contact_Address/Postal_Code")
                :Country (value-of person "Contact_Address/Country")}]})

(defn- parse-personnel
  "Parse the personnel object of the SERF XML"
  [doc]
  (let [root-personnel (select doc "/SERF/Personnel")
        service-provider-personnel (select doc "/SERF/Service_Provider/Personnel")
        organization (select doc "/SERF/Service_Provider/Service_Organization")
        personnel (concat root-personnel service-provider-personnel)]
    (for [person personnel
          role (values-at person "Role")]
      ;;TODO: CMR-2298 Fix Responsibilities to have multiple roles. Then adjust accordingly below. 
      {:Role ((csk/->PascalCaseKeyword (keyword role)) serf-roles->umm-roles)
       :Party (parse-party person organization)})))

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
     :Protocol (value-of related-url "Protocol")
     :Description (value-of related-url "Description")
     :ContentType {:Type (value-of related-url "URL_Content_Type/Type")
                   :Subtype (value-of related-url "URL_Content_Type/Subtype")}
     :MimeType (value-of related-url "Mime_Type")}))

(defn- parse-multimedia-samples
  "Parse a SERF Multimedia Sample element into a RelatedURL map" 
  [doc]
  (for [multimedia-sample (select doc "/SERF/Multimedia_Sample")]
    {:URLs (values-at multimedia-sample "URL")
     :Protocol (value-of multimedia-sample "Format")
     :Description (value-of multimedia-sample "Description")}))

(defn- parse-related-urls
  "Parse SERF Related URLs and Multimedia Samples into a UMM RelatedUrls object"
  [doc]
  (let [actual-urls (parse-actual-related-urls doc)
        multimedia-urls (parse-multimedia-samples doc)]
    (conj actual-urls multimedia-urls)))

(defn parse-serf-xml
  "Returns collection map from a SERF XML document."
  [doc]
  {:EntryTitle (value-of doc "/SERF/Entry_Title")
   :EntryId (value-of doc "/SERF/Entry_ID")
   :Abstract (value-of doc "/SERF/Summary/Abstract")
   :Purpose (value-of doc "/SERF/Summary/Purpose")
   :ServiceLanguage (value-of doc "/SERF/Service_Language")
   :Responsibilities (parse-personnel doc)
   ;;TODO: Bundle /SERF/Multimedia_Sample into RelatedUrls
   :RelatedUrls (parse-related-urls doc)
   :ServiceCitation (parse-service-citations doc)
   :Quality (value-of doc "/SERF/Quality")
   :UseConstraints (value-of doc "/SERF/Use_Constraints")
   :AccessConstraints (value-of doc "/SERF/Use_Constraints")
   :MetadataAssociations (for [ma (select doc "/SERF/Parent_SERF")]
                           {:EntryId (value-of ma "Entry_Id/Short_Name")
                            :Version (without-default-value-of ma "Entry_Id/Version")
                            :Description (without-default-value-of ma "Description")
                            :Type (string/upper-case (without-default-value-of ma "Type"))})
   :PublicationReferences (parse-publication-references doc)
   :ISOTopicCategories (values-at doc "/SERF/ISO_Topic_Category")
   :Platforms (parse-platforms doc)
   :Distributions (for [dist (select doc "/SERF/Distribution")]
                    {:DistributionMedia (value-of dist "Distribution_Media")
                     :DistributionSize (value-of dist "Distribution_Size")
                     :DistributionFormat (value-of dist "Distribution_Format")
                     :Fees (value-of dist "Fees")})
   :AdditionalAttributes (for [aa (select doc "/SERF/Extended_Metadata/Metadata")]
                           {:Group (value-of aa "Group")
                            :Name (value-of aa "Name")
                            :DataType (value-of aa "Type")
                            :Description (without-default-value-of aa "Description")
                            :UpdateDate (date/not-default (value-of aa "Update_Date"))
                            :Value (value-of aa "Value")})
   :AncillaryKeywords (values-at doc "/SERF/Keyword")
   :Projects (parse-projects doc)
   :MetadataDates (parse-data-dates doc)
   :ServiceKeywords (for [sk (select doc "/SERF/Service_Parameters")]
                      {:Category (value-of sk "Service_Category")
                       :Topic (value-of sk "Service_Topic")
                       :Term (value-of sk "Service_Term")
                       :VariableLevel1 (value-of sk "Service_Variable_Level_1")
                       :VariableLevel2 (value-of sk "Service_Variable_Level_2")
                       :VariableLevel3 (value-of sk "Service_Variable_Level_3")
                       :DetailedVariable (value-of sk "Service_Detailed_Variable")})
   :ScienceKeywords (for [sk (select doc "/SERF/Science_Parameters")]
                      {:Category (value-of sk "Science_Category")
                       :Topic (value-of sk "Science_Topic")
                       :Term (value-of sk "Science_Term")
                       :ServiceSpecificName (value-of sk "Service_Specific_Name")})})


(defn serf-xml-to-umm-s
  "Returns UMM-S service record from a SERF XML document."
  [metadata]
  (js/coerce js/umm-s-schema (parse-serf-xml metadata)))
