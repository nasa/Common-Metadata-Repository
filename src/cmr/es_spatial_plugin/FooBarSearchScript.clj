(ns cmr.es-spatial-plugin.FooBarSearchScript
  (:import org.elasticsearch.common.logging.ESLogger
           org.elasticsearch.index.fielddata.ScriptDocValues$Strings)
  (:gen-class :extends org.elasticsearch.script.AbstractSearchScript
              :constructors {[String org.elasticsearch.common.logging.ESLogger] []}
              :init init
              :exposes-methods {doc getDoc}
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

(defn- info [this ^String msg]
  (let [logger (:logger (.data this))]
    (.info logger msg nil)))

(defn -run [this]
  (let [field (.get (.getDoc this) (field-name this))]
    (if (and (not (nil? field))
             (not (.isEmpty field)))
      (let [value (.getValue field)
            result (or (= value "foo") (= value "bar"))]
        (info this (str "FooBar field is [" value "] result: " result))
        result)
      (do
        (info this "FooBar field is not set")
        false))))

