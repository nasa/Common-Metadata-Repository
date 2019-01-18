(ns cmr.common.xml.xslt
  "Provides functions for invoking xsl on metadata."
  (:require [clojure.java.io :as io])
  (:import java.io.StringReader
           java.io.StringWriter
           javax.xml.transform.stream.StreamSource
           javax.xml.transform.stream.StreamResult
           [javax.xml.transform
            TransformerFactory
            Templates
            URIResolver]
           net.sf.saxon.TransformerFactoryImpl))

(defn- create-uri-resolver
  "Creates an instance of the URIResolver interface that will direct all paths within the xslt
  folder on the classpath."
  ^URIResolver []
  (proxy [URIResolver] []
    (resolve
      [href base]
      (StreamSource. (io/reader (io/resource (str "xslt/" href)))))))

(defn read-template
  "Returns the xsl transformer template for the given xsl file"
  [f]
  (with-open [r (io/reader f)]
    (let [xsl-resource (StreamSource. r)
          factory (TransformerFactoryImpl.)]
      (.setURIResolver factory (create-uri-resolver))
      (.newTemplates factory xsl-resource))))

(defn transform
  "Transforms the given xml string by appling the given xsl template."
  [xml ^Templates template]
  (let [transformer (.newTransformer template)
        source (new StreamSource (new StringReader xml))
        result (new StreamResult (new StringWriter))]
    (.transform transformer source result)
    (str (.getWriter result))))
