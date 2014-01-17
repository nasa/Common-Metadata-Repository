(ns cmr.es-spatial-plugin.spatial-script-factory-helper
  "Contains functionality used by SpatialScriptFactory. Seperated into a typical Clojure namespace
  to make it more compatible with REPL development."
  (:require [clojure.string]
            [cmr-spatial.ring :as ring])
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

(defn- ords-str->ring [ords-str]
  (let [ords (map #(Double. ^String %) (clojure.string/split ords-str #","))]
    (apply ring/ords->ring ords)))

(defn new-script [logger script-params]
  (let [{:keys [ords] :as params} (extract-params script-params)]
    (assert-required-parameters params)
    (SpatialScript. ^Object (ords-str->ring ords) logger)))


