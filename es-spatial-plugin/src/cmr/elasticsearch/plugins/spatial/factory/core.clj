(ns cmr.elasticsearch.plugins.spatial.factory.core
  (:import
   org.elasticsearch.common.logging.Loggers
   org.elasticsearch.common.settings.Settings)
  (:gen-class
   :name cmr.elasticsearch.plugins.SpatialScriptFactory
   :extends org.elasticsearch.common.component.AbstractComponent
   :implements [org.elasticsearch.script.NativeScriptFactory]
   :constructors {^{org.elasticsearch.common.inject.Inject true}
                 [org.elasticsearch.common.settings.Settings]
                 [org.elasticsearch.common.settings.Settings]}
   :init init
   :state data))

(defn get-new-script-fn
  []
  (-> 'cmr.elasticsearch.plugins.spatial.factory.helper/new-script
      find-var
      var-get))

(import 'cmr.elasticsearch.plugins.SpatialScriptFactory)

(defn- -init [^Settings settings]
  (let [logger (Loggers/getLogger SpatialScriptFactory settings nil)]
    [[settings] {:logger logger}]))

(defn -newScript [^SpatialScriptFactory this script-params]
  ;; XXX I suspect there's a better way of doing this ...
  (let [new-script (get-new-script-fn)]
    (new-script (:logger (.data this)) script-params)))


