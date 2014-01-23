(ns cmr.es-spatial-plugin.spatial-script-factory-helper
  "Contains functionality used by SpatialScriptFactory. Seperated into a typical Clojure namespace
  to make it more compatible with REPL development."
  (:require [clojure.string]
            [cmr-spatial.ring :as ring]
            [cmr-spatial.point :as point])
  (:import cmr.es_spatial_plugin.SpatialScript
           org.elasticsearch.common.xcontent.support.XContentMapValues
           org.elasticsearch.ElasticSearchIllegalArgumentException))

(def parameters
  "The parameters to the Spatial script"
  [:ords])

(defn- extract-params
  "Extracts the parameters from the params map given in the script."
  [script-params]
  (when script-params
    (into {} (for [param parameters]
               [param (XContentMapValues/nodeStringValue
                        (get script-params (name param)) nil)]))))

(defn- assert-required-parameters
  "Asserts that all the parameters are supplied or it throws an exception."
  [params]
  (when-not (every? params parameters)
    (throw (ElasticSearchIllegalArgumentException.
             (str "Missing one or more of required parameters: "
                  (clojure.string/join parameters ", "))))))

(defn- ords-str->intersects-fn
  "Chooses which intersection function to use based on the number of ordinates in the params.
  2 means it will intersect a point.
  More than that means it will intersect a ring."
  [ords-str]
  (let [ords (map #(Double. ^String %) (clojure.string/split ords-str #","))]
    (if (= (count ords) 2)
      (let [point (apply point/point ords)]
        (fn [ring] (ring/covers-point? ring point)))
      (let [ring (apply ring/ords->ring ords)]
        (fn [ring2] (ring/intersects-ring? ring ring2))))))

(defn new-script [logger script-params]
  (let [{:keys [ords] :as params} (extract-params script-params)
        intersects-fn (ords-str->intersects-fn ords)]
    (assert-required-parameters params)
    (SpatialScript. ^Object intersects-fn logger)))


