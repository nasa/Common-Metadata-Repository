(ns cmr.es-spatial-plugin.StringMatchScriptFactory
  (:require [clojure.string])
  (:import cmr.es_spatial_plugin.StringMatchScript
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

(import 'cmr.es_spatial_plugin.StringMatchScriptFactory)

(defn- -init [^Settings settings]
  (let [logger (Loggers/getLogger StringMatchScriptFactory settings nil)]
    [[settings] {:logger logger}]))

(def parameters
  "The parameters to the String Match script"
  [:field :search-string])

(defn- extract-params
  "Extracts the parameters from the params map given in the script."
  [script-params]
  (when script-params
    (into {} (for [param parameters]
               [param (XContentMapValues/nodeStringValue
                        (get script-params (name param)) nil)]))))

(defn- assert-required-parameters
  "Asserts that all the parameters are supplied or it throws an exception."
  [params]
  (when-not (every? params parameters)
    (throw (ElasticSearchIllegalArgumentException.
             (str "Missing one or more of required parameters: "
                  (clojure.string/join parameters ", "))))))

(defn -newScript [^StringMatchScriptFactory this script-params]
  (let [{:keys [field search-string] :as params} (extract-params script-params)
        logger (:logger (.data this))]
    (assert-required-parameters params)
    (StringMatchScript. field search-string logger)))


