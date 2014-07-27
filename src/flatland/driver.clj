(ns flatland.driver
  (:require [vdd-core.core :as vdd]
            [vdd-core.internal.project-viz :as project-viz]))

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
