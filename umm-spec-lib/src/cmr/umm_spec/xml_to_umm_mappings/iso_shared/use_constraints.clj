(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.use-constraints
  "Functions for parsing UMM use constraints records out of ISO XML documents."
  (:require
   [clojure.string :as str]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer :all]
   [cmr.umm-spec.date-util :as date]
   [cmr.umm-spec.iso19115-2-util :refer :all]
   [cmr.umm-spec.models.umm-collection-models :as umm-coll-models]
   [cmr.umm-spec.models.umm-common-models :as umm-cmn-models]
   [cmr.umm-spec.url :as url]
   [cmr.umm-spec.util :as su]))

(defn- get-license-value
  "Get the first LicenseUrl or LicenseText info from a list of other-constraints.
   get the char-string-value from each other-constraint, Parse the label part out,
   remove all the spaces from the label, if the lower-case of the label is the same as
   the license-label, then this other-constraint is the license-constraint."
  [other-constraints-list license-label]
  (when-let [value (some #(when-let [value (value-of % "gco:CharacterString")]
                            (when-let [label (str/replace (first (str/split value #":")) #" " "")]
                              (when (= license-label (str/lower-case label))
                                value)))
                         other-constraints-list)]
    (let [label-pattern (re-pattern (str (first (str/split value #":")) ":"))
          license-value (second (str/split value label-pattern))]
      license-value)))

(defn- get-description-value
  "Get description value from the list of gmd:useLimitation values.
   Pick the first that doesn't start with Restriction Comment:."
  [description-list sanitize?]
  (when-let [value (some #(when-let [^String value (value-of % "gco:CharacterString")]
                            (when-not (.contains value "Restriction Comment:")
                              value))
                         description-list)]
    (su/truncate value su/USECONSTRAINTS_MAX sanitize?)))

(defn- get-use-constraints
  "Get the use constraints."
  [constraints-list sanitize?]
  (loop [cnt (count constraints-list) [constraints & t] constraints-list use-constraints-list []]
    (let [description-list (select constraints "gmd:useLimitation")
          other-constraints-list (select constraints "gmd:otherConstraints")
          description (get-description-value description-list sanitize?)
          linkage (get-license-value other-constraints-list "licenseurl")
          license-text (when-not linkage
                         (get-license-value other-constraints-list "licensetext"))
          use-constraints (when (or description linkage license-text)
                            [(umm-coll-models/map->UseConstraintsType
                               {:Description (when description
                                               description)
                                :LicenseURL (when linkage
                                              (umm-cmn-models/map->OnlineResourceType
                                                {:Linkage linkage}))
                                :LicenseText license-text})])]
      ;; Go through constraints-list to get all the use-constraints.
      (if (zero? cnt)
        use-constraints-list
        (recur (dec cnt) t (concat use-constraints-list use-constraints))))))

(defn parse-use-constraints
  "Parse the use constraints from XML resource constraint.
   constraints-xpath is:
   /gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:resourceConstraints/gmd:MD_LegalConstraints.
   We want to find the first Description, the first LicenseUrl and the first LicenseText, and then return UseConstraints
   as one of the combinations in the following order, if the values exist:
   {:Description first-desc :LicenseUrl first-lic-url}, {:Description first-desc :LicenseText first-lic-text},
   {:Description first-desc}, {:LicenseUrl first-lic-url} and {:LicenseText first-lic-text}. "
  [doc constraints-xpath sanitize?]
  (let [constraints-list (seq (select doc constraints-xpath))
        use-constraints-list (get-use-constraints constraints-list sanitize?)
        first-description (:Description (some #(when (:Description %) %) use-constraints-list))
        first-license-url (:LicenseURL (some #(when (:LicenseURL %) %) use-constraints-list))
        first-license-text (:LicenseText (some #(when (:LicenseText %) %) use-constraints-list))]
    (when (or first-description first-license-url first-license-text)
      (umm-coll-models/map->UseConstraintsType
        {:Description first-description
         :LicenseURL first-license-url
         :LicenseText (when-not first-license-url
                        first-license-text)}))))

(defn- regex-value
  "Utitlity function to return the value of the element that matches the given xpath and regex."
  [element xpath regex]
  (when-let [elements (select element xpath)]
    (when-let [matches (seq
                         (for [match-el elements
                               :let [match (re-matches regex (text match-el))]
                               :when match]
                           ;; A string response implies there is no group in the regular expression and the
                           ;; entire matching string is returned and if there is a group in the regular
                           ;; expression, the first group of the matching string is returned.
                           (if (string? match) match (second match))))]
      (str/join matches))))

(defn parse-access-constraints
  "If both value and Description are nil, return nil.
  Otherwise, if Description is nil, assoc it with su/not-provided"
  [doc constraints-xpath sanitize?]
  (let [value (regex-value doc (str constraints-xpath
                                 "/gmd:otherConstraints/gco:CharacterString")
               #"(?s)Restriction Flag:(.+)")
        access-constraints-record
        {:Description (su/truncate
                       (regex-value doc (str constraints-xpath
                                         "/gmd:useLimitation/gco:CharacterString")
                         #"(?s)Restriction Comment: (.+)")
                       su/ACCESSCONSTRAINTS_DESCRIPTION_MAX
                       sanitize?)
         :Value (when value
                 (Double/parseDouble value))}]
    (when (seq (util/remove-nil-keys access-constraints-record))
      (update access-constraints-record :Description #(su/with-default % sanitize?)))))
