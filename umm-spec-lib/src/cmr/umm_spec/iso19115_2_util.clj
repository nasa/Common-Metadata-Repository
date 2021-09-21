(ns cmr.umm-spec.iso19115-2-util
  "Defines common xpaths and functions used by various namespaces in ISO19115-2."
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select]]))

(def long-name-xpath
  "gmi:identifier/gmd:MD_Identifier/gmd:description/gco:CharacterString")

(def short-name-xpath
  "gmi:identifier/gmd:MD_Identifier/gmd:code/gco:CharacterString")

;; relative to gmi:childOperation
(def campaign-xpath
  "gmi:MI_Operation/gmi:identifier/gmd:MD_Identifier/gmd:code/gco:CharacterString")

(defn char-string-value
  "Utitlity function to return the gco:CharacterString element value of the given parent xpath."
  [element parent-xpath]
  (value-of element (str parent-xpath "/gco:CharacterString")))

(defn gmx-anchor-value
  "Utitlity function to return the gmx:Anchor element value of the given parent xpath."
  [element parent-xpath]
  (value-of element (str parent-xpath "/gmx:Anchor")))

(def code-lists
  "The uri base of the code-lists used in the generation of ISO xml"
  {:earthdata "http://earthdata.nasa.gov/metadata/resources/Codelists.xml"
   :ngdc "http://data.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml"
   :iso "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml"})

(def iso-date-type-codes
  "A map of UMM date type enum values to ISO date type codes."
  {"CREATE" "creation"
   "UPDATE" "revision"
   "REVIEW" "lastRevision"
   "DELETE" "unavailable"})

(def umm-date-type-codes
  "A map of ISO date type codes to UMM date type enum values. Inverse of iso-date-type-codes."
  (set/map-invert iso-date-type-codes))

(def iso-metadata-type-definitions
 "A map of UMM date type enums to ISO metadata date definitions"
 {"CREATE" "Create Date"
  "UPDATE" "Update Date"
  "REVIEW" "Review Date"
  "DELETE" "Delete Date"})

(defn get-iso-metadata-type-name
 "Get the ISO metadata date name for the UMM date type enum - name is the type definition prefixed
 with 'Metadata'"
 [metadata-type]
 (when-let [type-definition (get iso-metadata-type-definitions metadata-type)]
  (str "Metadata " type-definition)))

(def umm-metadata-date-types
 "A map of ISO metadata date definitions to UMM date type enum"
 (set/map-invert iso-metadata-type-definitions))

(def eos-echo-attributes-info
  [:eos:otherPropertyType
   [:gco:RecordType
    {:xlink:href
     "http://earthdata.nasa.gov/metadata/schema/eos/1.0/eos.xsd#xpointer(//element[@name='AdditionalAttributes'])"}
    "Echo Additional Attributes"]])

(def gmd-echo-attributes-info
  [:gmd:otherPropertyType
   [:gco:RecordType
    {:xlink:href
     "http://earthdata.nasa.gov/metadata/schema/eos/1.0/eos.xsd#xpointer(//element[@name='AdditionalAttributes'])"}
    "Echo Additional Attributes"]])

(def keyword-separator-split
  "Separator used to separate keyword into keyword fields"
  ;; Note: This is going to be changed to "\s*>\s*"" per CMR-3181
  ;; but requires changes to unit tests
  ;; Note: This now will also match "&gt;" to satisfy CMR-4192
  #"(\s?>\s?|\s?\Q&gt;\E\s?)")

(def keyword-separator-join
  "Separator used to join keyword fields into a keyword string"
  " > ")

(def version-description-separator
  "Separator used to join Abstract and VersionDescription"
  " Version Description: ")

(defn generate-title
  "Returns an ISO title string from the ShortName and LongName fields of the given record."
  [record]
  (let [{short-name :ShortName long-name :LongName} record]
    (if (seq long-name) (str short-name keyword-separator-join long-name) short-name)))

(def partial-spatial-extent-alt-xpath
  (str "/gmd:geographicElement"
       "/gmd:EX_GeographicDescription[@id='GranuleSpatialRepresentation']"
       "/gmd:geographicIdentifier/gmd:MD_Identifier"))

(defn- parse-key-val-str
  "Returns a map of string keys and values from a comma-separated list of equals-separated pairs."
  [description-str]
  (when (and (string? description-str)
             (not (string/blank? description-str)))
    (into {}
          (for [pair-str (string/split description-str #",")]
            (let [[k v] (string/split pair-str #"=")]
              [(string/trim k) (string/trim (or v ""))])))))

(defn sanitize-value
  "Returns a key-value string value without \",\" or \"=\" characters."
  [x]
  (string/replace x #"[,=]" ""))

(defn key-val-str
  "Returns map encoded as ISO key-value string e.g. for use in the extent description."
  [m]
  (string/join ","
            (for [[k v] m]
              (str k "=" (sanitize-value v)))))

(defn get-extent-info-map
  "Returns a map of equal-separated pairs from the comma-separated list in the ISO document's extent
  description element."
  [doc spatial-extent-xpath]
  (let [[extent-el] (select doc spatial-extent-xpath)
        extent-info-map (parse-key-val-str (value-of extent-el "gmd:description/gco:CharacterString"))]
    (if (get extent-info-map "SpatialGranuleSpatialRepresentation")
      extent-info-map
      (let [[extent-alt-el] (select doc (str spatial-extent-xpath partial-spatial-extent-alt-xpath))]
        (merge extent-info-map
               {"SpatialGranuleSpatialRepresentation"
                (value-of extent-alt-el "gmd:code/gco:CharacterString")})))))

(defn parse-data-dates
  "Parses the collection DataDates from the the collection document."
  [doc data-dates-xpath]
  (distinct (for [date-el (select doc data-dates-xpath)
                  :let [date (or (value-of date-el "gmd:date/gco:DateTime")
                                 (value-of date-el "gmd:date/gco:Date"))
                        date-type (umm-date-type-codes
                                   (value-of date-el "gmd:dateType/gmd:CI_DateTypeCode"))]
                  :when date-type]
              {:Date date
               :Type date-type})))

(defn safe-trim
  [value]
  (if (string? value)
    (string/trim value)
    value))
