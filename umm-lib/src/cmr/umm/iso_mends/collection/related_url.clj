(ns cmr.umm.iso-mends.collection.related-url
  "Contains functions for parsing and generating the ISO MENDS related urls"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.iso-mends.collection.helper :as h]))

(def resource-name->types
  "Mapping of online resource name to related url type and sub-type"
  {"DATA ACCESS" "GET DATA"
   "Guide" "VIEW RELATED INFORMATION"
   "Browse" "GET RELATED VISUALIZATION"})

(def type->name
  "Mapping of related url type to online resource name"
  {"GET DATA" "DATA ACCESS"
   "VIEW RELATED INFORMATION" "Guide"
   "GET RELATED VISUALIZATION" "Browse"})

(defn- xml-elem->related-url
  "Returns a related url from a parsed XML structure"
  [resource-elem]
  ;; ECHO onlineAccessUrl maps to ISO OnlineResourceUrl with :code download and no :name
  ;; ECHO onlineResourceUrl (DATA ACCESS type) maps to ISO OnlineResourceUrl with no :code
  ;; and :name "DATA ACCESS"
  (let [url (cx/string-at-path resource-elem [:CI_OnlineResource :linkage :URL])
        name (cx/string-at-path resource-elem [:CI_OnlineResource :name :CharacterString])
        description (cx/string-at-path resource-elem [:CI_OnlineResource :description :CharacterString])
        code (cx/string-at-path resource-elem [:CI_OnlineResource :function :CI_OnLineFunctionCode])
        title description
        type (when name (resource-name->types name))
        type (if (= "download" code) "GET DATA" type)]
    (c/map->RelatedURL
      {:url url
       :description description
       :title title
       :type type})))

(defn xml-elem->related-urls
  "Returns related-urls elements from a parsed XML structure"
  [xml-struct]
  (let [url-elems (cx/elements-at-path
                    xml-struct
                    [:distributionInfo :MD_Distribution :distributor :MD_Distributor
                     :distributorTransferOptions :MD_DigitalTransferOptions :onLine])]
    (seq (map xml-elem->related-url url-elems))))

(defn- function-code-attributes
  [code]
  {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode"
   :codeListValue code})

(defn generate-resource-urls
  "Generates the ISO OnlineResources elements from a UMM related urls entry."
  [related-urls]
  (for [related-url related-urls]
    (let [{:keys [url type description]} related-url
          name (type->name type)
          ;; figure out code for the resource url
          code (if (= "GET DATA" type)
                 (if name "" "download")
                 "information")]
      (x/element :gmd:onLine {}
                 (x/element :gmd:CI_OnlineResource {}
                            (x/element :gmd:linkage {}
                                       (x/element :gmd:URL {} url))
                            (when name (h/iso-string-element :gmd:name name))
                            (if description
                              (h/iso-string-element :gmd:description description)
                              (x/element :gmd:description {:gco:nilReason "missing"}))
                            (x/element :gmd:function {}
                                       (x/element :gmd:CI_OnLineFunctionCode
                                                  (function-code-attributes code) code)))))))

