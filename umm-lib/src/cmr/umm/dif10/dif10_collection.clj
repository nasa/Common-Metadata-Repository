(ns cmr.umm.dif10.dif10-collection
  "Contains functions for parsing and generating the DIF dialect."
  (:require [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [cmr.common.util :as util]
            [cmr.common.xml :as cx]
            [cmr.umm.dif10.dif10-core :as dif10-core]
            [cmr.umm.umm-collection :as c]
            [cmr.common.xml :as v]
            [cmr.umm.dif10.collection.temporal :as t]
            [cmr.umm.dif10.collection.project-element :as pj]
            [cmr.umm.dif10.collection.related-url :as ru]
            [cmr.umm.dif10.collection.science-keyword :as sk]
            [cmr.umm.dif.collection.location-keywords :as lk]
            [cmr.umm.dif10.collection.spatial :as s]
            [cmr.umm.dif10.collection.org :as org]
            [cmr.umm.dif10.collection.platform :as platform]
            [cmr.umm.dif10.collection.progress :as progress]
            [cmr.umm.dif10.collection.related-url :as ru]
            [cmr.umm.dif10.collection.reference :as ref]
            [cmr.umm.dif10.collection.personnel :as personnel]
            [cmr.umm.dif10.collection.product-specific-attribute :as psa]
            [cmr.umm.dif10.collection.metadata-association :as ma]
            [cmr.umm.dif10.collection.two-d-coordinate-system :as two-d]
            [cmr.umm.dif.collection.extended-metadata :as em]
            [cmr.common.date-time-parser :as dtp])
  (:import cmr.umm.umm_collection.UmmCollection))

(defn- xml-elem->Product
  "Returns a UMM Product from a parsed Collection Content XML structure"
  [collection-content]
  (c/map->Product
    {:short-name (cx/string-at-path collection-content [:Entry_ID :Short_Name])
     :long-name (util/trunc
                  (cx/string-at-path collection-content [:Entry_Title])
                  1024)
     :version-id (cx/string-at-path collection-content [:Entry_ID :Version])
     :collection-data-type (cx/string-at-path collection-content [:Collection_Data_Type])}))

(defn- xml-elem->DataProviderTimestamps
  "Returns a UMM DataProviderTimestamps from a parsed Collection Content XML structure"
  [collection-content]
  (let [insert-time (cx/string-at-path collection-content [:Metadata_Dates :Data_Creation])
        update-time (cx/string-at-path collection-content [:Metadata_Dates :Data_Last_Revision])
        delete-time (cx/string-at-path collection-content [:Metadata_Dates :Metadata_Delete])]
    (when (or insert-time update-time)
      (c/map->DataProviderTimestamps
        {:insert-time (dtp/try-parse-datetime insert-time) 
         :update-time (dtp/try-parse-datetime update-time) 
         :revision-date-time (dtp/try-parse-datetime update-time)
         :delete-time (dtp/try-parse-datetime delete-time)}))))

(defn- xml-elem->Collection
  "Returns a UMM Product from a parsed Collection XML structure"
  [xml-struct]
  (c/map->UmmCollection
    {:entry-title (cx/string-at-path xml-struct [:Entry_Title])
     :personnel (personnel/xml-elem->personnel xml-struct)
     :science-keywords (sk/xml-elem->ScienceKeywords xml-struct)
     :platforms (platform/xml-elem->Platforms xml-struct)
     :temporal (t/xml-elem->Temporal xml-struct)
     :collection-progress (progress/parse xml-struct)
     :spatial-coverage (s/xml-elem->SpatialCoverage xml-struct)
     :two-d-coordinate-systems (two-d/xml-elem->TwoDCoordinateSystems xml-struct)
     :projects (pj/xml-elem->Projects xml-struct)
     :quality (cx/string-at-path xml-struct [:Quality])
     :use-constraints (cx/string-at-path xml-struct [:Use_Constraints])
     :organizations (org/xml-elem->Organizations xml-struct)
     :publication-references (ref/xml-elem->References xml-struct)
     :collection-citations (ref/xml-elem->Citations xml-struct)
     :summary (cx/string-at-path xml-struct [:Summary :Abstract])
     :purpose (cx/string-at-path xml-struct [:Summary :Purpose])
     :related-urls (ru/xml-elem->RelatedURLs xml-struct)
     :collection-associations (ma/xml-elem->MetadataAssociations xml-struct)
     :product-specific-attributes (psa/xml-elem->ProductSpecificAttributes xml-struct)
     :product (xml-elem->Product xml-struct)
     :data-provider-timestamps (xml-elem->DataProviderTimestamps xml-struct)
     :spatial-keywords (lk/xml-elem->spatial-keywords xml-struct)
     :temporal-keywords (seq (cx/strings-at-path xml-struct [:Data_Resolution :Temporal_Resolution]))
     :access-value (em/xml-elem->access-value xml-struct)}))

(defn parse-collection
  "Parses DIF 10 XML into a UMM Collection record."
  [xml]
  (xml-elem->Collection (x/parse-str xml)))

(defn parse-temporal
  "Parses the XML and extracts the temporal data."
  [xml]
  (t/xml-elem->Temporal (x/parse-str xml)))

(defn parse-access-value
  "Parses the XML and extracts the access value"
  [xml]
  (em/xml-elem->access-value (x/parse-str xml)))

(def dif10-header-attributes
  "The set of attributes that go on the dif root element"
  {:xmlns "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
   :xmlns:dif "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
   :xsi:schemaLocation "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/ http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/dif_v10.2.xsd"})

(def product-levels
  "The set of values that DIF 10 defines for Processing levels as enumerations in its schema"
  #{c/not-provided "0" "1" "1A" "1B" "1T" "2" "2G" "2P" "3" "4" "NA"})

(defn- dif10-product-level-id
  "Returns the given product-level-id in DIF10 format."
  [product-level-id]
  (when product-level-id
    (product-levels (str/replace product-level-id #"Level " ""))))

(extend-protocol dif10-core/UmmToDif10Xml
  UmmCollection
  (umm->dif10-xml
    ([collection]
     (let [{{:keys [short-name version-id collection-data-type]} :product
            {:keys [insert-time update-time delete-time]} :data-provider-timestamps
            :keys [entry-title summary purpose temporal organizations science-keywords
                   platforms product-specific-attributes projects related-urls
                   temporal-keywords personnel collection-associations quality use-constraints
                   publication-references temporal access-value]} collection]
       (x/emit-str
        (x/element :DIF dif10-header-attributes
                   (x/element :Entry_ID {}
                              (x/element :Short_Name {} short-name)
                              (x/element :Version {} version-id))
                   (x/element :Entry_Title {} entry-title)
                   (ref/generate-dataset-citations collection)
                   (personnel/generate-personnel personnel)
                   (sk/generate-science-keywords science-keywords)
                   (platform/generate-platforms platforms)
                   (t/generate-temporal temporal)
                   (progress/generate collection)
                   (s/generate-spatial-coverage collection)
                   (pj/generate-projects projects)
                   (x/element :Quality {} quality)
                   (x/element :Use_Constraints {} use-constraints)
                   (org/generate-organizations organizations)
                   (ref/generate-references publication-references)
                   (x/element :Summary {}
                              (x/element :Abstract {} summary)
                              (x/element :Purpose {} purpose))
                   (ru/generate-related-urls related-urls)
                   (ma/generate-metadata-associations collection-associations)
                   (x/element :Metadata_Name {} "CEOS IDN DIF")
                   (x/element :Metadata_Version {} "VERSION 10.2")
                   (x/element :Metadata_Dates {}
                              ;; No equivalent UMM fields exist for the next two elements.
                              (x/element :Metadata_Creation {} "1970-01-01T00:00:00")
                              (x/element :Metadata_Last_Revision {} "1970-01-01T00:00:00")
                              (when delete-time
                                (x/element :Metadata_Delete {} (str delete-time)))
                              (x/element :Data_Creation {} (str insert-time))
                              (x/element :Data_Last_Revision {} (str update-time)))
                   (psa/generate-product-specific-attributes product-specific-attributes)
                   (let [processing-level-id
                         (dif10-product-level-id (get-in collection [:product :processing-level-id]))]
                     (when-not (empty? processing-level-id)
                       (x/element :Product_Level_Id {} processing-level-id)))
                   (when collection-data-type
                     (x/element :Collection_Data_Type {} collection-data-type))
                   (when access-value
                     (x/element :Extended_Metadata {}
                                (em/generate-metadata-elements
                                 [{:name em/restriction_flag_external_meta_name
                                   :value access-value}])))))))))

(defn validate-xml
  "Validates the XML against the DIF schema."
  [xml]
  (v/validate-xml (io/resource "schema/dif10/dif_v10.2.xsd") xml))
