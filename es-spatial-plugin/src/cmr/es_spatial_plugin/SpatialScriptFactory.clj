(ns cmr.es-spatial-plugin.SpatialScriptFactory
  (:import org.elasticsearch.common.logging.Loggers
           org.elasticsearch.common.settings.Settings)
  (:gen-class
    :extends org.elasticsearch.common.component.AbstractComponent
    :implements [org.elasticsearch.script.NativeScriptFactory]
    :constructors {^{org.elasticsearch.common.inject.Inject true} [org.elasticsearch.common.settings.Settings] [org.elasticsearch.common.settings.Settings]}
    :init init
    :state data))

(import 'cmr.es_spatial_plugin.SpatialScriptFactory)

(defn- -init [^Settings settings]
  (let [logger (Loggers/getLogger SpatialScriptFactory settings nil)]
    [[settings] {:logger logger}]))

(defn -newScript [^SpatialScriptFactory this script-params]
  (let [new-script (var-get (find-var 'cmr.es-spatial-plugin.spatial-script-factory-helper/new-script))]
    (new-script (:logger (.data this)) script-params)))


