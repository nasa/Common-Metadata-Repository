(ns cmr.es-spatial-plugin.FooBarSearchScript
  (:import org.elasticsearch.common.logging.ESLogger
           org.elasticsearch.index.fielddata.ScriptDocValues$Strings)
  (:gen-class :extends org.elasticsearch.script.AbstractSearchScript
              :constructors {[String org.elasticsearch.common.logging.ESLogger] []}
              :init init
              :state data))


(comment
  (cmr.es_spatial_plugin.FooBarSearchScript. nil nil)

  (import 'cmr.es_spatial_plugin.FooBarSearchScriptFactory)

  (cmr.es_spatial_plugin.FooBarSearchScriptFactory. nil)

)

(defn- -init [field-name logger]
  [[] {:field-name field-name
       :logger logger}])

(defn- field-name [this]
  (:field-name (.data this)))

(defn- debug [this msg]
  (let [logger (:logger (.data this))]
    (.debug logger msg)))

(defn -run [this]
  (let [field (.get (.doc this) (field-name this))]
    (if (and (not (nil? field))
             (not (.isEmpty field)))
      (let [value (.getValue field)
            result (or (= value "foo") (= value "bar"))]
        (debug this (str "FooBar field is [" value "] result: " result))
        result)
      (do
        (debug this "FooBar field is not set")
        false))))

