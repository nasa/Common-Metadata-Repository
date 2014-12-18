(ns cmr.umm.dif.collection
  "Contains functions for parsing and generating the DIF dialect."
  (:require [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [cmr.common.util :as util]
            [cmr.common.xml :as cx]
            [cmr.umm.dif.core :as dif-core]
            [cmr.umm.collection :as c]
            [cmr.common.xml :as v]
            [cmr.umm.dif.collection.project :as pj]
            [cmr.umm.dif.collection.related-url :as ru]
            [cmr.umm.dif.collection.science-keyword :as sk]
            [cmr.umm.dif.collection.org :as org]
            [cmr.umm.dif.collection.temporal :as t]
            [cmr.umm.dif.collection.product-specific-attribute :as psa]
            [cmr.umm.dif.collection.platform :as platform]
            [cmr.umm.dif.collection.spatial-coverage :as sc]
            [cmr.umm.dif.collection.extended-metadata :as em]
            [cmr.umm.dif.collection.personnel :as personnel])
  (:import cmr.umm.collection.UmmCollection))

(def PRODUCT_LEVEL_ID_EXTERNAL_META_NAME
  "ProductLevelId")

(def COLLECTION_DATA_TYPE_EXTERNAL_META_NAME
  "CollectionDataType")

(defn- xml-elem->Product
  "Returns a UMM Product from a parsed Collection Content XML structure"
  [collection-content]
  (let [short-name (cx/string-at-path collection-content [:Entry_ID])
        long-name (cx/string-at-path collection-content [:Entry_Title])
        long-name (util/trunc long-name 1024)
        version-id (cx/string-at-path collection-content [:Data_Set_Citation :Version])
        processing-level-id (em/extended-metadatas-value collection-content PRODUCT_LEVEL_ID_EXTERNAL_META_NAME)
        collection-data-type (em/extended-metadatas-value collection-content COLLECTION_DATA_TYPE_EXTERNAL_META_NAME)]
    (c/map->Product {:short-name short-name
                     :long-name long-name
                     :version-id version-id
                     :processing-level-id processing-level-id
                     :collection-data-type collection-data-type})))

(defn- xml-elem->DataProviderTimestamps
  "Returns a UMM DataProviderTimestamps from a parsed Collection Content XML structure"
  [collection-content]
  (let [insert-time (cx/string-at-path collection-content [:DIF_Creation_Date])
        update-time (cx/string-at-path collection-content [:Last_DIF_Revision_Date])]
    (when (or insert-time update-time)
      (c/map->DataProviderTimestamps
        {:insert-time (t/string->datetime insert-time)
         :update-time (t/string->datetime update-time)}))))

(defn- xml-elem->Collection
  "Returns a UMM Product from a parsed Collection XML structure"
  [xml-struct]
  (c/map->UmmCollection
    {:entry-id (cx/string-at-path xml-struct [:Entry_ID])
     :entry-title (cx/string-at-path xml-struct [:Entry_Title])
     :summary (cx/string-at-path xml-struct [:Summary :Abstract])
     :product (xml-elem->Product xml-struct)
     :data-provider-timestamps (xml-elem->DataProviderTimestamps xml-struct)
     ;; See CMR-588
     ;:spatial-keywords (seq (cx/strings-at-path xml-struct [:Location]))
     :temporal-keywords (seq (cx/strings-at-path xml-struct [:Data_Resolution :Temporal_Resolution]))
     :temporal (t/xml-elem->Temporal xml-struct)
     :science-keywords (sk/xml-elem->ScienceKeywords xml-struct)
     :platforms (platform/xml-elem->Platforms xml-struct)
     :product-specific-attributes (psa/xml-elem->ProductSpecificAttributes xml-struct)
     :projects (pj/xml-elem->Projects xml-struct)
     :related-urls (ru/xml-elem->RelatedURLs xml-struct)
     :spatial-coverage (sc/xml-elem->SpatialCoverage xml-struct)
     :organizations (org/xml-elem->Organizations xml-struct)
     :personnel (personnel/xml-elem->personnel xml-struct)}))

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
     (let [{{:keys [version-id processing-level-id collection-data-type]} :product
            {:keys [insert-time update-time]} :data-provider-timestamps
            :keys [entry-id entry-title summary temporal organizations science-keywords platforms
                   product-specific-attributes projects related-urls spatial-coverage
                   temporal-keywords personnel]} collection
           ;; DIF only has range-date-times, so we ignore the temporal field if it is not of range-date-times
           temporal (when (seq (:range-date-times temporal)) temporal)
           emit-fn (if indent? x/indent-str x/emit-str)]
       (emit-fn
         (x/element :DIF dif-header-attributes
                    (x/element :Entry_ID {} entry-id)
                    (x/element :Entry_Title {} entry-title)
                    (when version-id
                      (x/element :Data_Set_Citation {}
                                 (x/element :Version {} version-id)))
                    (personnel/generate-personnel personnel)
                    (sk/generate-science-keywords science-keywords)
                    (platform/generate-platforms platforms)
                    (t/generate-temporal temporal)
                    (sc/generate-spatial-coverage spatial-coverage)
                    (when (seq temporal-keywords)
                      (for [tk temporal-keywords]
                        (x/element :Data_Resolution {} (x/element :Temporal_Resolution {} tk))))
                    (when-not (empty? projects)
                      (pj/generate-projects projects))
                    (org/generate-data-center organizations)
                    (x/element :Summary {} (x/element :Abstract {} summary))
                    (when-not (empty? related-urls)
                      (ru/generate-related-urls related-urls))
                    (x/element :Metadata_Name {} "dummy")
                    (x/element :Metadata_Version {} "dummy")
                    (when insert-time
                      (x/element :DIF_Creation_Date {} (str insert-time)))
                    (when update-time
                      (x/element :Last_DIF_Revision_Date {} (str update-time)))
                    (sc/generate-spatial-coverage-extended-metadata spatial-coverage)
                    (psa/generate-product-specific-attributes product-specific-attributes)
                    (when processing-level-id
                      (em/generate-extended-metadatas [{:name PRODUCT_LEVEL_ID_EXTERNAL_META_NAME
                                                        :value processing-level-id}] false))
                    (when collection-data-type
                      (em/generate-extended-metadatas [{:name COLLECTION_DATA_TYPE_EXTERNAL_META_NAME
                                                        :value collection-data-type}] false))))))))

(defn validate-xml
  "Validates the XML against the DIF schema."
  [xml]
  (v/validate-xml (io/resource "schema/dif/dif.xsd") xml))

