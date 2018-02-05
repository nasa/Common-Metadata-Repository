(ns cmr.elasticsearch.plugins.spatial.script.core
  (:gen-class
   :name cmr.elasticsearch.plugins.SpatialScript
   :extends org.elasticsearch.script.AbstractSearchScript
   :constructors {[Object org.elasticsearch.common.logging.ESLogger] []}
   :init init
   :exposes-methods {doc getDoc fields getFields}
   :state data))

(import 'cmr.elasticsearch.plugins.SpatialScript)

(defn get-intersects?-fn
  []
  (-> 'cmr.elasticsearch.plugins.spatial.script.helper/doc-intersects?
      find-var
      var-get))

(defn- -init [intersects-fn logger]
  [[] {:intersects-fn intersects-fn
       :logger logger}])

(defn- intersects-fn [^SpatialScript this]
  (:intersects-fn (.data this)))

(defn- logger [^SpatialScript this]
  (:logger (.data this)))

(defn -run [^SpatialScript this]
  (let [intersects? (get-intersects?-fn)]
    (intersects? (logger this) (.getFields this) (intersects-fn this))))
