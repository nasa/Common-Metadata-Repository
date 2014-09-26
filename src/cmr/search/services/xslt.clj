(ns cmr.search.services.xslt
  "Provides functions for invoking xsl on metadata."
  (:require [clojure.java.io :as io])
  (:import javax.xml.transform.TransformerFactory
           java.io.StringReader
           java.io.StringWriter
           javax.xml.transform.stream.StreamSource
           javax.xml.transform.stream.StreamResult))

(def xsl-transformers
  "An atom containing a map of xsl file name and compiled transformers."
  (atom {}))

(defn- xsl->transformer
  "Returns the xsl transformer for the given xsl file"
  [xsl]
  (let [xsl-resource (new StreamSource (io/file (io/resource xsl)))
        factory (TransformerFactory/newInstance)]
    (.newTransformer factory xsl-resource)))

(defn- create-xsl-transformer
  "Creates a xsl transformer and sets it in xsl-transformers"
  [xsl]
  (swap! xsl-transformers #(assoc % xsl (xsl->transformer xsl))))

(defn transform
  "Transforms the given xml by appling the given xsl"
  [xml xsl]
  ;; create and cache the transformer if it has not been created
  (when-not (get @xsl-transformers xsl)
    (create-xsl-transformer xsl))

  (let [transformer (get @xsl-transformers xsl)
        source (new StreamSource (new StringReader xml))
        result (new StreamResult (new StringWriter))]
    (.transform transformer source result)
    (.toString (.getWriter result))))
