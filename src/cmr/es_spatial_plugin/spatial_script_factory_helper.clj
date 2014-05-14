(ns cmr.es-spatial-plugin.spatial-script-factory-helper
  "Contains functionality used by SpatialScriptFactory. Seperated into a typical Clojure namespace
  to make it more compatible with REPL development."
  (:require [clojure.string]
            [cmr.spatial.ring :as ring]
            [cmr.spatial.point :as point]
            [cmr.spatial.derived :as d])
  (:import cmr.es_spatial_plugin.SpatialScript
           org.elasticsearch.common.xcontent.support.XContentMapValues
           org.elasticsearch.ElasticsearchIllegalArgumentException))

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
    (throw (ElasticsearchIllegalArgumentException.
             (str "Missing one or more of required parameters: "
                  (clojure.string/join parameters ", "))))))

(defn- ords-str->intersects-fn
  "Chooses which intersection function to use. Eventually it will support more than one"
  [ords-str]
  (let [ords (map #(Double. ^String %) (clojure.string/split ords-str #","))]
    (let [ring (apply ring/ords->ring ords)
          ring (d/calculate-derived ring)]

      ;; The function is hardcoded to assume polygon for now
      (fn [polygon]
        (ring/intersects-ring? ring (-> polygon
                                        :rings
                                        first
                                        d/calculate-derived))))))

(defn new-script [logger script-params]
  (let [{:keys [ords] :as params} (extract-params script-params)
        intersects-fn (ords-str->intersects-fn ords)]
    (assert-required-parameters params)
    (SpatialScript. ^Object intersects-fn logger)))


