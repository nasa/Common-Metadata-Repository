(ns cmr.es-spatial-plugin.FooBarSearchScriptFactory
  (:import cmr.es_spatial_plugin.FooBarSearchScript
           org.elasticsearch.common.xcontent.support.XContentMapValues
           org.elasticsearch.ElasticSearchIllegalArgumentException)
  (:gen-class
    :extends org.elasticsearch.common.component.AbstractComponent
    :implements [org.elasticsearch.script.NativeScriptFactory]
    :constructors {[org.elasticsearch.common.settings.Settings] [org.elasticsearch.common.settings.Settings]}
    :init init))

(defn- -init [settings]
  [[settings] nil])

(defn -newScript [this params]
  (if-let [field-name (when params
                        (XContentMapValues/nodeStringValue
                          (.get params "field") nil))]
    (FooBarSearchScript. field-name (.logger this))
    (throw (ElasticSearchIllegalArgumentException.
             "Missing the field parameter."))))



