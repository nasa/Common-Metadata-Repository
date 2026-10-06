(ns cmr.umm-spec.umm-to-xml-mappings.iso-shared.doi
  "Functions for generating ISO MENDS/SMAP XML elements from UMM DOI and Associated DOI elements."
  (:require
   [cmr.common.date-time-parser :as date-time-parser]
   [cmr.common.util :as util]
   [cmr.umm-spec.iso19115-2-util :as iso-util]))

(def authority-code-list-url (str (:earthdata-iso iso-util/code-lists) "#CI_RoleCode"))
(def authority-code-list-value "authority")

(defn- generate-authority
  "Generate the authority section for an identifier."
  [authority]
  [:gmd:authority
   [:gmd:CI_Citation
    [:gmd:title {:gco:nilReason "inapplicable"}]
    [:gmd:date {:gco:nilReason "inapplicable"}]
    [:gmd:citedResponsibleParty
     [:gmd:CI_ResponsibleParty
      [:gmd:organisationName
       [:gco:CharacterString authority]]
      [:gmd:role
       [:gmd:CI_RoleCode {:codeList authority-code-list-url
                          :codeListValue authority-code-list-value} authority-code-list-value]]]]]])

(defn generate-doi
  "Returns the DOI field."
  [c]
  (let [doi (util/remove-nil-keys (get-in c [:DOI]))]
    (if (seq (:DOI doi))
      [:gmd:identifier
       [:gmd:MD_Identifier
         (when-let [authority (:Authority doi)]
           (generate-authority authority))
         [:gmd:code [:gco:CharacterString (:DOI doi)]]
         [:gmd:codeSpace [:gco:CharacterString "gov.nasa.esdis.umm.doi"]]
         [:gmd:description [:gco:CharacterString "DOI"]]]]
      (when (:MissingReason doi)
        (if (= "Not Applicable" (:MissingReason doi))
          [:gmd:identifier
           [:gmd:MD_Identifier
             [:gmd:code {:gco:nilReason "inapplicable"}]
             [:gmd:codeSpace [:gco:CharacterString "gov.nasa.esdis.umm.doi"]]
             (when-let [explanation (:Explanation doi)]
               [:gmd:description [:gco:CharacterString (str "Explanation: " explanation)]])]]
          [:gmd:identifier
           [:gmd:MD_Identifier
             [:gmd:code {:gco:nilReason "unknown"}]
             [:gmd:codeSpace [:gco:CharacterString "gov.nasa.esdis.umm.doi"]]
             (when-let [explanation (:Explanation doi)]
               [:gmd:description [:gco:CharacterString (str "Explanation: " explanation)]])]])))))

(def assoc-doi-code-list-url (str (:earthdata-iso iso-util/code-lists) "#DS_AssociationTypeCode"))
(def assoc-doi-code-list-value "associatedDOI")
(def assoc-doi-type-code-map
  {"Child Dataset" ["childDataset" "Child_Dataset"]
   "Collaborative/Other Agency" ["collaborativeOtherAgency" "Collaborative_Other_Agency"]
   "Field Campaign" ["fieldCampaign" "Field_Campaign"]
   "Parent Dataset" ["parentDataset" "Parent_Dataset"]
   "Related Dataset" ["relatedDataset" "Related_Dataset"]
   "Other" ["other" "Other"]
   "IsPreviousVersionOf" ["isPreviousVersionOf" "Is_Previous_Version_Of"]
   "IsNewVersionOf" ["isNewVersionOf" "Is_New_Version_Of"]
   "IsDescribedBy" ["isDescribedBy" "Is_Described_By"]})

(defn generate-associated-dois
  "Generate from a UMM-C record the AssociatedDOIs into the ISO MENDS/SMAP form."
  [c]
  (for [assoc (:AssociatedDOIs c)
        :let [title (:Title assoc)
              authority (:Authority assoc)
              umm-type (:Type assoc)
              type-array (when umm-type
                           (get assoc-doi-type-code-map umm-type))
              code-list-value (when type-array
                                (first type-array))
              code-value (when type-array
                           (last type-array))
              desc-of-type-other (when (= "other" code-list-value)
                                   (:DescriptionOfOtherType assoc))]]
    [:gmd:aggregationInfo
     [:gmd:MD_AggregateInformation
      (when title
        [:gmd:aggregateDataSetName
         [:gmd:CI_Citation
          [:gmd:title
           [:gco:CharacterString title]]
          [:gmd:date {:gco:nilReason "inapplicable"}]]])
      [:gmd:aggregateDataSetIdentifier
       [:gmd:MD_Identifier
        (when authority
          (generate-authority authority))
        [:gmd:code
         [:gco:CharacterString (:DOI assoc)]]
        [:gmd:codeSpace
         [:gco:CharacterString "gov.nasa.esdis.umm.associateddoi"]]
        [:gmd:description
         [:gco:CharacterString "Associated DOI"]]]]
      [:gmd:associationType
       (if umm-type
         (if desc-of-type-other
           [:gmd:DS_AssociationTypeCode {:codeList (str (:earthdata iso-util/code-lists) "#EOS_AssociationTypeCode")
                                         :codeListValue code-list-value}
            desc-of-type-other]
           [:gmd:DS_AssociationTypeCode {:codeList (str (:earthdata iso-util/code-lists) "#EOS_AssociationTypeCode")
                                         :codeListValue code-list-value}
            code-value])
         [:gmd:DS_AssociationTypeCode {:codeList assoc-doi-code-list-url
                                       :codeListValue assoc-doi-code-list-value}
          assoc-doi-code-list-value])]]]))

(defn generate-previous-version
  "Generate from a UMM-C record the Previous Version into the ISO form."
  [c]
  (for [prev-version (get-in c [:DOI :PreviousVersion])
        :let [doi (:DOI prev-version)
              description (:Description prev-version)
              version (:Version prev-version)
              published (:Published prev-version)
              beginning (:BeginningDateTime prev-version)
              ending (:EndingDateTime prev-version)
              deprecated (:DeprecatedDateTime prev-version)
              coll-progress (:CollectionProgress prev-version)]
        :when doi]
    [:gmd:aggregationInfo
     [:gmd:MD_AggregateInformation
      [:gmd:aggregateDataSetIdentifier
       [:gmd:MD_Identifier
        (when (or description version published coll-progress beginning ending deprecated)
          [:gmd:authority
           [:gmd:CI_Citation
            (if coll-progress
              [:gmd:title [:gco:CharacterString coll-progress]]
              [:gmd:title {:gco:nilReason "inapplicable"}])

            ;; Map the new dates as CI_Date elements safely
            (let [date-elements
                  (remove nil?
                          [(when beginning
                             [:gmd:date
                              [:gmd:CI_Date
                               [:gmd:date [:gco:DateTime (if (string? beginning) beginning (date-time-parser/clj-time->date-time-str beginning))]]
                               [:gmd:dateType [:gmd:CI_DateTypeCode {:codeList (str (:earthdata iso-util/code-lists) "#CI_DateTypeCode") :codeListValue "validityBegins"} "validityBegins"]]]])
                           (when ending
                             [:gmd:date
                              [:gmd:CI_Date
                               [:gmd:date [:gco:DateTime (if (string? ending) ending (date-time-parser/clj-time->date-time-str ending))]]
                               [:gmd:dateType [:gmd:CI_DateTypeCode {:codeList (str (:earthdata iso-util/code-lists) "#CI_DateTypeCode") :codeListValue "validityExpires"} "validityExpires"]]]])
                           (when deprecated
                             [:gmd:date
                              [:gmd:CI_Date
                               [:gmd:date [:gco:DateTime (if (string? deprecated) deprecated (date-time-parser/clj-time->date-time-str deprecated))]]
                               [:gmd:dateType [:gmd:CI_DateTypeCode {:codeList (str (:earthdata iso-util/code-lists) "#CI_DateTypeCode") :codeListValue "deprecated"} "deprecated"]]]])])]
              (if (seq date-elements)
                date-elements
                [:gmd:date {:gco:nilReason "inapplicable"}]))

            ;; Make sure edition, published, and description only generate if they exist!
            (when version
              [:gmd:edition [:gco:CharacterString version]])
            (when published
              [:gmd:editionDate [:gco:DateTime (if (string? published) published (date-time-parser/clj-time->date-time-str published))]])
            (when description
              [:gmd:otherCitationDetails [:gco:CharacterString description]])]])
        [:gmd:code [:gco:CharacterString doi]]
        [:gmd:codeSpace [:gco:CharacterString "gov.nasa.esdis.umm.doi.previousversion"]]
        [:gmd:description [:gco:CharacterString "DOI Previous Version"]]]]
      [:gmd:associationType
       [:gmd:DS_AssociationTypeCode {:codeList (str (:earthdata iso-util/code-lists) "#EOS_AssociationTypeCode") :codeListValue "doiPreviousVersion"} "DOI_Previous_Version"]]]]))