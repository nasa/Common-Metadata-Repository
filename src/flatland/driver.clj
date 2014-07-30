(ns flatland.driver
  (:require [vdd-core.core :as vdd]
            [vdd-core.internal.project-viz :as project-viz]))


(comment

  (add-viz-geometries [{:type :cartesian-ring
                        :ords [0 0 10 10]}])

  (add-viz-geometries [{:type :ring
                        :ords [0 0 10 10 20 30]}])

  (add-viz-geometries [{:type :bounding-rectangle
                        :west -10
                        :north 10
                        :east 25
                        :south -13}])

  (clear-viz-geometries)

)

(defn set-viz-geometries [geometries]
  (vdd/data->viz {:cmd :set-geometries
                  :geometries geometries}))

(defn add-viz-geometries [geometries]
  (vdd/data->viz {:cmd :add-geometries
                  :geometries geometries}))

(defn remove-geometries [ids]
  (vdd/data->viz {:cmd :remove-geometries
                  :ids ids}))

(defn clear-viz-geometries []
  (vdd/data->viz {:cmd :clear-geometries}))

