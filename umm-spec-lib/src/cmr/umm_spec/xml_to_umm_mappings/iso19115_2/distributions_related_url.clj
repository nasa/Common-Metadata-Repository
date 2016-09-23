(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.distributions-related-url
  "Functions for parsing UMM related-url records out of ISO 19115-2 XML documents."
  (:require
   [clojure.string :as str]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select]]
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

(def resource-name->types
  "Mapping of ISO online resource name to UMM related url type and sub-type"
  {"DATA ACCESS" "GET DATA"
   "Guide" "VIEW RELATED INFORMATION"
   "Browse" "GET RELATED VISUALIZATION"})

(defn- parse-online-urls
  "Parse ISO online resource urls"
  [doc sanitize?]
  (for [url (select doc distributor-online-url-xpath)
        :let [name (char-string-value url "gmd:name")
              code (value-of url "gmd:function/gmd:CI_OnlineFunctionCode")
              type (if (= "download" code)
                     "GET DATA"
                     (when name (resource-name->types name)))
              url-link (value-of url "gmd:linkage/gmd:URL")]]
    {:URLs (when url-link [(url/format-url url-link sanitize?)])
     :Description (char-string-value url "gmd:description")
     :Relation (when type [type])}))

(defn- parse-browse-graphics
  "Parse browse graphic urls"
  [doc sanitize?]
  (for [url (select doc browse-graphic-xpath)
        ;; We retrieve browse url from two different places. This might change depending on the
        ;; outcome of ECSE-129.
        :let [browse-url (or (value-of url "gmd:fileName/gmx:FileName/@src")
                             (value-of url "gmd:fileName/gco:CharacterString"))]]
    {:URLs (when browse-url [(url/format-url browse-url sanitize?)])
     :Description (char-string-value url "gmd:fileDescription")
     :Relation (when-let [rel (resource-name->types (char-string-value url "gmd:fileType"))]
                 [rel])}))

(defn parse-related-urls
  "Parse related-urls present in the document"
  [doc sanitize?]
  (if-let [related-urls (seq
                         (concat
                          (parse-online-urls doc sanitize?)
                          (parse-browse-graphics doc sanitize?)))]
    related-urls
    (when sanitize?
     [su/not-provided-related-url])))
