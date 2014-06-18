(ns cmr.spatial.dev.viz-helper
  (:require [earth.driver :as earth-viz]
            [vdd-core.core :as vdd]
            [cmr.common.lifecycle :as lifecycle]
            [cmr.spatial.point :as p]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.arc :as a]
            [cmr.spatial.ring :as r]
            [clojure.string :as s]
            [cmr.spatial.derived :as d]
            [cmr.spatial.math :refer :all])
  (:import cmr.spatial.point.Point
           cmr.spatial.ring.Ring
           cmr.spatial.polygon.Polygon
           cmr.spatial.mbr.Mbr
           cmr.spatial.arc.Arc))

(comment

  (add-geometries
    [(p/point 0 0)
     (p/point 1 1)
     (p/point 2 2)])

  (add-geometries
    [(assoc
       (r/ring [(p/point 1.0 1.0)
                     (p/point 1.0 4.0)
                     (p/point -1.0 1.0)
                     (p/point -2.0 1.0)
                     (p/point -1.0 0.0)
                     (p/point 1.0 1.0)])
       :options {:style {:color "9918A0ff" :width 5}})])

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
    (let [{:keys [lon lat options]} point
          label (str (round 2 lon) "," (round 2 lat))
          balloon label
          ;; provide a default label and balloon
          options (merge {:label label :balloon balloon} options)]
      [{:type :point
        :lon lon
        :lat lat
        :options options}]))
  Ring
  (cmr-spatial->viz-geoms
    [ring]
    (let [{:keys [points mbr options]} ring]
      [{:type :ring
        :ords (p/points->ords points)
        :options options}]))

  Arc
  (cmr-spatial->viz-geoms
    [arc]
    (let [{:keys [options]} arc]
      [{:type :ring
        :ords (a/arc->ords arc)
        :options options}]))

  Polygon
  (cmr-spatial->viz-geoms
    [polygon]
    (let [{:keys [rings options]} polygon
          [boundary & holes] (map #(update-in % [:options] merge options) rings)
          ;; Create distinct colors for the holes that are evenly distributed among the color space. (mostly)
          hole-colors (map #(format "99%02x%02xff" % %)
                           (range 0 255 (int (Math/ceil (/ 255 (inc (count holes)))))))
          holes (map (fn [hole color]
                       (assoc-in hole [:options :style] {:width 5 :color color}))
                     holes
                     hole-colors)]

      (mapcat cmr-spatial->viz-geoms (cons boundary holes))))



  Mbr
  (cmr-spatial->viz-geoms
    [mbr]
    (let [{:keys [west north east south options]} mbr]
      [{:type :bounding-rectangle
        :west west
        :north north
        :east east
        :south south
        :options options}])))

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

(defn remove-geometries
  "Removes geometries displayed on the page with the specified ids. The ids for a geometry can be
  set in the options map under the top level of the object."
  [ids]
  (earth-viz/remove-geometries ids))
