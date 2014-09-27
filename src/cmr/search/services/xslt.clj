(ns cmr.search.services.xslt
  "Provides functions for invoking xsl on metadata."
  (:require [clojure.java.io :as io]
            [cmr.common.cache :as cache])
  (:import javax.xml.transform.TransformerFactory
           java.io.StringReader
           java.io.StringWriter
           javax.xml.transform.stream.StreamSource
           javax.xml.transform.stream.StreamResult))

(def xsl-transformer-cache-name
  :xsl-transformers)

(defn context->xsl-transformer-cache
  [context]
  (get-in context [:system :caches xsl-transformer-cache-name]))

(defn- xsl->transformer
  "Returns the xsl transformer for the given xsl file"
  [xsl]
  (let [xsl-resource (new StreamSource (io/file (io/resource xsl)))
        factory (TransformerFactory/newInstance)]
    (.newTransformer factory xsl-resource)))

(defn transform
  "Transforms the given xml by appling the given xsl"
  [context xml xsl]
  (let [transformer (cache/cache-lookup
                      (context->xsl-transformer-cache context) xsl #(xsl->transformer xsl))
        source (new StreamSource (new StringReader xml))
        result (new StreamResult (new StringWriter))]
    (.transform transformer source result)
    (.toString (.getWriter result))))
