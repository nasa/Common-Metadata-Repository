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

(defn parse-related-urls
  "Parse related-urls present in the document"
  [doc sanitize?]
  (seq (concat (sdru/parse-online-and-service-urls
                 doc sanitize? service-url-path distributor-online-url-xpath)
               (sdru/parse-browse-graphics doc sanitize? browse-graphic-xpath)
               (sdru/parse-publication-urls doc sanitize? publication-url-path))))
