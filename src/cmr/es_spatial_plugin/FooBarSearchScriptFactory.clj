(ns cmr.es-spatial-plugin.FooBarSearchScriptFactory
  (:import cmr.es_spatial_plugin.FooBarSearchScript
           org.elasticsearch.common.xcontent.support.XContentMapValues
           org.elasticsearch.ElasticSearchIllegalArgumentException
           org.elasticsearch.common.logging.Loggers
           org.elasticsearch.common.settings.Settings)
  (:gen-class
    :extends org.elasticsearch.common.component.AbstractComponent
    :implements [org.elasticsearch.script.NativeScriptFactory]
    :constructors {^{org.elasticsearch.common.inject.Inject true} [org.elasticsearch.common.settings.Settings] [org.elasticsearch.common.settings.Settings]}
    :init init
    :state data))

(import 'cmr.es_spatial_plugin.FooBarSearchScriptFactory)

(defn- -init [^Settings settings]

  (let [logger (Loggers/getLogger FooBarSearchScriptFactory settings nil)]
    [[settings] {:logger logger}]))

(defn -newScript [this params]
  (if-let [field-name (when params
                        (XContentMapValues/nodeStringValue
                          (.get params "field") nil))]
    (FooBarSearchScript. field-name (:logger (.data this)))
    (throw (ElasticSearchIllegalArgumentException.
             "Missing the field parameter."))))



