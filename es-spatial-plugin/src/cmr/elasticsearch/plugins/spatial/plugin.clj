(ns cmr.elasticsearch.plugins.spatial.plugin
  (:import
   org.elasticsearch.script.ScriptModule)
  (:gen-class
   :name cmr.elasticsearch.plugins.SpatialSearchPlugin
   :extends org.elasticsearch.plugins.AbstractPlugin))

(import 'cmr.elasticsearch.plugins.SpatialScriptFactory)

(defn -name [this]
  "spatialsearch-plugin")

(defn -description [this]
  "Adds spatial searching in spherical coordinate system to elastic search.")

(defn -processModule [this module]
  (when (instance? ScriptModule module)
    (let [^ScriptModule module module]
      ;; XXX The following is done to avoid AOT problems during development
      ;;     ... would love to hear what the problems were so we could come
      ;;     up with a better solution.
      (require 'cmr.elasticsearch.plugins.spatial.script.helper)
      (require 'cmr.elasticsearch.plugins.spatial.factory.helper)
      (.registerScript module "spatial" SpatialScriptFactory))))

