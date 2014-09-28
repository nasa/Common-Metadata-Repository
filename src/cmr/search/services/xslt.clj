(ns cmr.search.services.xslt
  "Provides functions for invoking xsl on metadata."
  (:require [clojure.java.io :as io]
            [cmr.common.cache :as cache])
  (:import javax.xml.transform.TransformerFactory
           java.io.StringReader
           java.io.StringWriter
           javax.xml.transform.stream.StreamSource
           javax.xml.transform.stream.StreamResult
           javax.xml.transform.Templates))

(def xsl-transformer-cache-name
  "This is the name of the cache to use for XSLT transformer templates. Templates are thread
  safe but transformer instances are not.
  http://www.onjava.com/pub/a/onjava/excerpt/java_xslt_ch5/?page=9"
  :xsl-transformer-templates)

(defn context->xsl-transformer-cache
  [context]
  (get-in context [:system :caches xsl-transformer-cache-name]))

(defn- xsl->transformer-template
  "Returns the xsl transformer template for the given xsl file"
  [xsl]
  (let [xsl-resource (new StreamSource (io/file xsl))
        factory (TransformerFactory/newInstance)]
    (.newTemplates factory xsl-resource)))

(defn transform
  "Transforms the given xml by appling the given xsl"
  [context xml xsl]
  (let [^Templates template (cache/cache-lookup
                              (context->xsl-transformer-cache context)
                              xsl
                              #(xsl->transformer-template xsl))
        transformer (.newTransformer template)
        source (new StreamSource (new StringReader xml))
        result (new StreamResult (new StringWriter))]
    (.transform transformer source result)
    (.toString (.getWriter result))))

