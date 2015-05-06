(ns cmr.umm.dif10.collection
  "Contains functions for parsing and generating the DIF dialect."
  (:require [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [cmr.common.util :as util]
            [cmr.common.xml :as cx]
            [cmr.umm.dif.core :as dif-core]
            [cmr.umm.collection :as c]
            [cmr.common.xml :as v]
            [camel-snake-kebab.core :as csk]
            [cmr.umm.dif10.collection.temporal :as t]
            [cmr.umm.dif10.collection.project-element :as pj]
            [cmr.umm.dif10.collection.related-url :as ru]
            [cmr.umm.dif10.collection.science-keyword :as sk]
            [cmr.umm.dif10.collection.org :as org]
            [cmr.umm.dif10.collection.platform :as platform])
  (:import cmr.umm.collection.UmmCollection))

(defn- xml-elem->Product
  "Returns a UMM Product from a parsed Collection Content XML structure"
  [collection-content]
  (let [short-name (cx/string-at-path collection-content [:Entry_ID])
        long-name (cx/string-at-path collection-content [:Entry_Title])
        long-name (util/trunc long-name 1024)
        version-id (cx/string-at-path collection-content [:Version])
        processing-level-id (cx/string-at-path collection-content [:Product_Level_Id])
        collection-data-type (cx/string-at-path collection-content [:Collection_Data_Type])]
    (c/map->Product {:short-name short-name
                     :long-name long-name
                     :version-id version-id
                     :processing-level-id processing-level-id
                     :collection-data-type collection-data-type})))

(defn- xml-elem->DataProviderTimestamps
  "Returns a UMM DataProviderTimestamps from a parsed Collection Content XML structure"
  [collection-content]
  (let [insert-time (cx/string-at-path collection-content [:Metadata_Dates :Metadata_Creation])
        update-time (cx/string-at-path collection-content [:Metadata_Dates :Metadata_Last_Revision])]
    (when (or insert-time update-time)
      (c/map->DataProviderTimestamps
        {:insert-time (t/string->datetime insert-time)
         :update-time (t/string->datetime update-time)}))))


(defn- xml-elem->OrbitParameters
  "Returns a UMM OrbitParameters record from a parsed OrbitParameters XML structure"
  [orbit-params]
  (when orbit-params
    (c/map->OrbitParameters {:swath-width (cx/double-at-path orbit-params [:Swath_Width])
                             :period (cx/double-at-path orbit-params [:Period])
                             :inclination-angle (cx/double-at-path orbit-params [:Inclination_Angle])
                             :number-of-orbits (cx/double-at-path orbit-params [:Number_Of_Orbits])
                             :start-circular-latitude (cx/double-at-path orbit-params
                                                                         [:Start_Circular_Latitude])})))

(defn- xml-elem->SpatialCoverage
  "Returns a UMM SpatialCoverage from a parsed Collection XML structure"
  [xml-struct]
  (if-let [spatial-elem (cx/element-at-path xml-struct [:Spatial_Coverage])]
    (let [gsr (csk/->kebab-case-keyword (cx/string-at-path spatial-elem [:Granule_Spatial_Representation]))
          orbit-params (cx/element-at-path spatial-elem [:Orbit_Parameters])]
      (if-let [geom-elem (cx/element-at-path spatial-elem [:Geometry])]
        (c/map->SpatialCoverage
          {:granule-spatial-representation gsr
           :orbit-parameters (xml-elem->OrbitParameters orbit-params)
           :spatial-representation (csk/->kebab-case-keyword (cx/string-at-path geom-elem [:Coordinate_System]))})
        (c/map->SpatialCoverage
          {:granule-spatial-representation gsr
           :orbit-parameters (xml-elem->OrbitParameters orbit-params)})))))


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
       :spatial-coverage (xml-elem->SpatialCoverage xml-struct)
       :organizations (org/xml-elem->Organizations xml-struct)})))

(defn parse-collection
  "Parses DIF 10 XML into a UMM Collection record."
  [xml]
  (xml-elem->Collection (x/parse-str xml)))

(defn validate-xml
  "Validates the XML against the DIF schema."
  [xml]
  (v/validate-xml (io/resource "schema/dif10/dif_v10.1.xsd") xml))