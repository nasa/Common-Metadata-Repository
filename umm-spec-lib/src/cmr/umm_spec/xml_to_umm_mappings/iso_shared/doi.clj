(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.doi
  "Functions for parsing UMM DOI records out of ISO 19115 and ISO SMAP XML documents."
  (:require
   [clojure.string :as string]
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
  [gmd-id]
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
        doi-list (for [ci-ct (select doc citation-base-xpath)
                       gmd-id (select ci-ct "gmd:identifier")
                       :when (is-doi-field? gmd-id)
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
      (first doi-list)
      {:MissingReason "Unknown"
       :Explanation "It is unknown if this record has a DOI."})))

(defn parse-associated-dois
  "Parse out the associated DOIs from the ISO MENDS/SMAP document."
  [doc associated-doi-xpath]
  (let [assocs
         (seq
           (for [assoc (select doc associated-doi-xpath)
                 :let [assoc-type (value-of assoc "gmd:associationType/gmd:DS_AssociationTypeCode")
                       assoc-type (when assoc-type
                                    (string/trim assoc-type))
                       doi (iso-util/char-string-value assoc "gmd:aggregateDataSetIdentifier/gmd:MD_Identifier/gmd:code")]
                 :when (and doi (= "associatedDOI" assoc-type))]
             {:DOI doi
              :Title (iso-util/char-string-value assoc "gmd:aggregateDataSetName/gmd:CI_Citation/gmd:title")
              :Authority (iso-util/char-string-value assoc (str "gmd:aggregateDataSetIdentifier/gmd:MD_Identifier"
                                                                "/gmd:authority/gmd:CI_Citation/gmd:citedResponsibleParty"
                                                                "/gmd:CI_ResponsibleParty/gmd:organisationName"))}))]
    (when assocs
      (into [] assocs))))
