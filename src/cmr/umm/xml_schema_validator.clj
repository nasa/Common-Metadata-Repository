(ns cmr.umm.xml-schema-validator
  "Provides functions for validating XML against a schema."
  (:require [clojure.java.io :as io])
  (:import javax.xml.validation.SchemaFactory
           javax.xml.XMLConstants
           javax.xml.transform.stream.StreamSource
           org.xml.sax.ext.DefaultHandler2
           java.io.StringReader))

(defn- sax-parse-exception->str
  "Converts a SaxParseException to a String"
  [e]
  (format "Line %d - %s" (.getLineNumber e) (.getMessage e)))

(defn- create-error-handler
  "Creates an instance of DefaultHandler2 that will save all schema errors in the errors-atom."
  [errors-atom]
  (proxy [DefaultHandler2] []
    (error
      [e]
      (swap! errors-atom conj (sax-parse-exception->str e)))
    (fatalError
      [e]
      (swap! errors-atom conj (sax-parse-exception->str e)))))

(defn validate-xml
  "Validates the XML against the schema in the given resource. schema-resource should be a classpath
  resource as returned by clojure.java.io/resource.
  Returns a list of errors in the XML schema."
  [schema-resource xml]
  (let [factory (SchemaFactory/newInstance XMLConstants/W3C_XML_SCHEMA_NS_URI)
        schema (.newSchema factory schema-resource)
        validator (.newValidator schema)
        errors-atom (atom [])]
    (.setErrorHandler validator (create-error-handler errors-atom))
    (.validate validator (StreamSource. (StringReader. xml)))
    @errors-atom))
