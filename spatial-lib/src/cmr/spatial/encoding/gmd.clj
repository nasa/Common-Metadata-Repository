(ns cmr.spatial.encoding.gmd
  "Functions for encoding and decoding between spatial geometries and
  gmd:geographicElement elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.spatial.encoding.gml :as gml]
            [cmr.spatial.mbr :as mbr]))

(declare decode-geo-content)

;; Interface

(defmulti encode
  "Returns a gmd:geographicElement xml element from a spatial geometry."
  type)

(defn decode
  "Returns spatial geometry parsed from a gmd:geographicElement xml element."
  [gmd-geo-element]
  (-> gmd-geo-element :content first decode-geo-content))

;; Implementations

(defmulti decode-geo-content
  "Decode the content of a gmd:geographicElement"
  :tag)

(defmethod decode-geo-content :default
  [_]
  nil)

(defmethod encode cmr.spatial.mbr.Mbr
  [geometry]
  (let [{:keys [west north east south]} geometry
        gen-point-fn (fn [tag content]
                       (x/element tag {}
                                  (x/element :gco:Decimal {} content)))]
    (x/element :gmd:geographicElement {}
               (x/element :gmd:EX_GeographicBoundingBox {:id (str "geo-" (java.util.UUID/randomUUID))}
                          (x/element :gmd:extentTypeCode {}
                                     (x/element :gco:Boolean {} 1))
                          (gen-point-fn :gmd:westBoundLongitude west)
                          (gen-point-fn :gmd:eastBoundLongitude east)
                          (gen-point-fn :gmd:southBoundLatitude south)
                          (gen-point-fn :gmd:northBoundLatitude north)))))

(defmethod decode-geo-content :EX_GeographicBoundingBox
  [bounding-box-elem]
  (let [parse #(cx/double-at-path bounding-box-elem [% :Decimal])]
    (apply mbr/mbr (map parse [:westBoundLongitude :northBoundLatitude
                               :eastBoundLongitude :southBoundLatitude]))))

;; GMD treats everything else (points, lines, polygons) as polygons

(defmethod encode :default
  [polygon]
  (x/element
   :gmd:geographicElement {}
   (x/element
    :gmd:EX_BoundingPolygon {}
    (x/element :gmd:extentTypeCode {}
               (x/element :gco:Boolean {} 1))
    (x/element :gmd:polygon {}
               (gml/encode polygon)))))

(defmethod decode-geo-content :EX_BoundingPolygon
  [bounding-polygon-element]
  ;; EXBoundingPolygon elements contain a gmd:polygon which contains a
  ;; single gml geometry element
  (-> bounding-polygon-element (cx/element-at-path [:polygon]) :content first gml/decode))
