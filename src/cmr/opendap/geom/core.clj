(ns cmr.opendap.geom.core
  (:require
    [cmr.opendap.geom.impl.esri :as esri]
    [cmr.opendap.geom.impl.geographiclib :as geographiclib]
    [cmr.opendap.geom.impl.jts :as jts])
  (:import
    (com.esri.core.geometry.Polygon)
    (com.vividsolutions.jts.geom.Polygon)
    (cmr.opendap.geom.impl.geographiclib GeographiclibPolygon))
  (:refer-clojure :exclude [empty? intersection reverse]))

(defprotocol PolygonAPI
  (area [this])
  (bounding-box [this])
  (empty? [this])
  (intersection [this other])
  (intersects? [this other])
  (points [this])
  (point-count [this])
  (reverse [this])
  (valid? [this])
  ;; Experimental
  (cartesian->wgs84 [this])
  (wgs84->cartesian [this]))

(extend com.vividsolutions.jts.geom.Polygon
        PolygonAPI
        jts/behaviour)

(extend com.esri.core.geometry.Polygon
        PolygonAPI
        esri/behaviour)

(extend GeographiclibPolygon
        PolygonAPI
        geographiclib/behaviour)

(defn create-polygon
  [type points]
  (case type
    :jts (jts/create points)
    :esri (esri/create points)
    :geographiclib (geographiclib/create points)))
