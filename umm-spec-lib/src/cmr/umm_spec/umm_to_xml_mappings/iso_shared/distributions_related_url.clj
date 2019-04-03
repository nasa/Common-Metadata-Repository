(ns cmr.umm-spec.umm-to-xml-mappings.iso-shared.distributions-related-url
  "Functions for generating ISO XML elements from UMM related-url records."
  (:require
    [clojure.string :as str]
    [cmr.common.xml.gen :refer :all]
    [cmr.umm-spec.iso19115-2-util :as iso]
    [cmr.umm-spec.url :as url]
    [cmr.umm-spec.util :as su :refer [char-string]]))

(def type->name
  "Mapping of related url type to online resource name"
  {"GET DATA" "DATA ACCESS"
   "VIEW RELATED INFORMATION" "Guide"
   "GET RELATED VISUALIZATION" "Browse"})

(defn browse-url?
  "Returns true if the related-url is browse url"
  [related-url]
  (= "VisualizationURL" (:URLContentType related-url)))

(defn- browse-urls
  "Returns the related-urls that are browse urls"
  [related-urls]
  (filter browse-url? related-urls))

(defn- online-resource-urls
  "Returns all related-urls which are not browse urls"
  [related-urls]
  (filter #(= "DistributionURL" (:URLContentType %)) related-urls))

(defn- generate-description-with-types
 "In ISO, we don't have separate fields to store the types, we put them in
 the description string. Create a description string that contains the
 description, URLContentType, type, and subtype"
 [related-url]
 (let [{:keys [URLContentType Type Subtype Description]} related-url
       description (if Description
                    (format "Description: %s URLContentType: %s Type: %s"
                     Description URLContentType Type)
                    (format "URLContentType: %s Type: %s"
                     URLContentType Type))]
  (if Subtype
   (format "%s Subtype: %s" description Subtype)
   description)))

(defn- generate-distribution-name
  "Generate operation description from GetData values"
  [Format MimeType]
  (let [description (format "Format: %s " (su/with-default Format))
        description (if (seq MimeType)
                      (str description (format "MimeType: %s " (su/with-default MimeType)))
                      description)]
    (str/trim description)))

(defn generate-browse-urls
  "Returns content generator instructions for a browse url"
  [c]
  (for [related-url (browse-urls (:RelatedUrls c))
        :let [{:keys [URL Description Type]} related-url
              description (generate-description-with-types related-url)]]
    [:gmd:graphicOverview
     [:gmd:MD_BrowseGraphic
      [:gmd:fileName
       [:gmx:FileName {:src URL}]]
      [:gmd:fileDescription (char-string description)]
      [:gmd:fileType (char-string (type->name Type))]]]))

(defn generate-online-resource-url
  "Returns content generator instructions for an online resource url or access url.
  Used when mapping data contacts. encode-types=true will encode the Type and Subtype into the
  description field."
  [online-resource-url open-tag encode-types]
  (when online-resource-url
   (let [{:keys [URL Description Type]}  online-resource-url
         description (if encode-types
                      (generate-description-with-types online-resource-url)
                      Description)
         name (type->name Type)
         code (if (= "GET DATA" Type) "download" "information")]
       [open-tag
        [:gmd:CI_OnlineResource
         [:gmd:linkage
          [:gmd:URL URL]]
         [:gmd:protocol
          (char-string (url/protocol URL))]
         [:gmd:name
          (char-string name)]
         (if description
           [:gmd:description
            (char-string description)]
           [:gmd:description {:gco:nilReason "missing"}])
         [:gmd:function
          [:gmd:CI_OnLineFunctionCode
           {:codeList (str (:ngdc iso/code-lists) "#CI_OnLineFunctionCode")
            :codeListValue code}]]]])))

(defn generate-distributor-online-resource-url
  "Returns content generator instructions for an online resource url along with the distributor."
  [online-resource-url open-tag encode-types]
  (when online-resource-url
   (let [{:keys [URL Description Type GetData]}  online-resource-url
         description (if encode-types
                      (generate-description-with-types online-resource-url)
                      Description)
         name (type->name Type)
         format (:Format GetData)
         mime-type (:MimeType GetData)
         code (if (= "GET DATA" Type) "download" "information")]
     (when-not (= "USE SERVICE API" Type)
       [:gmd:distributor
        [:gmd:MD_Distributor
         [:gmd:distributorContact {:gco:nilReason "missing"}]
         [:gmd:distributionOrderProcess
          [:gmd:MD_StandardOrderProcess
           [:gmd:fees
            (char-string (or (:Fees GetData) ""))]]]
         [:gmd:distributorFormat
          [:gmd:MD_Format
           [:gmd:name
            (char-string
             (generate-distribution-name format mime-type))]
           [:gmd:version {:gco:nilReason "unknown"}]
           [:gmd:specification
            ""]]]
         [:gmd:distributorTransferOptions
          [:gmd:MD_DigitalTransferOptions
           [:gmd:unitsOfDistribution
            (char-string (:Unit GetData))]
           [:gmd:transferSize
            [:gco:Real (:Size GetData)]]
           [open-tag
            [:gmd:CI_OnlineResource
             [:gmd:linkage
              [:gmd:URL URL]]
             [:gmd:protocol
              (char-string (url/protocol URL))]
             [:gmd:name
              (char-string name)]
             (if description
               [:gmd:description
                (if (seq (:Checksum GetData))
                  (char-string (str description " Checksum: " (:Checksum GetData)))
                  (char-string description))]
               [:gmd:description {:gco:nilReason "missing"}])
             [:gmd:function
              [:gmd:CI_OnLineFunctionCode
               {:codeList (str (:ngdc iso/code-lists) "#CI_OnLineFunctionCode")
                :codeListValue code}]]]]]]]]))))

(defn- generate-operation-description
  "Generate operation description from GetService values"
  [MimeType DataID DataType Format]
  (let [operation-description (format "MimeType: %s " (su/with-default MimeType))
        operation-description (str operation-description (format "DataID: %s " (su/with-default DataID)))
        operation-description (str operation-description (format "DataType: %s " (su/with-default DataType)))
        operation-description (if (seq Format)
                                (str operation-description (format "Format: %s " (su/with-default Format)))
                                operation-description)]
    (str/trim operation-description)))

(defn generate-service-related-url
 "Write 'USE SERVICE API' related urls to an additional area of ISO"
 [related-urls]
 (for [service-url (filter #(and (= "DistributionURL" (:URLContentType %))
                                 (= "USE SERVICE API" (:Type %)))
                           related-urls)
       :let [{URL :URL Description :Description} service-url
             {:keys [MimeType Protocol FullName DataID DataType URI Format]}  (:GetService service-url)
             URI (remove #(= URL %) URI)
             operation-description (generate-operation-description MimeType DataID DataType Format)
             url-type-desc (generate-description-with-types
                             (dissoc service-url :Description))]] ; Don't want description
  [:gmd:identificationInfo
   [:srv:SV_ServiceIdentification
    [:gmd:citation {:gco:nilReason "missing"}]
    [:gmd:abstract {:gco:nilReason "missing"}]
    [:srv:serviceType
     [:gco:LocalName (str "RelatedURL " url-type-desc)]]
    [:srv:couplingType
     [:srv:SV_CouplingType
      {:codeList "" :codeListValue ""} "tight"]]
    [:srv:containsOperations
     [:srv:SV_OperationMetadata
      (if FullName
        [:srv:operationName (char-string FullName)]
        [:srv:operationName {:gco:nilReason "missing"}])
      [:srv:DCP {:gco:nilReason "unknown"}]
      (when operation-description
        [:srv:operationDescription
         (char-string operation-description)])
      [:srv:connectPoint
       [:gmd:CI_OnlineResource
        [:gmd:linkage
         [:gmd:URL URL]]
        [:gmd:protocol
         (if Protocol
           (char-string Protocol)
           (char-string su/not-provided))]
        (if Description
          [:gmd:description
           (char-string Description)]
          [:gmd:description {:gco:nilReason "missing"}])
        [:gmd:function
         [:gmd:CI_OnLineFunctionCode
          {:codeList (str (:ngdc iso/code-lists) "#CI_OnLineFunctionCode")
           :codeListValue "download"}]]]]
      (when URI
        (for [uri URI]
          [:srv:connectPoint
           [:gmd:CI_OnlineResource
            [:gmd:linkage
             [:gmd:URL uri]]]]))]]]]))

(defn generate-publication-related-urls
 "PublicatonURL and CollectionURL go in the same section as the Publication
 References. Write URLs with these types there."
 [c]
 (for [publication-url (filter #(or (= "CollectionURL" (:URLContentType %))
                                    (= "PublicationURL" (:URLContentType %)))
                               (:RelatedUrls c))
       :let [description (generate-description-with-types publication-url)]]
  [:gmd:aggregationInfo
   [:gmd:MD_AggregateInformation
    [:gmd:aggregateDataSetName
     [:gmd:CI_Citation
      [:gmd:title {:gco:nilReason "missing"}]
      [:gmd:date {:gco:nilReason "missing"}]
      [:gmd:citedResponsibleParty
       [:gmd:CI_ResponsibleParty
        [:gmd:contactInfo
         [:gmd:CI_Contact
          [:gmd:onlineResource
           [:gmd:CI_OnlineResource
            [:gmd:linkage
             [:gmd:URL (:URL publication-url)]]
            [:gmd:description (char-string description)]]]]]
        [:gmd:role
         [:gmd:CI_RoleCode
          {:codeList "" :codeListValue ""}]]]]]]
    [:gmd:associationType {:gco:nilReason "missing"}]]]))

(defn generate-distributions
  "Returns content generator instructions for distributions in the given umm-c"
  [c]
  (when-let [related-urls (online-resource-urls (:RelatedUrls c))]
    (for [related-url related-urls]
      (generate-distributor-online-resource-url related-url :gmd:onLine true))))
