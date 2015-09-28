(ns cmr.umm-spec.iso19115-2-util
  "Defines common xpaths and functions used by various namespaces in ISO19115-2."
  (:require [cmr.umm-spec.iso-utils :as iso-utils]
            [cmr.umm-spec.xml.parse :refer :all]
            clojure.set
            [clojure.string :as str]))

(def long-name-xpath
  "gmi:identifier/gmd:MD_Identifier/gmd:description/gco:CharacterString")

(def short-name-xpath
  "gmi:identifier/gmd:MD_Identifier/gmd:code/gco:CharacterString")

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

(defn tiling-system-coding-params
  "Returns ISO tiling system encoding string parameters for given tiling system map."
  [tiling-system]
  (condp #(.contains %2 %1) (:TilingIdentificationSystemName tiling-system)
    "CALIPSO" ["o" "p" ","]
    "MISR"    ["p" "b" "-"]
    "MODIS"   ["h" "v" "-"]
    "WRS"     ["p" "r" "-"]
    ["x" "y" "-"]))

(defn tiling-system-string
  "Returns an encoded ISO tiling system coordinate string from the given UMM tiling system. The
  coordinate 1 and coordinate 2 prefixes and separator may be specified, or else they will be looked
  up based on the tiling system name."
  ([tiling-system p1 p2 sep]
   (let [{{c1-min :MinimumValue
           c1-max :MaximumValue} :Coordinate1
           {c2-min :MinimumValue
            c2-max :MaximumValue} :Coordinate2} tiling-system]
     (str p1 c1-min
          (when c1-max
            (str sep c1-max))
          p2 c2-min
          (when c2-max
            (str sep c2-max)))))
  ([tiling-system]
   (apply tiling-system-string tiling-system (tiling-system-coding-params tiling-system))))

(defn parse-tiling-system-coordinates
  "Returns a map containing :Coordinate1 and :Coordinate2 from an encoded ISO tiling system
  parameter string."
  [tiling-system-str]
  (let [[c1 c2] (for [[_ min-str max-str] (re-seq #"(-?\d+\.?\d*)[-,]?(-?\d+\.?\d*)?"
                                                  tiling-system-str)]
                  {:MinimumValue (Double. min-str)
                   :MaximumValue (when max-str (Double. max-str))})]
    {:Coordinate1 c1
     :Coordinate2 c2}))

(def echo-attributes-info
  [:eos:otherPropertyType
   [:gco:RecordType {:xlink:href "http://earthdata.nasa.gov/metadata/schema/eos/1.0/eos.xsd#xpointer(//element[@name='AdditionalAttributes'])"}
    "Echo Additional Attributes"]])

(defn generate-title
  "Returns an ISO title string from the ShortName and LongName fields of the given record."
  [record]
  (let [{short-name :ShortName long-name :LongName} record]
    (if (seq long-name) (str short-name iso-utils/keyword-separator long-name) short-name)))

(defn generate-descriptive-keywords
  "Returns the content generator instructions for the given descriptive keywords."
  ([keywords]
   (generate-descriptive-keywords nil keywords))
  ([keyword-type keywords]
   (when (seq keywords)
     [:gmd:descriptiveKeywords
      [:gmd:MD_Keywords
       (for [keyword keywords]
         [:gmd:keyword [:gco:CharacterString keyword]])
       (when keyword-type
         [:gmd:type
          [:gmd:MD_KeywordTypeCode
           {:codeList (str (:ngdc code-lists) "#MD_KeywordTypeCode")
            :codeListValue keyword-type} keyword-type]])
       [:gmd:thesaurusName {:gco:nilReason "unknown"}]]])))
