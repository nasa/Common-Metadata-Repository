(ns cmr.umm.dif10.collection
  "Contains functions for parsing and generating the DIF dialect."
  (:require [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [cmr.common.util :as util]
            [cmr.common.xml :as cx]
            [cmr.umm.dif10.core :as dif10-core]
            [cmr.umm.collection :as c]
            [cmr.common.xml :as v]
            [camel-snake-kebab.core :as csk]
            [cmr.umm.dif10.collection.temporal :as t]
            [cmr.umm.dif10.collection.project-element :as pj]
            [cmr.umm.dif10.collection.related-url :as ru]
            [cmr.umm.dif10.collection.science-keyword :as sk]
            [cmr.umm.dif10.collection.spatial :as s]
            [cmr.umm.dif10.collection.org :as org]
            [cmr.umm.dif10.collection.platform :as platform]
            [cmr.umm.dif10.collection.related-url :as ru]
            [cmr.umm.dif10.collection.reference :as ref])
  (:import cmr.umm.collection.UmmCollection))

(defn- xml-elem->Product
  "Returns a UMM Product from a parsed Collection Content XML structure"
  [collection-content]
  (c/map->Product
    {:short-name (cx/string-at-path collection-content [:Entry_ID])
     :long-name (util/trunc
                  (cx/string-at-path collection-content [:Entry_Title])
                  1024)
     :version-id (cx/string-at-path collection-content [:Version])
     :collection-data-type (cx/string-at-path collection-content [:Collection_Data_Type])}))

(defn- xml-elem->DataProviderTimestamps
  "Returns a UMM DataProviderTimestamps from a parsed Collection Content XML structure"
  [collection-content]
  (let [insert-time (cx/string-at-path collection-content [:Metadata_Dates :Metadata_Creation])
        update-time (cx/string-at-path collection-content [:Metadata_Dates :Metadata_Last_Revision])
        delete-time (cx/string-at-path collection-content [:Metadata_Dates :Metadata_Delete])]
    (when (or insert-time update-time)
      (c/map->DataProviderTimestamps
        {:insert-time (t/string->datetime insert-time)
         :update-time (t/string->datetime update-time)
         :delete-time (t/string->datetime delete-time)}))))


(defn- xml-elem->Collection
  "Returns a UMM Product from a parsed Collection XML structure"
  [xml-struct]
  (let [product (xml-elem->Product xml-struct)]
    (c/map->UmmCollection
      {:entry-id (str (:short-name product) "_" (:version-id product))
       :entry-title (cx/string-at-path xml-struct [:Entry_Title])
       :summary (cx/string-at-path xml-struct [:Summary :Abstract])
       :purpose (cx/string-at-path xml-struct [:Summary :Purpose])
       :product product
       :quality (cx/string-at-path xml-struct [:Quality])
       :data-provider-timestamps (xml-elem->DataProviderTimestamps xml-struct)
       :temporal-keywords (seq (cx/strings-at-path xml-struct [:Data_Resolution :Temporal_Resolution]))
       :temporal (t/xml-elem->Temporal xml-struct)
       :science-keywords (sk/xml-elem->ScienceKeywords xml-struct)
       :platforms (platform/xml-elem->Platforms xml-struct)
       :projects (pj/xml-elem->Projects xml-struct)
       :related-urls (ru/xml-elem->RelatedURLs xml-struct)
       :spatial-coverage (s/xml-elem->SpatialCoverage xml-struct)
       :organizations (org/xml-elem->Organizations xml-struct)
       :publication-references (ref/xml-elem->References xml-struct)})))

(defn parse-collection
  "Parses DIF 10 XML into a UMM Collection record."
  [xml]
  (xml-elem->Collection (x/parse-str xml)))

(def dif10-header-attributes
  "The set of attributes that go on the dif root element"
  {:xmlns "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
   :xmlns:dif "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"})

(extend-protocol dif10-core/UmmToDif10Xml
  UmmCollection
  (umm->dif10-xml
    ([collection]
     (dif10-core/umm->dif10-xml collection false))
    ([collection indent?]
     (let [{{:keys [version-id processing-level-id collection-data-type]} :product
            {:keys [insert-time update-time delete-time]} :data-provider-timestamps
            :keys [entry-id entry-title summary purpose temporal organizations science-keywords
                   platforms product-specific-attributes projects related-urls spatial-coverage
                   temporal-keywords personnel collection-associations quality use-constraints
                   publication-references temporal]} collection
           emit-fn (if indent? x/indent-str x/emit-str)]
       (emit-fn
         (x/element :DIF dif10-header-attributes
                    (x/element :Entry_ID {} entry-id)
                    (x/element :Version {} version-id)
                    (x/element :Entry_Title {} entry-title)
                    (sk/generate-science-keywords science-keywords)
                    (platform/generate-platforms platforms)
                    (t/generate-temporal temporal)
                    (s/generate-spatial-coverage spatial-coverage)
                    (pj/generate-projects projects)
                    (org/generate-organizations organizations)
                    (ref/generate-references publication-references)
                    (x/element :Summary {}
                               (x/element :Abstract {} summary)
                               (x/element :Purpose {} purpose))
                    (ru/generate-related-urls related-urls)
                    (x/element :Metadata_Name {} "CEOS IDN DIF")
                    (x/element :Metadata_Version {} "VERSION 10.1")
                    (x/element :Metadata_Dates {}
                               (x/element :Metadata_Creation {} (str insert-time))
                               (x/element :Metadata_Last_Revision {} (str update-time))
                               (when delete-time
                                 (x/element :Metadata_Delete {} (str delete-time)))
                               ;; No equivalent UMM fields exist for the next two elements which are
                               ;; required elements in DIF 10.1. Currently adding a dummy date. This
                               ;; needs to be reviewed as and when DIF 10 is updated.CMRIN-79
                               (x/element :Data_Creation {} "1970-01-01T00:00:00")
                               (x/element :Data_Last_Revision {} "1970-01-01T00:00:00"))
                    (when collection-data-type
                      (x/element :Collection_Data_Type {} collection-data-type))
                    ;; The next element which is required in DIF 10.1 will be removed in the future
                    ;; vesions of DIF 10. No equivalent UMM field exists in our code base. Currently
                    ;; using a valid enum value as a place holder.CMRIN-75
                    (x/element :Product_Flag {} "Not provided")))))))

(defn validate-xml
  "Validates the XML against the DIF schema."
  [xml]
  (v/validate-xml (io/resource "schema/dif10/dif_v10.1.xsd") xml))