(ns cmr.es-spatial-plugin.SpatialSearchPlugin
  (:import cmr.es_spatial_plugin.FooBarSearchScriptFactory)
  (:gen-class :extends org.elasticsearch.plugins.AbstractPlugin))


(defn -name [this]
  "spatialsearch-plugin")

(defn -description [this]
  "Adds spatial searching in spherical coordinate system to elastic search.")

(defn onModule [module]
  (.registerScript module "foo_bar" FooBarSearchScriptFactory))

