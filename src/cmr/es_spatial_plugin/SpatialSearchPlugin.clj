(ns cmr.es-spatial-plugin.SpatialSearchPlugin
  (:import cmr.es_spatial_plugin.FooBarSearchScriptFactory
           cmr.es_spatial_plugin.StringMatchScriptFactory
           org.elasticsearch.script.ScriptModule)
  (:require [clojure.tools.logging :as log])
  (:gen-class :extends org.elasticsearch.plugins.AbstractPlugin))


(defn -name [this]
  (log/warn "name called!")
  "spatialsearch-plugin")

(defn -description [this]
  (log/warn "description called!")
  "Adds spatial searching in spherical coordinate system to elastic search.")

(defn -processModule [this module]
  (when (instance? ScriptModule module)
    (log/info "processModule!")
    (.registerScript module "foo_bar" FooBarSearchScriptFactory)
    (.registerScript module "string_match" StringMatchScriptFactory)
    ))

