(ns cmr.umm.dif.collection
  "Contains functions for parsing and generating the DIF dialect."
  (:require [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [cmr.common.xml :as cx]
            [cmr.umm.dif.core :as dif-core]
            [cmr.umm.collection :as c]
            [cmr.umm.xml-schema-validator :as v]
            [cmr.umm.dif.collection.project :as pj]
            [cmr.umm.dif.collection.related-url :as ru]
            [cmr.umm.dif.collection.science-keyword :as sk]
            [cmr.umm.dif.collection.org :as org]
            [cmr.umm.dif.collection.temporal :as t]
            [cmr.umm.dif.collection.product-specific-attribute :as psa]
            [cmr.umm.dif.collection.spatial-coverage :as sc]
            [cmr.umm.dif.collection.extended-metadata :as em])
  (:import cmr.umm.collection.UmmCollection))

(defn- xml-elem->Product
  "Returns a UMM Product from a parsed Collection Content XML structure"
  [collection-content]
  (c/map->Product {:short-name (cx/string-at-path collection-content [:Entry_ID])
                   :long-name (cx/string-at-path collection-content [:Entry_Title])
                   :version-id (cx/string-at-path collection-content [:Data_Set_Citation :Version])}))

(defn- xml-elem->Collection
  "Returns a UMM Product from a parsed Collection XML structure"
  [xml-struct]
  (let [product (xml-elem->Product xml-struct)]
    (c/map->UmmCollection
      {:entry-id (cx/string-at-path xml-struct [:Entry_ID])
       :entry-title (cx/string-at-path xml-struct [:Entry_Title])
       :product product
       ;:spatial-keywords (seq (cx/strings-at-path xml-struct [:Location]))
       :temporal (t/xml-elem->Temporal xml-struct)
       :science-keywords (sk/xml-elem->ScienceKeywords xml-struct)
       ;:platforms (platform/xml-elem->Platforms xml-struct)
       :product-specific-attributes (psa/xml-elem->ProductSpecificAttributes xml-struct)
       :projects (pj/xml-elem->Projects xml-struct)
       :related-urls (ru/xml-elem->RelatedURLs xml-struct)
       :spatial-coverage (sc/xml-elem->SpatialCoverage xml-struct)
       :organizations (org/xml-elem->Organizations xml-struct)})))

(defn parse-collection
  "Parses DIF XML into a UMM Collection record."
  [xml]
  (xml-elem->Collection (x/parse-str xml)))

(def dif-header-attributes
  "The set of attributes that go on the dif root element"
  {:xmlns "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
   :xmlns:dif "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
   :xsi:schemaLocation "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/ http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/dif_v9.8.4.xsd"})

(extend-protocol cmr.umm.dif.core/UmmToDifXml
  UmmCollection
  (umm->dif-xml
    ([collection]
     (cmr.umm.dif.core/umm->dif-xml collection false))
    ([collection indent?]
     (let [{{:keys [short-name long-name version-id]} :product
            {:keys [insert-time update-time delete-time]} :data-provider-timestamps
            :keys [entry-title temporal organizations science-keywords platforms product-specific-attributes
                   projects related-urls spatial-coverage]} collection
           emit-fn (if indent? x/indent-str x/emit-str)]
       (emit-fn
         (x/element :DIF dif-header-attributes
                    (x/element :Entry_ID {} short-name)
                    (x/element :Entry_Title {} entry-title)
                    (when version-id
                      (x/element :Data_Set_Citation {}
                                 (x/element :Version {} version-id)))
                    (sk/generate-science-keywords science-keywords)
                    (t/generate-temporal temporal)
                    (when-not (empty? projects)
                      (pj/generate-projects projects))
                    (org/generate-data-center organizations)
                    (x/element :Summary {} (x/element :Abstract {} "dummy"))
                    (when-not (empty? related-urls)
                      (ru/generate-related-urls related-urls))
                    (x/element :Metadata_Name {} "dummy")
                    (x/element :Metadata_Version {} "dummy")
                    (sc/generate-spatial-coverage spatial-coverage)
                    (psa/generate-product-specific-attributes product-specific-attributes)))))))

(defn validate-xml
  "Validates the XML against the DIF schema."
  [xml]
  (v/validate-xml (io/resource "schema/dif/dif.xsd") xml))

