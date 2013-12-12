(ns cmr.es-spatial-plugin.SpatialSearchPlugin
  (:import cmr.es_spatial_plugin.StringMatchScriptFactory
           org.elasticsearch.script.ScriptModule)
  (:gen-class :extends org.elasticsearch.plugins.AbstractPlugin))


(defn -name [this]
  "spatialsearch-plugin")

(defn -description [this]
  "Adds spatial searching in spherical coordinate system to elastic search.")

(defn -processModule [this module]
  (when (instance? ScriptModule module)
    (let [^ScriptModule module module]
      (.registerScript module "string_match" StringMatchScriptFactory))))

