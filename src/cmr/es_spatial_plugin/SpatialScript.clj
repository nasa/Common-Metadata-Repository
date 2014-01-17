(ns cmr.es-spatial-plugin.SpatialScript
  (:import org.elasticsearch.common.logging.ESLogger)
  (:gen-class :extends org.elasticsearch.script.AbstractSearchScript
              :constructors {[Object org.elasticsearch.common.logging.ESLogger] []}
              :init init
              :exposes-methods {doc getDoc fields getFields}
              :state data))

(import 'cmr.es_spatial_plugin.SpatialScript)

(defn- -init [ring logger]
  [[] {:ring ring
       :logger logger}])

(defn- ring [^SpatialScript this]
  (:ring (.data this)))

(defn- info [^SpatialScript this ^String msg]
  (let [^ESLogger logger (:logger (.data this))]
    (.info logger msg nil)))

(defn lookup
  "Temporary helper to lookup a clojure variable dynamically and return it's value.
  The purpose of this is to allow easy testing with the REPL. AOT compiled code prevents
  refreshing at the repl. Vars found in namespaces looked up this way aren't AOT compiled."
  [sym]
  (var-get (find-var sym)))

(defn -run [^SpatialScript this]
  (let [intersects? (lookup 'cmr.es-spatial-plugin.spatial-script-helper/doc-intersects?)]
    (intersects? this (.getFields this) (ring this))))
