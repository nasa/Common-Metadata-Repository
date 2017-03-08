(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.distributions-related-url
  "Functions for parsing UMM related-url records out of ISO 19115-2 XML documents."
  (:require
   [clojure.string :as str]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer :all]
   [cmr.umm-spec.iso19115-2-util :refer :all]
   [cmr.umm-spec.url :as url]
   [cmr.umm-spec.util :as su]))

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

(defn- get-index-or-nil
 "Get the index of the key in the description. Return nil if the key does not
 exist in the description"
 [description key]
 (let [index (.indexOf description key)]
  (when (>= index 0)
   index)))

(defn- parse-url-types-from-description
 "In ISO, since there are not separate fields for the types, they are put in the
 description in the format 'Description: X URLContentType: Y Type: Z Subtype: A'
 Parse all available elements out from the description string."
 [description]
 (when description
  (let [description-index (get-index-or-nil description "Description:")
        url-content-type-index (get-index-or-nil description "URLContentType:")
        type-index (get-index-or-nil description " Type:")
        subtype-index (get-index-or-nil description "Subtype:")]
   (if (and (nil? description-index)(nil? url-content-type-index)
            (nil? type-index) (nil? subtype-index))
    {:Description description} ; Description not formatted like above, so just description
    {:Description (when description-index
                   (let [desc (subs description
                               description-index
                               (or url-content-type-index type-index subtype-index (count description)))]
                    (str/trim (subs desc (inc (.indexOf desc ":"))))))
     :URLContentType (when url-content-type-index
                      (let [content-type (subs description
                                          url-content-type-index
                                          (or type-index subtype-index (count description)))]
                        (str/trim (subs content-type (inc (.indexOf content-type ":"))))))
     :Type (when type-index
            (let [type (subs description
                        type-index
                        (or subtype-index (count description)))]
              (str/trim (subs type (inc (.indexOf type ":"))))))
     :Subtype (when subtype-index
               (let [subtype (subs description subtype-index)]
                 (str/trim (subs subtype (inc (.indexOf subtype ":"))))))}))))

(defn- parse-service-urls
 "Parse service URLs from service location. These are most likely dups of the
 distribution urls, but may contain additional type info."
 [doc sanitize?]
 (for [service (select doc service-url-path)
       :let [local-name (value-of service "srv:serviceType/gco:LocalName")]
       :when (str/includes? local-name "RelatedURL")
       :let [url-types (parse-url-types-from-description local-name)
             url (first (select service
                          (str "srv:containsOperations/srv:SV_OperationMetadata/"
                               "srv:connectPoint/gmd:CI_OnlineResource")))
             url-link (value-of url "gmd:linkage/gmd:URL")]]
   (merge url-types
     {:URL (when url-link (url/format-url url-link sanitize?))
      :Description (char-string-value url "gmd:description")})))

(defn- parse-online-urls
  "Parse ISO online resource urls"
  [doc sanitize? service-urls]
  (for [url (select doc distributor-online-url-xpath)
        :let [name (char-string-value url "gmd:name")
              code (value-of url "gmd:function/gmd:CI_OnlineFunctionCode")
              url-link (value-of url "gmd:linkage/gmd:URL")
              url-link (when url-link (url/format-url url-link sanitize?))
              opendap-type (when (= code "GET DATA : OPENDAP DATA (DODS)")
                            "GET SERVICE")
              types-and-desc (parse-url-types-from-description
                              (char-string-value url "gmd:description"))
              service-url (some #(= url-link (:URL %)) service-urls)]]
   {:URL url-link
    :URLContentType "DistributionURL"
    :Type (or opendap-type (:Type types-and-desc) (:Type service-url) "GET DATA")
    :Subtype (if opendap-type
              "OPENDAP DATA (DODS)"
              (or (:Subtype types-and-desc) (:Subtype service-url)))
    :Description (:Description types-and-desc)}))

(defn- parse-publication-urls
 "Parse PublicationURL and CollectionURL from the publication location."
 [doc sanitize?]
 (for [url (select doc publication-url-path)
       :let [description (char-string-value url "gmd:description")]
       :when (and (some? description)
                  (str/includes? description "PublicationURL"))
       :let [types-and-desc (parse-url-types-from-description description)
             url-content-type (or (:URLContentType types-and-desc) "PublicationURL")]]
  {:URL (value-of url "gmd:linkage/gmd:URL")
   :Description (:Description types-and-desc)
   :URLContentType (or (:URLContentType types-and-desc) "PublicationURL")
   :Type (or (:Type types-and-desc) "VIEW RELATED INFORMATION")
   :Subtype (:Subtype types-and-desc)}))

(defn- parse-online-and-service-urls
 "Parse online and service urls. Service urls may be a dup of distribution urls,
 but may contain additional needed type information. Filter out dup service URLs."
 [doc sanitize?]
 (let [service-urls (parse-service-urls doc sanitize?)
       online-urls (parse-online-urls doc sanitize? service-urls)
       online-url-urls (set (map :URL online-urls))
       service-urls (seq (remove #(contains? online-url-urls (:URL %)) service-urls))]
  (concat
   online-urls
   service-urls)))

(defn- parse-browse-graphics
  "Parse browse graphic urls"
  [doc sanitize?]
  (for [url (select doc browse-graphic-xpath)
        ;; We retrieve browse url from two different places. This might change depending on the
        ;; outcome of ECSE-129.
        :let [browse-url (or (value-of url "gmd:fileName/gmx:FileName/@src")
                             (value-of url "gmd:fileName/gco:CharacterString"))
              types-and-desc (parse-url-types-from-description
                              (char-string-value url "gmd:fileDescription"))]]
     {:URL (when browse-url (url/format-url browse-url sanitize?))
      :Description (:Description types-and-desc)
      :URLContentType "VisualizationURL"
      :Type "GET RELATED VISUALIZATION"
      :Subtype (:Subtype types-and-desc)}))

(defn parse-related-urls
  "Parse related-urls present in the document"
  [doc sanitize?]
  (seq (concat (parse-online-and-service-urls doc sanitize?)
               (parse-browse-graphics doc sanitize?)
               (parse-publication-urls doc sanitize?))))
