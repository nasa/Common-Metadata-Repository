(ns cmr.umm-spec.iso19115-2-util
  "Defines common xpaths and functions used by various namespaces in ISO19115-2."
  (:require
   [clojure.string :as str]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select]])
  (:require
   clojure.set))

(def long-name-xpath
  "gmi:identifier/gmd:MD_Identifier/gmd:description/gco:CharacterString")

(def short-name-xpath
  "gmi:identifier/gmd:MD_Identifier/gmd:code/gco:CharacterString")

;; relative to gmi:childOperation
(def campaign-xpath
  "gmi:MI_Operation/gmi:identifier/gmd:MD_Identifier/gmd:code/gco:CharacterString") 

(def start-end-date-xpath
  "gmi:description/gco:CharacterString")


(defn char-string-value
  "Utitlity function to return the gco:CharacterString element value of the given parent xpath."
  [element parent-xpath]
  (value-of element (str parent-xpath "/gco:CharacterString")))

(def code-lists
  "The uri base of the code-lists used in the generation of ISO xml"
  {:earthdata "http://earthdata.nasa.gov/metadata/resources/Codelists.xml"
   :ngdc "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml"
   :iso "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml"})

(def iso-date-type-codes
  "A map of UMM date type enum values to ISO date type codes."
  {"CREATE" "creation"
   "UPDATE" "revision"
   "REVIEW" "lastRevision"
   "DELETE" "unavailable"})

(def umm-date-type-codes
  "A map of ISO date type codes to UMM date type enum values. Inverse of iso-date-type-codes."
  (clojure.set/map-invert iso-date-type-codes))

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
 (clojure.set/map-invert iso-metadata-type-definitions))

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
  #"\s?>\s?")

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

(def extent-xpath
  "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:extent/gmd:EX_Extent")

(defn- parse-key-val-str
  "Returns a map of string keys and values from a comma-separated list of equals-separated pairs."
  [description-str]
  (when (and (string? description-str)
             (not (str/blank? description-str)))
    (into {}
          (for [pair-str (str/split description-str #",")]
            (let [[k v] (str/split pair-str #"=")]
              [(str/trim k) (str/trim (or v ""))])))))

(defn sanitize-value
  "Returns a key-value string value without \",\" or \"=\" characters."
  [x]
  (str/replace x #"[,=]" ""))

(defn key-val-str
  "Returns map encoded as ISO key-value string e.g. for use in the extent description."
  [m]
  (str/join ","
            (for [[k v] m]
              (str k "=" (sanitize-value v)))))

(defn get-extent-info-map
  "Returns a map of equal-separated pairs from the comma-separated list in the ISO document's extent
  description element."
  [doc]
  (let [[extent-el] (select doc extent-xpath)]
    (parse-key-val-str (value-of extent-el "gmd:description/gco:CharacterString"))))

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
