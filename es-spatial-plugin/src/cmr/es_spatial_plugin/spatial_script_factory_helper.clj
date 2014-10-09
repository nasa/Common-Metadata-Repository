(ns cmr.es-spatial-plugin.spatial-script-factory-helper
  "Contains functionality used by SpatialScriptFactory. Seperated into a typical Clojure namespace
  to make it more compatible with REPL development."
  (:require [clojure.string :as str]
            [cmr.spatial.point :as point]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.derived :as d]
            [cmr.spatial.serialize :as srl]
            [cmr.spatial.relations :as relations]
            [cmr.es-spatial-plugin.SpatialScript])
  (:import cmr.es_spatial_plugin.SpatialScript
           org.elasticsearch.common.xcontent.support.XContentMapValues
           org.elasticsearch.ElasticsearchIllegalArgumentException))

(def parameters
  "The parameters to the Spatial script"
  [:ords :ords-info])

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

(defn- params->spatial-shape
  [params]
  (let [{:keys [ords-info ords]} (->> params
                                      (map
                                        (fn [[k v]]
                                          ;; Convert the comma separated string into a vector of integers.
                                          [k (map #(Integer. ^String %) (str/split v #","))]))
                                      (into {}))]
    (first (srl/ords-info->shapes ords-info ords))))

(defn new-script [logger script-params]
  (try
    (let [params (extract-params script-params)
          shape (params->spatial-shape params)
          intersects-fn (relations/shape->intersects-fn shape)]
      (assert-required-parameters params)
      (SpatialScript. ^Object intersects-fn logger))
    (catch Throwable e
      (.printStackTrace e)
      (throw e))))


