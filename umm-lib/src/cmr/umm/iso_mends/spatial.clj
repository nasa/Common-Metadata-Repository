(ns cmr.umm.iso-mends.spatial
  "Functions for extracting spatial extent information from an
  ISO-MENDS format XML document."
  (:require [clojure.data.xml :as x]
            [clojure.string :as string]
            [cmr.common.util :as util]
            [cmr.common.xml :as cx]
            [cmr.spatial.derived :as d]
            [cmr.spatial.encoding.gmd :as gmd]
            [cmr.spatial.line-string :as ls]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.point :as p]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.relations :as r]
            [cmr.umm.collection :as c]
            [cmr.umm.iso-mends.core :as core]
            [cmr.umm.spatial :as umm-s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing XML

(defn- parse-geometries
  "Returns a seq of UMM geometry records from an ISO XML document."
  [xml]
  (let [id-elem   (core/id-elem xml)
        geo-elems (cx/elements-at-path id-elem [:extent :EX_Extent :geographicElement])
        ;; ISO MENDS includes bounding boxes for each element (point,
        ;; polygon, etc.) in the spatial extent metadata. We can
        ;; discard the redundant bounding boxes.
        shape-elems (map second (partition 2 geo-elems))]
    (remove nil? (map gmd/decode shape-elems))))

(def ref-sys-path-with-ns
  "A namespaced element path sequence for the ISO MENDS coordinate system element."
  [:gmd:referenceSystemInfo :gmd:MD_ReferenceSystem :gmd:referenceSystemIdentifier :gmd:RS_Identifier :gmd:code :gco:CharacterString])

(def ref-sys-path
  "The (non-namespaced) path to access the ISO MENDS coordinate system element."
  (map #(keyword (second (.split (name %) ":"))) ref-sys-path-with-ns))

(defn- parse-coordinate-system
  [xml]
  (umm-s/->coordinate-system (cx/string-at-path xml ref-sys-path)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Functions

(defn xml-elem->SpatialCoverage
  "Returns a UMM SpatialCoverage record from the given ISO MENDS XML
  document root element."
  [xml]
  (when-let [geometries (seq (parse-geometries xml))]
    (let [coord-sys  (parse-coordinate-system xml)]
      (c/map->SpatialCoverage
       {:spatial-representation coord-sys
        :granule-spatial-representation coord-sys
        :geometries (map #(umm-s/set-coordinate-system coord-sys %) geometries)}))))

(defn spatial-coverage->coordinate-system-xml
  "Returns ISO MENDS coordinate system XML element from the given SpatialCoverage."
  [{:keys [spatial-representation]}]
  (when spatial-representation
    (reduce (fn [content tag] (x/element tag {} content))
            (.toUpperCase (name spatial-representation))
            (reverse ref-sys-path-with-ns))))

(defn spatial-coverage->extent-xml
  "Returns a sequence of ISO MENDS elements from the given SpatialCoverage."
  [{:keys [spatial-representation geometries]}]
  (->> geometries
       (map (partial umm-s/set-coordinate-system spatial-representation))
       (map d/calculate-derived)
       (mapcat (juxt r/mbr identity))
       (map gmd/encode)))
