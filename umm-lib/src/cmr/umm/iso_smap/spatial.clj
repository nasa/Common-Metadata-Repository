(ns cmr.umm.iso-smap.spatial
  "Common functions for ISO SMAP spatial XML parsing and generation."
  (:require [cmr.spatial.encoding.gmd :as gmd]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.ring-relations :as rr]
            [cmr.umm.umm-spatial :as umm-s]))

;; Because GML parsing assumes anti-clockwise order, but ISO SMAP
;; expects points in clockwise order, we need to flip the point order
;; of polygon rings.

(defn- flip-polygon-point-order
  "Returns standard GMD/GML spatial object with rings flipped to match
  ISO SMAP convention."
  [obj]
  (if-let [rings (:rings obj)]
    (poly/polygon (map rr/invert rings))
    obj))

(defn- set-coordinate-system
  "Returns spatial record in SMAP coordinate system (geodetic)."
  [obj]
  (umm-s/set-coordinate-system :geodetic obj))

(defn decode
  "Returns CMR spatial geometry for gmd:geographicElement XML element."
  [element]
  (-> element gmd/decode flip-polygon-point-order set-coordinate-system))

(defn encode
  "Returns SMAP XML elements for CMR spatial geometry."
  [geometry]
  (-> geometry set-coordinate-system flip-polygon-point-order gmd/encode))
