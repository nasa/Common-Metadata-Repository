(ns cmr.umm-spec.xml-to-umm-mappings.iso-smap.distributions-related-url
  "Functions for parsing UMM related-url records out of ISO SMAP XML documents."
  (:require
   [clojure.string :as str]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer :all]
   [cmr.umm-spec.iso19115-2-util :refer :all]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.distributions-related-url :as sdru]
   [cmr.umm-spec.url :as url]))

(def distributor-xpath
  (str "/gmd:DS_Series/gmd:seriesMetadata"
       "/gmi:MI_Metadata/gmd:distributionInfo/gmd:MD_Distribution/gmd:distributor"))

(def distributor-fees-xpath
  "gmd:MD_Distributor/gmd:distributionOrderProcess/gmd:MD_StandardOrderProcess/gmd:fees/gco:CharacterString")

(def distributor-format-xpath
  "gmd:MD_Distributor/gmd:distributorFormat/gmd:MD_Format/gmd:name/gco:CharacterString")

(def distributor-media-xpath
  "gmd:MD_Distributor/gmd:distributorFormat/gmd:MD_Format/gmd:specification/gco:CharacterString")

(def distributor-transfer-options-xpath
  "gmd:MD_Distributor/gmd:distributorTransferOptions/gmd:MD_DigitalTransferOptions")

(def distributor-online-url-xpath
  (str distributor-xpath "/gmd:MD_Distributor/gmd:distributorTransferOptions/gmd:MD_DigitalTransferOptions/gmd:onLine/gmd:CI_OnlineResource"))

(def browse-graphic-xpath
  (str "/gmd:DS_Series/gmd:seriesMetadata"
       "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:graphicOverview/gmd:MD_BrowseGraphic"))

(def service-url-path
  (str "/gmd:DS_Series/gmd:seriesMetadata"
       "/gmi:MI_Metadata/gmd:identificationInfo/srv:SV_ServiceIdentification"))

(def publication-url-path
 (str "/gmd:DS_Series/gmd:seriesMetadata"
      "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:aggregationInfo/"
      "gmd:MD_AggregateInformation/gmd:aggregateDataSetName/gmd:CI_Citation/"
      "gmd:citedResponsibleParty/gmd:CI_ResponsibleParty/gmd:contactInfo/"
      "gmd:CI_Contact/gmd:onlineResource/CI_OnlineResource"))

(def service-online-resource-xpath (str "srv:containsOperations/srv:SV_OperationMetadata/"
                                        "srv:connectPoint/gmd:CI_OnlineResource"))

(def distributor-xpaths-map
  {:Root distributor-xpath
   :Fees distributor-fees-xpath
   :Format distributor-format-xpath
   :TransferOptions distributor-transfer-options-xpath
   :URL "gmd:onLine/gmd:CI_OnlineResource/"})

(defn parse-related-urls
  "Parse related-urls present in the document"
  [doc sanitize?]
  (seq (concat (sdru/parse-online-and-service-urls
                 doc sanitize? service-url-path distributor-xpaths-map service-online-resource-xpath)
               (sdru/parse-browse-graphics doc sanitize? browse-graphic-xpath)
               (sdru/parse-publication-urls doc sanitize? publication-url-path))))
