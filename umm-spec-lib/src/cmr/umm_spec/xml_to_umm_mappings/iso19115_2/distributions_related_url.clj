(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.distributions-related-url
  "Functions for parsing UMM related-url records out of ISO 19115-2 XML documents."
  (:require
   [clojure.string :as str]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer :all]
   [cmr.umm-spec.iso19115-2-util :refer :all]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.distributions-related-url :as sdru]))

(def distributor-xpath
  "/gmi:MI_Metadata/gmd:distributionInfo/gmd:MD_Distribution/gmd:distributor/gmd:MD_Distributor")

(def distributor-fees-xpath
  "gmd:distributionOrderProcess/gmd:MD_StandardOrderProcess/gmd:fees/gco:CharacterString")

(def distributor-format-xpath
  "gmd:distributorFormat/gmd:MD_Format/gmd:name/gco:CharacterString")

(def distributor-media-xpath
  "gmd:distributorFormat/gmd:MD_Format/gmd:specification/gco:CharacterString")

(def distributor-transfer-options-xpath
  "gmd:distributorTransferOptions/gmd:MD_DigitalTransferOptions")

(def distributor-online-url-xpath
  (str distributor-xpath "/gmd:distributorTransferOptions/gmd:MD_DigitalTransferOptions/gmd:onLine/gmd:CI_OnlineResource"))

(def browse-graphic-xpath
  "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:graphicOverview/gmd:MD_BrowseGraphic")

(def service-url-path
 "/gmi:MI_Metadata/gmd:identificationInfo/srv:SV_ServiceIdentification")

(def publication-url-path
 (str "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:aggregationInfo/"
      "gmd:MD_AggregateInformation/gmd:aggregateDataSetName/gmd:CI_Citation/"
      "gmd:citedResponsibleParty/gmd:CI_ResponsibleParty/gmd:contactInfo/"
      "gmd:CI_Contact/gmd:onlineResource/CI_OnlineResource"))

(def service-online-resource-xpath (str "srv:containsOperations/srv:SV_OperationMetadata/"
                                        "srv:connectPoint/gmd:CI_OnlineResource"))

(defn parse-distributions
  "Returns the distributions parsed from the given xml document."
  [doc sanitize?]
  (for [distributor-element (select doc distributor-xpath)]
    {:DistributionMedia (value-of distributor-element distributor-media-xpath)
     :Sizes (for [transfer-el (select distributor-element distributor-transfer-options-xpath)
                  :let [size (value-of transfer-el "gmd:transferSize/gco:Real")]]
              {:Size size
               :Unit (or (value-of transfer-el "gmd:unitsOfDistribution/gco:CharacterString")
                         (when (and sanitize? size) "MB"))})
     :DistributionFormat (value-of distributor-element distributor-format-xpath)
     :Fees (value-of distributor-element distributor-fees-xpath)}))

; (defn- get-index-or-nil
;  "Get the index of the key in the description. Return nil if the key does not
;  exist in the description"
;  [description key]
;  (let [index (.indexOf description key)]
;   (when (>= index 0)
;    index)))
;
; (defn- parse-url-types-from-description
;  "In ISO, since there are not separate fields for the types, they are put in the
;  description in the format 'Description: X URLContentType: Y Type: Z Subtype: A'
;  Parse all available elements out from the description string."
;  [description]
;  (when description
;   (let [description-index (get-index-or-nil description "Description:")
;         url-content-type-index (get-index-or-nil description "URLContentType:")
;         type-index (get-index-or-nil description " Type:")
;         subtype-index (get-index-or-nil description "Subtype:")]
;    (if (and (nil? description-index)(nil? url-content-type-index)
;             (nil? type-index) (nil? subtype-index))
;     {:Description description} ; Description not formatted like above, so just description
;     {:Description (when description-index
;                    (let [desc (subs description
;                                description-index
;                                (or url-content-type-index type-index subtype-index (count description)))]
;                     (str/trim (subs desc (inc (.indexOf desc ":"))))))
;      :URLContentType (when url-content-type-index
;                       (let [content-type (subs description
;                                           url-content-type-index
;                                           (or type-index subtype-index (count description)))]
;                         (str/trim (subs content-type (inc (.indexOf content-type ":"))))))
;      :Type (when type-index
;             (let [type (subs description
;                         type-index
;                         (or subtype-index (count description)))]
;               (str/trim (subs type (inc (.indexOf type ":"))))))
;      :Subtype (when subtype-index
;                (let [subtype (subs description subtype-index)]
;                  (str/trim (subs subtype (inc (.indexOf subtype ":"))))))}))))
;
; (defn- parse-operation-description
;   "Parses operationDescription string, returns MimeType, DataID, and DataType"
;   [operation-description]
;   (if operation-description
;     (let [split-operation-description (when operation-description
;                                               (str/split operation-description #" "))
;           mime-type-index (get-index-or-nil operation-description "MimeType:")
;           mime-type (nth operation-description (inc mime-type-index))
;           dataid-index (get-index-or-nil operation-description "DataID:")
;           dataid (nth operation-description dataid-index)
;           data-type-index (get-index-or-nil operation-description "DateType:")
;           data-type (nth operation-description (inc data-type-index))]
;       [mime-type dataid data-type])
;     [nil nil nil]))
;
; (defn- parse-service-urls
;   "Parse service URLs from service location. These are most likely dups of the
;  distribution urls, but may contain additional type info."
;   [doc sanitize?]
;   (for [service (select doc service-url-path)
;         :let [local-name (value-of service "srv:serviceType/gco:LocalName")]
;         :when (str/includes? local-name "RelatedURL")
;         :let [url-types (parse-url-types-from-description local-name)
;               uris (mapv #(value-of % "gmd:linkage/gmd:URL")
;                          (select service service-online-resource-xpath))
;               url (first (select service service-online-resource-xpath))
;               url-link (value-of url "gmd:linkage/gmd:URL")
;               full-name (value-of service "srv:containsOperations/srv:SV_OperationMetadata/srv:operationName/gco:CharacterString")
;               protocol (value-of service (str service-online-resource-xpath "gmd:protocol/gco:CharacterString"))
;               operation-description (value-of service "srv:containsOperations/srv:SV_OperationMetadata/srv:operationDescription/gco:CharacterString")
;               [mime-type dataid data-type] (parse-operation-description operation-description)]]
;
;     (merge url-types
;            {:URL (when url-link (url/format-url url-link sanitize?))
;             :Description (char-string-value url "gmd:description")}
;            {:GetService (when (or mime-type full-name dataid protocol data-type
;                                   (not (empty? uris)))
;                           {:MimeType (su/with-default mime-type sanitize?)
;                            :FullName (su/with-default full-name sanitize?)
;                            :DataID (su/with-default dataid sanitize?)
;                            :Protocol (su/with-default protocol sanitize?)
;                            :DataType (su/with-default data-type sanitize?)
;                            :URI (su/with-default uris sanitize?)})})))

; (defn- parse-online-urls
;   "Parse ISO online resource urls"
;   [doc sanitize? service-urls]
;   (for [url (select doc distributor-online-url-xpath)
;         :let [name (char-string-value url "gmd:name")
;               code (value-of url "gmd:function/gmd:CI_OnlineFunctionCode")
;               url-link (value-of url "gmd:linkage/gmd:URL")
;               url-link (when url-link (url/format-url url-link sanitize?))
;               opendap-type (when (= code "GET DATA : OPENDAP DATA (DODS)")
;                             "GET SERVICE")
;               types-and-desc (parse-url-types-from-description
;                               (char-string-value url "gmd:description"))
;               service-url (some #(= url-link (:URL %)) service-urls)
;               checksum (value-of url "gmd:description/gco:CharacterString=\"Checksum:\"")]]
;     (merge
;      {:URL url-link
;       :URLContentType "DistributionURL"
;       :Type (or opendap-type (:Type types-and-desc) (:Type service-url) "GET DATA")
;       :Subtype (if opendap-type
;                 "OPENDAP DATA (DODS)"
;                 (or (:Subtype types-and-desc) (:Subtype service-url)))
;       :Description (:Description types-and-desc)}
;      (case (or opendap-type (:Type types-and-desc))
;        "GET DATA" (if checksum
;                     {:GetData {:Unit (su/with-default nil sanitize?)
;                                :Size (su/with-default nil sanitize?)
;                                :Format (su/with-default nil sanitize?)
;                                :Checksum checksum}}
;                     {:GetData nil})
;        "GET SERVICE" {:GetService (:GetService service-url)}
;        nil))))
;

(defn- parse-publication-urls
 "Parse PublicationURL and CollectionURL from the publication location."
 [doc sanitize?]
 (for [url (select doc publication-url-path)
       :let [description (char-string-value url "gmd:description")]
       :when (and (some? description)
                  (str/includes? description "PublicationURL"))
       :let [types-and-desc (sdru/parse-url-types-from-description description)
             url-content-type (or (:URLContentType types-and-desc) "PublicationURL")]]
  {:URL (value-of url "gmd:linkage/gmd:URL")
   :Description (:Description types-and-desc)
   :URLContentType (or (:URLContentType types-and-desc) "PublicationURL")
   :Type (or (:Type types-and-desc) "VIEW RELATED INFORMATION")
   :Subtype (:Subtype types-and-desc)}))

(defn parse-related-urls
  "Parse related-urls present in the document"
  [doc sanitize?]
  (seq (concat (sdru/parse-online-and-service-urls
                 doc sanitize? service-url-path distributor-online-url-xpath)
               (sdru/parse-browse-graphics doc sanitize? browse-graphic-xpath)
               (parse-publication-urls doc sanitize?))))
