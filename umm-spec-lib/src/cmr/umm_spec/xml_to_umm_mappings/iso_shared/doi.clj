(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.doi
  "Functions for parsing UMM DOI records out of ISO 19115 and ISO SMAP XML documents."
  (:require
   [clojure.string :as str]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer [value-of]]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.distributions-related-url :as iso-shared-distrib]))

(def doi-namespace
  "DOI namespace."
  "gov.nasa.esdis.umm.doi")

(def doi-in-description
  "This is used to check if the constant DOI is contained in the identification description element."
  "DOI")

(defn- is-doi-field?
  "Returns true if the given gmd-id is for a DOI field."
  [gmd-id]
  (or (= (value-of gmd-id "gmd:MD_Identifier/gmd:codeSpace/gco:CharacterString") doi-namespace)
      (if-some [x (value-of gmd-id "gmd:MD_Identifier/gmd:description/gco:CharacterString")]
        (str/includes? x doi-in-description)
        false)))

(defn- parse-explanation
  "Parses explanation for missing reason out of description."
  [description]
  (when description
    (when-let [explanation-index (util/get-index-or-nil description "Explanation:")]
      (let [explanation (subs description explanation-index)]
        (str/trim (subs explanation (inc (.indexOf explanation ":"))))))))

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
                                                    "gmd:MD_Identifier/gmd:description/gco:CharacterString"))]]
                   (util/remove-nil-keys
                    {:DOI doi-value
                     :Authority (or (value-of gmd-id orgname-path)
                                    (value-of gmd-id indname-path))
                     :MissingReason (when-not (seq doi-value)
                                      "Not Applicable")
                     :Explanation explanation}))]
    (first doi-list)))
