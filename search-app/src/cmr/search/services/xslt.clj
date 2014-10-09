(ns cmr.search.services.xslt
  "Provides functions for invoking xsl on metadata."
  (:require [clojure.java.io :as io]
            [cmr.common.cache :as cache])
  (:import java.io.StringReader
           java.io.StringWriter
           javax.xml.transform.stream.StreamSource
           javax.xml.transform.stream.StreamResult
           [javax.xml.transform
            TransformerFactory
            Templates
            URIResolver]))

(def xsl-transformer-cache-name
  "This is the name of the cache to use for XSLT transformer templates. Templates are thread
  safe but transformer instances are not.
  http://www.onjava.com/pub/a/onjava/excerpt/java_xslt_ch5/?page=9"
  :xsl-transformer-templates)

(defn- create-uri-resolver
  "Creates an instance of the URIResolver interface that will direct all paths within the xslt
  folder on the classpath."
  ^URIResolver []
  (proxy [URIResolver] []
    (resolve
      [href base]
      (new StreamSource (io/reader (io/resource (str "xslt/" href)))))))

(defn- xsl->transformer-template
  "Returns the xsl transformer template for the given xsl file"
  [xsl]
  (with-open [xsl-reader (io/reader xsl)]
    (let [xsl-resource (new StreamSource xsl-reader)
          factory (TransformerFactory/newInstance)]
      (.setURIResolver factory (create-uri-resolver))
      (.newTemplates factory xsl-resource))))

(defn transform
  "Transforms the given xml by appling the given xsl"
  [context xml xsl]
  (let [^Templates template (cache/cache-lookup
                              (cache/context->cache context xsl-transformer-cache-name)
                              xsl
                              #(xsl->transformer-template xsl))
        transformer (.newTransformer template)
        source (new StreamSource (new StringReader xml))
        result (new StreamResult (new StringWriter))]
    (.transform transformer source result)
    (.toString (.getWriter result))))

