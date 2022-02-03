(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.use-constraints
  "Functions for parsing UMM use constraints records out of ISO XML documents."
  (:require
   [clojure.string :as string]
   [cmr.common.log :refer (warn)]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer :all]
   [cmr.umm-spec.models.umm-collection-models :as umm-coll-models]
   [cmr.umm-spec.models.umm-common-models :as umm-cmn-models]
   [cmr.umm-spec.util :as su]))

(defn- get-license-value
  "Get the first LicenseUrl or LicenseText or FreeAndOpenData info from a list of other-constraints.
   get the char-string-value from each other-constraint, Parse the label part out,
   remove all the spaces from the label, if the lower-case of the label is the same as
   the license-label, then this other-constraint is the license-constraint."
  [other-constraints-list license-label]
  (when-let [value (some #(when-let [value (value-of % "gco:CharacterString")]
                            (when-let [label (string/replace (first (string/split value #":")) #" " "")]
                              (when (= license-label (string/lower-case label))
                                value)))
                         other-constraints-list)]
    (let [label-pattern (re-pattern (str (first (string/split value #":")) ":"))
          license-value (second (string/split value label-pattern))]
      license-value)))

(defn- get-description-value
  "Get description value from the list of gmd:useLimitation values.
   Pick the first that doesn't start with Restriction Comment:."
  [description-list sanitize?]
  (when-let [value (some #(when-let [^String value (value-of % "gco:CharacterString")]
                            (when-not (or (.contains value "Restriction Comment:")
                                          (.contains value "Access Constraints Description:"))
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
          free-and-open (let [fo (get-license-value other-constraints-list "freeandopendata")]
                          (when fo
                            (string/trim fo)))
          linkage (get-license-value other-constraints-list "licenseurl")
          license-text (when-not linkage
                         (get-license-value other-constraints-list "licensetext"))
          use-constraints (when (or description linkage license-text free-and-open)
                            [(umm-coll-models/map->UseConstraintsType
                               {:Description (when description
                                               description)
                                :FreeAndOpenData (when (or (= free-and-open "true")
                                                           (= free-and-open "false"))
                                                   (Boolean/valueOf free-and-open))
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
   We want to find the first Description, the first LicenseUrl, the first LicenseText, and the
   first FreeAndOpenData and then return UseConstraints
   as one of the combinations in the following order, if the values exist:
   {:Description first-desc :LicenseUrl first-lic-url}, {:Description first-desc :LicenseText first-lic-text},
   {:Description first-desc}, {:LicenseUrl first-lic-url} and {:LicenseText first-lic-text}. "
  [doc constraints-xpath sanitize?]
  (let [constraints-list (seq (select doc constraints-xpath))
        use-constraints-list (get-use-constraints constraints-list sanitize?)
        first-description (:Description (some #(when (:Description %) %) use-constraints-list))
        first-license-url (:LicenseURL (some #(when (:LicenseURL %) %) use-constraints-list))
        first-license-text (:LicenseText (some #(when (:LicenseText %) %) use-constraints-list))
        first-free-and-open (:FreeAndOpenData (some #(when (some? (:FreeAndOpenData %)) %) use-constraints-list))]
    (when (or first-description (some? first-free-and-open) first-license-url first-license-text)
      (umm-coll-models/map->UseConstraintsType
        {:Description first-description
         :FreeAndOpenData first-free-and-open
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
      (string/join matches))))

(defn parse-ac
  "Parse the passed in ISO XML access constraints description and value using the
  passed in xpath and regular expression in this function."
  [doc constraints-xpath add-xpath reg-ex]
  (regex-value doc (str constraints-xpath add-xpath "/gco:CharacterString") reg-ex))

;; These are the old and new regex keys to parse on for access constraint values and descriptions.
(def old-access-value #"(?s)Restriction Flag:(.+)")
(def new-access-value #"(?s)Access Constraints Value:(.+)")
(def old-access-desc #"(?s)Restriction Comment:(.+)")
(def new-access-desc #"(?s)Access Constraints Description:(.+)")

;; These are the xpath parts to where to look for access constraint elements.
(def use-limitation-xpath "/gmd:useLimitation")
(def other-constraints-xpath "/gmd:otherConstraints")

(defn parse-access-constraints
  "If both value and Description are nil, return nil.
   Otherwise, if Description is nil, assoc it with su/not-provided.
   The boolean passed to parse-ac is whether or not the parsing is for :Value or :Description."
  [doc constraints-xpath sanitize?]
  (let [value (or (parse-ac doc constraints-xpath other-constraints-xpath old-access-value)
                  (parse-ac doc constraints-xpath other-constraints-xpath new-access-value))
        access-constraints-record
        {:Description (su/truncate
                       (or (parse-ac doc constraints-xpath use-limitation-xpath old-access-desc)
                           (parse-ac doc constraints-xpath use-limitation-xpath new-access-desc)
                           (parse-ac doc constraints-xpath other-constraints-xpath old-access-desc)
                           (parse-ac doc constraints-xpath other-constraints-xpath new-access-desc))
                       su/ACCESSCONSTRAINTS_DESCRIPTION_MAX
                       sanitize?)
         :Value (when value
                  (try (Double/parseDouble (string/trim value))
                    (catch Exception e
                      (warn (str "Exception thrown while trying to parse a non parsable ISO access "
                                 "constraint value number. The document is " doc)))))}]
    (when (seq (util/remove-nil-keys access-constraints-record))
      (update access-constraints-record :Description #(su/with-default % sanitize?)))))
