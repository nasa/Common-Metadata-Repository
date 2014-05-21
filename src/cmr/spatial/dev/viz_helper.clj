(ns cmr.spatial.dev.viz-helper
  (:require [earth.driver :as earth-viz]
            [vdd-core.core :as vdd]
            [cmr.common.lifecycle :as lifecycle]
            [cmr.spatial.point :as p]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.ring :as r]
            [clojure.string :as s]
            [cmr.spatial.derived :as d]
            [cmr.spatial.math :refer :all])
  (:import cmr.spatial.point.Point
           cmr.spatial.ring.Ring
           cmr.spatial.polygon.Polygon
           cmr.spatial.mbr.Mbr))

(comment

  (add-geometries
    [(p/point 0 0)
     (p/point 1 1)
     (p/point 2 2)])

  (clear-geometries)

)

;; Allows starting and stopping the visualization server.
(defrecord VizServer [config server]

  lifecycle/Lifecycle

  (start
    [this system]
    (assoc this :server (vdd/start-viz config)))

  (stop
    [this system]
    (vdd/stop-viz server)
    (dissoc this :server)))


(defn create-viz-server
  "Creates a visualization server which responds to lifecycle start and stop."
  []
  (let [config (assoc (vdd/config) :plugins ["earth"])]
    (->VizServer config nil)))

(defprotocol CmrSpatialToVizGeom
  "Protocol defining functions for converting a cmr spatial type to geometry that can be visualized."
  (cmr-spatial->viz-geoms
    [cmr-spatial]
    "Converts a CMR Spatial shape into a geometry that can be visualized"))

(extend-protocol CmrSpatialToVizGeom
  Point
  (cmr-spatial->viz-geoms
    [point]
    (let [{:keys [lon lat]} point
          label (str (round 2 lon) "," (round 2 lat))
          balloon label]
      [{:type :point
        :lon lon
        :lat lat
        :label label
        :balloon balloon}]))
  Ring
  (cmr-spatial->viz-geoms
    [ring]
    (let [{:keys [points mbr display-options]} ring]
      [{:type :ring
        :ords (p/points->ords points)
        :displayOptions display-options}]))

  Polygon
  (cmr-spatial->viz-geoms
    [polygon]
    (let [{:keys [rings]} polygon]
      (mapcat cmr-spatial->viz-geoms rings)))

  Mbr
  (cmr-spatial->viz-geoms
    [mbr]
    (let [{:keys [west north east south]} mbr]
      [{:type :bounding-rectangle
        :west west
        :north north
        :east east
        :south south}])))

(defn clear-geometries
  "Removes any displayed visualizations in the geometry"
  []
  (earth-viz/clear-viz-geometries))

(defn add-geometries
  "Adds spatial geometry to the visualization. The geometries passed in should be CMR Spatial areas.
  They will be converted into the geometry that can be displayed."
  [geometries]
  (->> geometries
       (map d/calculate-derived)
       (mapcat cmr-spatial->viz-geoms)
       earth-viz/add-viz-geometries))
