(ns cmr.umm.iso-mends.collection.keyword
  "Contains functions for parsing and generating the ISO MENDS keyword related fields"
  (:require
   [clojure.data.xml :as x]
   [clojure.string :as s]
   [cmr.common.xml :as cx]
   [cmr.umm.iso-mends.collection.helper :as h]
   [cmr.umm.umm-collection :as c]))

(defn- xml-elem->keywords
  "Returns a list of UMM keywords from a parsed XML structure for the given match key string"
  [match-key id-elem]
  (let [keywords-elem (cx/elements-at-path id-elem [:descriptiveKeywords :MD_Keywords])
        matched-keyword-elem (first (filter
                                      #(= match-key (cx/string-at-path % [:type :MD_KeywordTypeCode]))
                                      keywords-elem))]
    (seq (cx/strings-at-path matched-keyword-elem [:keyword :CharacterString]))))

(defn xml-elem->spatial-keywords
  "Returns a UMM spatial-keywords from a parsed XML structure"
  [id-elem]
  (xml-elem->keywords "place" id-elem))

(defn xml-elem->temporal-keywords
  "Returns a UMM temporal-keywords from a parsed XML structure"
  [id-elem]
  (xml-elem->keywords "temporal" id-elem))

(defn xml-elem->data-center
  "Returns a UMM data center from a parsed XML structure"
  [id-elem]
  (first (xml-elem->keywords "dataCenter" id-elem)))

(defn- iso-keyword->umm-science-keyword
  "Returns the umm science keyword for the given iso keyword string"
  [science-keyword]
  ;; NOTE: ISO science-keywords cannot be parsed correclty from xml if there are '>' in the keywords
  (let [[category topic term variable-level-1 variable-level-2 variable-level-3 detailed-variable]
        (map #(if (= "NONE" %) nil %) (s/split science-keyword #">"))]
    (c/map->ScienceKeyword
      {:category category
       :topic (or topic c/not-provided)
       :term (or term c/not-provided)
       :variable-level-1 variable-level-1
       :variable-level-2 variable-level-2
       :variable-level-3 variable-level-3
       :detailed-variable detailed-variable})))

(defn xml-elem->ScienceKeywords
  "Returns a UMM science-keywords from a parsed XML structure"
  [id-elem]
  (seq (map iso-keyword->umm-science-keyword (xml-elem->keywords "theme" id-elem))))

(defn- keyword-type-attributes
  "Returns the keyword type attributes"
  [type]
  {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_KeywordTypeCode"
   :codeListValue type})

(defn- science-keyword->keyword
  "Returns the ISO keyword for the given science keyword"
  [science-keyword]
  (let [{:keys [category topic term variable-level-1 variable-level-2
                variable-level-3 detailed-variable]} science-keyword]
    (s/join ">" (map #(or % "NONE")
                     [category topic term variable-level-1 variable-level-2
                      variable-level-3 detailed-variable]))))

(defn generate-keywords
  "Generates the keywords XML. Note: we can add the gmd:thesaurusName stuff later, which is a
  lot of duplicate info on GCMD and is not required by the schema."
  [type keywords]
  (when-not (empty? keywords)
    (x/element
      :gmd:descriptiveKeywords {}
      (x/element
        :gmd:MD_Keywords {}
        (if (sequential? keywords)
          (for [keyword keywords] (h/iso-string-element :gmd:keyword keyword))
          (h/iso-string-element :gmd:keyword keywords))
        (x/element :gmd:type {}
                   (x/element :gmd:MD_KeywordTypeCode (keyword-type-attributes type) type))))))


(defn generate-science-keywords
  [science-keywords]
  (let [keywords (map science-keyword->keyword science-keywords)]
    (generate-keywords "theme" keywords)))

(defn generate-spatial-keywords
  [spatial-keywords]
  (generate-keywords "place" spatial-keywords))

(defn generate-temporal-keywords
  [temporal-keywords]
  (generate-keywords "temporal" temporal-keywords))
