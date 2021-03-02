(ns cmr.umm-spec.umm-to-xml-mappings.iso-shared.doi
  "Functions for generating ISO MENDS/SMAP XML elements from UMM DOI and Associated DOI elements."
  (:require
    [cmr.common.util :as util]))

(def authority-code-list-url (str "https://cdn.earthdata.nasa.gov/iso"
                                  "/resources/Codelist/gmxCodelists.xml#CI_RoleCode"))
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

(def assoc-doi-code-list-url (str "https://cdn.earthdata.nasa.gov/iso"
                                  "/resources/Codelist/gmxCodelists.xml#DS_AssociationTypeCode"))
(def assoc-doi-code-list-value "associatedDOI")

(defn generate-associated-dois
  "Generate from a UMM-C record the AssociatedDOIs into the ISO MENDS/SMAP form."
  [c]
  (for [assoc (:AssociatedDOIs c)
        :let [title (:Title assoc)
              authority (:Authority assoc)]]
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
       [:gmd:DS_AssociationTypeCode {:codeList assoc-doi-code-list-url
                                     :codeListValue assoc-doi-code-list-value}
          assoc-doi-code-list-value]]]]))
