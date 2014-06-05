(ns cmr.es-spatial-plugin.SpatialScript
  (:gen-class :extends org.elasticsearch.script.AbstractSearchScript
              :constructors {[Object org.elasticsearch.common.logging.ESLogger] []}
              :init init
              :exposes-methods {doc getDoc fields getFields}
              :state data))

(import 'cmr.es_spatial_plugin.SpatialScript)

(defn- -init [intersects-fn logger]
  [[] {:intersects-fn intersects-fn
       :logger logger}])

(defn- intersects-fn [^SpatialScript this]
  (:intersects-fn (.data this)))

(defn- logger [^SpatialScript this]
  (:logger (.data this)))

(defn -run [^SpatialScript this]
  (let [intersects? (var-get (find-var 'cmr.es-spatial-plugin.spatial-script-helper/doc-intersects?))]
    (intersects? (logger this) (.getFields this) (intersects-fn this))))
