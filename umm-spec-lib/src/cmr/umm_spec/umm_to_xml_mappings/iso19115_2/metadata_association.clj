(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2.metadata-association
  "Functions for generating ISO19115-2 XML elements from UMM instrument records."
  (:require [cmr.common.xml.gen :refer :all]
            [cmr.umm-spec.iso19115-2-util :as iso]
            [cmr.umm-spec.util :as spec-util]))

(defn- output-character-string
  "Write a gco:CharacterString element or a gco:nilReason"
  [tag-name value]
  (if value
    [tag-name [:gco:CharacterString value]]
    [tag-name {:gco:nilReason "missing"}]))


(defn generate-metatdata-association-input
  [ma]
  [:gmd:source
   [:gmd:LI_Source
    (output-character-string :gmd:description (:Description ma))
    [:gmd:sourceCitation
     [:gmd:CI_Citation
      [:gmd:title
       [:gco:CharacterString (:EntryId ma)]]
      [:gmd:date {:gco:nilReason "unknown"}]
      (output-character-string :gmd:edition (:Version ma))]]]])

(def code-list-url (str "https://cdn.earthdata.nasa.gov/iso"
                        "/resources/Codelist/gmxCodelists.xml#DS_AssociationTypeCode"))

(defn generate-metatdata-association
  [ma]
  (let [entry-id (:EntryId ma)
        assoc-type (:Type ma)]
    [:gmd:aggregationInfo
     [:gmd:MD_AggregateInformation
      [:gmd:aggregateDataSetName
       [:gmd:CI_Citation
        [:gmd:title
         [:gco:CharacterString entry-id]]
        [:gmd:date {:gco:nilReason "unknown"}]
        (output-character-string :gmd:edition (:Version ma))
        (output-character-string :gmd:otherCitationDetails (:Description ma))]]
      [:gmd:aggregateDataSetIdentifier
       [:gmd:MD_Identifier
        [:gmd:code
         [:gco:CharacterString entry-id]]]]
      [:gmd:associationType
       [:gmd:DS_AssociationTypeCode {:codeList code-list-url :codeListValue assoc-type}
        assoc-type]]]]))

(defn generate-source-metadata-associations
  "Generate the xml for all the source associated metadata (Type = \"INPUT\")"
  [umm]
  (for [ma (:MetadataAssociations umm) :when (= "INPUT" (:Type ma))]
    (generate-metatdata-association-input ma)))

(defn generate-non-source-metadata-associations
  "Generate the xml for all the non-source associated metadata (Type != \"INPUT\""
  [umm]
  (for [ma (:MetadataAssociations umm) :when (not= "INPUT" (:Type ma))]
    (generate-metatdata-association ma)))
