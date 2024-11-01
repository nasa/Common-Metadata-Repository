(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.doi
  "Functions for parsing UMM DOI records out of ISO 19115 and ISO SMAP XML documents."
  (:require
   [clojure.string :as string]
   [cmr.common.date-time-parser :as date-time-parser]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer [value-of]]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.iso19115-2-util :as iso-util]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.shared-iso-parsing-util :as iso-xml-parsing-util]))

(def doi-namespace
  "DOI namespace."
  "gov.nasa.esdis.umm.doi")

(def doi-in-description
  "This is used to check if the constant DOI is contained in the identification description element."
  "DOI")

(def previous-version-path
  "Used to find the previous version contained in the aggregationInfo section. This path is for MENDS and
  since SMAP doesn't use previous version this is OK."
  "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:aggregationInfo/gmd:MD_AggregateInformation/")

(defn- is-doi-identified-by-attribute?
  "Checks to see if the Anchor contains an <gmx:Anchor xlink:title=\"DOI\" attribute. If it does then the
   identifier is a DOI. Returns true if sucessful or nil if not."
  [gmd-id]
  (let [attrs (get (first (select gmd-id "gmd:MD_Identifier/gmd:code/gmx:Anchor")) :attrs)]
    (some #(and (= :xlink/title (key %))
                (= "DOI" (val %)))
          attrs)))

(defn- is-doi-field?
  "Returns true if the given gmd-id is for a DOI field."
  [gmd-id doi-namespace]
  (or (= (value-of gmd-id "gmd:MD_Identifier/gmd:codeSpace/gco:CharacterString") doi-namespace)
      (if-some [x (value-of gmd-id "gmd:MD_Identifier/gmd:description/gco:CharacterString")]
        (string/includes? x doi-in-description)
        false)
      (is-doi-identified-by-attribute? gmd-id)))

(defn- parse-explanation
  "Parses explanation for missing reason out of description."
  [description]
  (when description
    (when-let [explanation-map (iso-xml-parsing-util/convert-iso-description-string-to-map
                                 description
                                 (re-pattern "Explanation:"))]
      (string/trim (:Explanation explanation-map)))))

(defn parse-previous-version
  "Parses out the first previous version from the ISO record."
  [doc]
  (first
   (for [p-version (select doc previous-version-path)
         :let [assoc-type (value-of p-version "gmd:associationType/gmd:DS_AssociationTypeCode")
               assoc-type (when assoc-type
                            (string/lower-case (string/trim assoc-type)))
               gmd-id (first (select p-version "gmd:aggregateDataSetIdentifier/"))]
         :when (and (is-doi-field? gmd-id (str doi-namespace ".previousversion"))
                    (= "doi_previous_version" assoc-type))]
     {:DOI (iso-util/char-string-value p-version "gmd:aggregateDataSetIdentifier/gmd:MD_Identifier/gmd:code")
      :Version (iso-util/char-string-value p-version "gmd:aggregateDataSetIdentifier/gmd:MD_Identifier/gmd:authority/gmd:CI_Citation/gmd:edition")
      :Description (iso-util/char-string-value p-version "gmd:aggregateDataSetIdentifier/gmd:MD_Identifier/gmd:authority/gmd:CI_Citation/gmd:otherCitationDetails")
      :Published (date-time-parser/try-parse-datetime
                  (or (value-of p-version "gmd:aggregateDataSetIdentifier/gmd:MD_Identifier/gmd:authority/gmd:CI_Citation/gmd:editionDate/gco:Date")
                      (value-of p-version "gmd:aggregateDataSetIdentifier/gmd:MD_Identifier/gmd:authority/gmd:CI_Citation/gmd:editionDate/gco:DateTime")))})))

(defn parse-doi
  "There could be multiple CI_Citations. Each CI_Citation could contain multiple gmd:identifiers.
   Each gmd:identifier could contain at most ONE DOI. The doi-list below will contain something like:
   [[nil]
    [nil {:DOI \"doi1\" :Authority \"auth1\"} {:DOI \"doi2\" :Authority \"auth2\"}]
    [{:DOI \"doi3\" :Authority \"auth3\"]]
   We will pick the first DOI for now."
  [doc citation-base-xpath]
  (let [orgname-path (str "gmd:MD_Identifier/gmd:authority/gmd:CI_Citation/gmd:citedResponsibleParty/"
                          "gmd:CI_ResponsibleParty/gmd:organisationName/gco:CharacterString")
        indname-path (str "gmd:MD_Identifier/gmd:authority/gmd:CI_Citation/gmd:citedResponsibleParty/"
                          "gmd:CI_ResponsibleParty/gmd:individualName/gco:CharacterString")
        previous-version (parse-previous-version doc)
        doi-list (for [ci-ct (select doc citation-base-xpath)
                       gmd-id (select ci-ct "gmd:identifier")
                       :when (is-doi-field? gmd-id doi-namespace)
                       :let [doi-value (or (value-of
                                            gmd-id "gmd:MD_Identifier/gmd:code/gco:CharacterString")
                                           (value-of
                                            gmd-id "gmd:MD_Identifier/gmd:code/gmx:Anchor"))
                             explanation (parse-explanation
                                          (value-of gmd-id
                                                    "gmd:MD_Identifier/gmd:description/gco:CharacterString"))
                             attrs (get (first (select gmd-id "gmd:MD_Identifier/gmd:code")) :attrs)
                             missing-reason (when attrs
                                              (cond
                                                (string/includes? attrs "inapplicable") "Not Applicable"
                                                (string/includes? attrs "unknown") "Unknown"
                                                :else nil))]]
                   (util/remove-nil-keys
                     (if doi-value
                       {:DOI doi-value
                        :Authority (or (value-of gmd-id orgname-path)
                                       (value-of gmd-id indname-path))}
                       {:MissingReason (when missing-reason
                                         missing-reason)

                        :Explanation explanation})))]
    (if (first doi-list)
      (if previous-version
        (merge (first doi-list)
               {:PreviousVersion previous-version})
        (first doi-list))
      {:MissingReason "Unknown"
       :Explanation "It is unknown if this record has a DOI."})))

(def associated-doi-types
  "A list of other associated Types other than associated-metadata used to find associated-metadata data."
  ["associateddoi" "childdataset" "collaborativeotheragency" "fieldcampaign" "parentdataset" "relateddataset" "other" "ispreviousversionof" "isnewversionof"])

(def associated-doi-code-umm-map
  "Map of associated DOI codes with their English names for the ISO code types."
  {"associateddoi" nil
   "childdataset" "Child Dataset"
   "collaborativeotheragency" "Collaborative/Other Agency"
   "fieldcampaign" "Field Campaign"
   "parentdataset" "Parent Dataset"
   "relateddataset" "Related Dataset"
   "other" "Other"
   "ispreviousversionof" "IsPreviousVersionOf"
   "isnewversionof" "IsNewVersionOf"})

(defn parse-associated-dois
  "Parse out the associated DOIs from the ISO MENDS/SMAP document."
  [doc associated-doi-xpath]
  (let [assocs
        (seq
         (util/remove-nils-empty-maps-seqs
          (for [assoc (select doc associated-doi-xpath)
                :let [assoc-code-list (select assoc "gmd:associationType/gmd:DS_AssociationTypeCode")
                      assoc-type (value-of (first assoc-code-list) "@codeListValue")
                      assoc-type (when assoc-type
                                   (string/trim assoc-type))
                      l-assoc-type (util/safe-lowercase assoc-type)
                      doi (iso-util/char-string-value assoc "gmd:aggregateDataSetIdentifier/gmd:MD_Identifier/gmd:code")]
                :when (and doi (some #(= l-assoc-type %) associated-doi-types))]
            {:DOI doi
             :Title (iso-util/char-string-value assoc "gmd:aggregateDataSetName/gmd:CI_Citation/gmd:title")
             :Authority (iso-util/char-string-value assoc (str "gmd:aggregateDataSetIdentifier/gmd:MD_Identifier"
                                                               "/gmd:authority/gmd:CI_Citation/gmd:citedResponsibleParty"
                                                               "/gmd:CI_ResponsibleParty/gmd:organisationName"))
             :Type (get associated-doi-code-umm-map l-assoc-type)
             :DescriptionOfOtherType (when (= "other" l-assoc-type)
                                       (when-let [other-desc (value-of assoc "gmd:associationType/gmd:DS_AssociationTypeCode")]
                                         (string/trim other-desc)))})))]
    (when assocs
      (into [] assocs))))
