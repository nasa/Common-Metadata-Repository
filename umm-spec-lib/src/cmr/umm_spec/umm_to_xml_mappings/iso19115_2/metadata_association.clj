(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2.metadata-association
  "Functions for generating ISO19115-2 XML elements from UMM instrument records."
  (:require [cmr.umm-spec.xml.gen :refer :all]
            [cmr.umm-spec.iso19115-2-util :as iso]
            [cmr.umm-spec.iso-utils :as iso-utils]
            [cmr.umm-spec.util :as spec-util]))

(defmulti generate-metatdata-association
  "Create the mapping for a UMM model metadata-assocation"
  (fn [ma]
    (keyword (:Type ma))))

(defmethod generate-metatdata-association :Input
  [ma]
  [:gmd:source
   [:gmd:LE_Source
    [:gmd:description
     [:gco:CharacterString (:Description ma)]]
    [:gmd:sourceCitation
     [:gmd:CI_Citation
      [:gmd:title
       [:gco:CharacterString (:EntryId ma)]]
      [:gmd:date {:gco:nilReason "unknown"}]
      [:gmd:edition
       [:gco:CharacterString (:Version ma)]]]]]])

(def code-list-url (str "http://www.ngdc.noaa.gov/metadata/published/xsd/schema"
                        "/resources/Codelist/gmxCodelists.xml#DS_AssociationTypeCode"))

(defmethod generate-metatdata-association :default
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
        [:gmd:edition
         [:gco:CharacterString (:Version ma)]]
        [:gmd:otherCitationDetails
         [:gco:CharacterString (:Description ma)]]]]
      [:gmd:aggregateDataSetIdentifier
       [:gmd:MD_Identifier
        [:gmd:code
         [:gco:CharacterString entry-id]]]]
      [:gmd:associationType
       [:gmd:DS_AssociationTypeCode {:codeList code-list-url :codeListValue assoc-type}
        assoc-type]]]]))

(defn generate-source-metadata-associations
  "Generate the xml for all the source associated metadata (Type = \"Input\")"
  [umm]
  (for [ma (:MetadataAssociations umm) :when (= "Input" (:Type ma))]
    (generate-metatdata-association ma)))

(defn generate-non-source-metadata-associations
  "Generate the xml for all the non-source associated metadata (Type != \"Input\""
  [umm]
  (for [ma (:MetadataAssociations umm) :when (not= "Input" (:Type ma))]
    (generate-metatdata-association ma)))

