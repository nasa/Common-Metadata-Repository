(ns cmr.umm.iso-smap.collection.spatial
  "Contains functions for parsing and generating the ISO SMAP spatial"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]
            [cmr.spatial.mbr :as mbr]
            [cmr.umm.generator-util :as gu]))

(defn bounding-box-elem->geometry
  "Returns the spatial geometry parsed from the bounding box xml element"
  [bounding-box-elem]
  (let [west (cx/double-at-path bounding-box-elem [:westBoundLongitude :Decimal])
        east (cx/double-at-path bounding-box-elem [:eastBoundLongitude :Decimal])
        north (cx/double-at-path bounding-box-elem [:northBoundLatitude :Decimal])
        south (cx/double-at-path bounding-box-elem [:southBoundLatitude :Decimal])]
    (mbr/mbr west north east south)))

(defn xml-elem->SpatialCoverage
  "Returns a UMM SpatialCoverage from a parsed XML structure"
  [xml-struct]
  ;; SMAP ISO only support BoundingBox spatial geometry
  (let [spatial-elems (cx/elements-at-path
                        xml-struct
                        [:seriesMetadata :MI_Metadata :identificationInfo :MD_DataIdentification
                         :extent :EX_Extent :geographicElement :EX_GeographicBoundingBox])]
    (when (seq spatial-elems)
      (let [geometries (map bounding-box-elem->geometry spatial-elems)]
        (c/map->SpatialCoverage
          {:granule-spatial-representation :cartesian
           :spatial-representation :cartesian
           :geometries geometries})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generators

(defn- generate-bounding-box-element
  "Generates the EX_GeographicBoundingBox for a given geometry, only BoundingBox is supported in SMAP ISO"
  [geometry]
  (let [{:keys [west north east south]} geometry
        gen-point-fn (fn [type value]
                       (x/element type {}
                                  (x/element :gco:Decimal {} value)))]
    (x/element :gmd:geographicElement {}
               (x/element :gmd:EX_GeographicBoundingBox {:id (gu/generate-id)}
                          (x/element :gmd:extentTypeCode {}
                                     (x/element :gco:Boolean {} 1))
                          (gen-point-fn :gmd:westBoundLongitude west)
                          (gen-point-fn :gmd:eastBoundLongitude east)
                          (gen-point-fn :gmd:southBoundLatitude south)
                          (gen-point-fn :gmd:northBoundLatitude north)))))

(defn generate-spatial
  "Generates the Spatial element from spatial coverage"
  [spatial-coverage]
  (when spatial-coverage
    (let [{:keys [geometries]} spatial-coverage
          bounding-boxes (filter #(= cmr.spatial.mbr.Mbr (type %)) geometries)]
      (map generate-bounding-box-element bounding-boxes))))

