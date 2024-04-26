(ns cmr.umm-spec.umm-to-xml-mappings.iso-shared.collection-citation
  "Functions for generating ISO XML elements from UMM collection-citation records."
  (:require
   [clojure.string :as str]
   [cmr.common.xml.gen :refer :all]
   [cmr.umm-spec.iso19115-2-util :as iso]))

(defn convert-date
  "Convert the release date in umm to the related fields in xml"
  [c]
  (when-let [date (:ReleaseDate (first (:CollectionCitations c)))]
     [:gmd:editionDate
      [:gco:DateTime (str date)]]))

(defn convert-creator
  "Convert the creator in umm to the related fields in xml"
  [c]
  (when-let [creator (:Creator (first (:CollectionCitations c)))]
    [:gmd:citedResponsibleParty
     [:gmd:CI_ResponsibleParty
      [:gmd:individualName
       [:gco:CharacterString (str/trim creator)]]
      [:gmd:role
        [:gmd:CI_RoleCode {:codeList (str (:ngdc iso/code-lists) "#CI_RoleCode")
                           :codeListValue "author"} "author"]]]]))

(defn convert-editor
  "Convert the editor in umm to the related fields in xml"
  [c]
  (when-let [editor (:Editor (first (:CollectionCitations c)))]
     [:gmd:citedResponsibleParty
      [:gmd:CI_ResponsibleParty
       [:gmd:individualName
        [:gco:CharacterString (str/trim editor)]]
       [:gmd:positionName
        [:gco:CharacterString "editor"]]
       [:gmd:role
         [:gmd:CI_RoleCode {:codeList (str (:ngdc iso/code-lists) "#CI_RoleCode")
                            :codeListValue "author"} "author"]]]]))

(defn convert-publisher
  "Convert the publisher in umm to the related fields in xml"
  [c]
  (when-let [publisher (:Publisher (first (:CollectionCitations c)))]
     [:gmd:citedResponsibleParty
      [:gmd:CI_ResponsibleParty
       [:gmd:individualName
        [:gco:CharacterString (str/trim publisher)]]
       [:gmd:role
        [:gmd:CI_RoleCode {:codeList (str (:ngdc iso/code-lists) "#CI_RoleCode")
                           :codeListValue "publisher"} "publisher"]]]]))

(defn convert-release-place
  "Convert the release-place in umm to the related fields in xml"
  [c]
  (when-let [release-place (:ReleasePlace (first (:CollectionCitations c)))]
     [:gmd:citedResponsibleParty
      [:gmd:CI_ResponsibleParty
       [:gmd:positionName
        [:gco:CharacterString "release place"]]
       [:gmd:contactInfo
        [:gmd:CI_Contact
         [:gmd:address
          [:gmd:CI_Address
           [:gmd:deliveryPoint
            [:gco:CharacterString release-place]]]]]]
       [:gmd:role
        [:gmd:CI_RoleCode {:codeList (str (:ngdc iso/code-lists) "#CI_RoleCode")
                           :codeListValue "publisher"} "publisher"]]]]))

(defn convert-online-resource
  "Convert the online-resource in umm to the related fields in xml"
  [c]
  (when-let [online-resource (:OnlineResource (first (:CollectionCitations c)))]
     [:gmd:citedResponsibleParty
      [:gmd:CI_ResponsibleParty
       [:gmd:contactInfo
        [:gmd:CI_Contact
         [:gmd:onlineResource
          [:gmd:CI_OnlineResource
             [:gmd:linkage
              [:gmd:URL (:Linkage online-resource)]]
             [:gmd:protocol
              [:gco:CharacterString (:Protocol online-resource)]]
             [:gmd:applicationProfile
              [:gco:CharacterString (:ApplicationProfile online-resource)]]
           (when-let [name (:Name online-resource)]
             [:gmd:name
              [:gco:CharacterString name]])
           (when-let [description (:Description online-resource)]
             [:gmd:description
              [:gco:CharacterString description]])
           [:gmd:function
            [:gmd:CI_OnLineFunctionCode {:codeList (str (:ngdc iso/code-lists) "#CI_RoleCode")
                                         :codeListValue (:Function online-resource)}(:Function online-resource)]]]]]]
       [:gmd:role
        [:gmd:CI_RoleCode {:codeList (str (:ngdc iso/code-lists) "#CI_RoleCode")
                           :codeListValue "resourceProvider"} "resourceProvider"]]]]))

(defn convert-data-presentation-form
  "Convert the data presentation form in umm to the related fields in xml"
  [c]
  (when-let [presentation-form (:DataPresentationForm (first (:CollectionCitations c)))]
    [:gmd:presentationForm
     [:gmd:CI_PresentationFormCode {:codeList ""
                                    :codeListValue presentation-form} presentation-form]]))

(defn convert-series-name-and-issue-id
  "Convert the series name and issue identification in umm to the related fields in xml"
  [c]
  (let [series-name (:SeriesName (first (:CollectionCitations c)))
        issue-identification (:IssueIdentification (first (:CollectionCitations c)))]
    (when (or series-name issue-identification)
     [:gmd:series
      [:gmd:CI_Series
       (when series-name
         [:gmd:name
          [:gco:CharacterString series-name]])
       (when issue-identification
         [:gmd:issueIdentification
          [:gco:CharacterString issue-identification]])]])))

(defn convert-other-citation-details
  "Convert the other citation details in umm to the related fields in xml"
  [c]
  (when-let [other-details (:OtherCitationDetails (first (:CollectionCitations c)))]
    [:gmd:otherCitationDetails
     [:gco:CharacterString other-details]]))
