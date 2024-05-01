(ns cmr.umm.iso-smap.granule.related-url
  "Contains functions for parsing and generating SMAP ISO OnlineResources and UMM related urls."
  (:require
   [clojure.set :as set]
   [clojure.data.xml :as xml]
   [cmr.common.xml :as cx]
   [cmr.umm.umm-collection :as coll]
   [cmr.umm.iso-smap.helper :as helper]))

(def resource-type->related-url-type
  "A mapping of SMAP ISO OnlineResource's type to UMM RelatedURL's type.
  This is probably an incomplete list. We will treat OnlineResources without a resource-type
  or a value of GET DATA VIA DIRECT ACCESS as online access url, and any resource-type other
  than BROWSE as a METADATA type."
  {"BROWSE" "GET RELATED VISUALIZATION"
   "METADATA" "VIEW RELATED INFORMATION"
   "GET DATA VIA DIRECT ACCESS" "GET DATA VIA DIRECT ACCESS"})

(defn xml-elem->related-url
  [elem]
  (let [url (cx/string-at-path elem [:linkage :URL])
        name (cx/string-at-path elem [:name :CharacterString])
        resource-type (if name
                        (resource-type->related-url-type name "VIEW RELATED INFORMATION")
                        "GET DATA")
        mime-type (cx/string-at-path elem [:applicationProfile :CharacterString])]
    (coll/map->RelatedURL
      {:url url
       :mime-type mime-type
       :type resource-type})))

(defn xml-elem->related-urls
  "Returns related-urls elements from a parsed XML structure"
  [xml-struct]
  (let [urls (map xml-elem->related-url
                  (cx/elements-at-path
                    xml-struct
                    [:composedOf :DS_DataSet :has :MI_Metadata :distributionInfo :MD_Distribution
                     :distributor :MD_Distributor :distributorTransferOptions
                     :MD_DigitalTransferOptions :onLine :CI_OnlineResource]))]
    (seq urls)))

(defn generate-related-urls
  "Generates the SMAP ISO online resource XML from a UMM related urls entry."
  [related-urls]
  (when (seq related-urls)
    (for [related-url related-urls]
      (let [{:keys [url type mime-type]} related-url]
        (xml/element
          :gmd:distributorTransferOptions {}
          (xml/element
            :gmd:MD_DigitalTransferOptions {}
            (xml/element
              :gmd:onLine {}
              (xml/element
                :gmd:CI_OnlineResource {}
                (xml/element :gmd:linkage {}
                             (xml/element :gmd:URL {} url))
                (helper/iso-string-element :gmd:applicationProfile mime-type)
                (when-not (= "GET DATA" type)
                  (helper/iso-string-element
                    :gmd:name
                    ((set/map-invert resource-type->related-url-type) type)))))))))))
