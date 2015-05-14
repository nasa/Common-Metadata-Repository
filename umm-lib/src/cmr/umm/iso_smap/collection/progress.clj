(ns cmr.umm.iso-smap.collection.progress
  "Functions for parsing/generating Collection Progress values from/to
  ISO SMAP XML."
  (:require [clojure.data.xml :as x]
            [clojure.string :as string]
            [cmr.common.xml :as cx]))

(def code-list-value-map
  {:planned "planned"
   :in-work "ongoing"
   :complete "completed"})

(def lookup-by-code-value
  (zipmap (map string/lower-case (vals code-list-value-map))
          (keys code-list-value-map)))

(defn parse
  "Returns a collection progress value parsed from an ISO SMAP
  collection XML element."
  [xml-struct]
  (some-> xml-struct
          (cx/element-at-path [:seriesMetadata :MI_Metadata :identificationInfo
                               :MD_DataIdentification :status :MD_ProgressCode])
          (get-in [:attrs :codeListValue])
          string/lower-case
          lookup-by-code-value))

(def code-list-url
  "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ProgressCode")

(defn generate
  "Returns SMAP XML elements representing the collection progress of
  the given collection."
  [{:keys [collection-progress]}]
  (when-let [value (code-list-value-map collection-progress)]
    (x/element :gmd:status {}
               (x/element :gmd:MD_ProgressCode {:codeList code-list-url
                                                :codeListValue value}
                          (string/upper-case value)))))
