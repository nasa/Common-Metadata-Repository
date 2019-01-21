(ns cmr.common.xml.xslt
  "Provides functions for invoking xsl on metadata."
  (:require
   [clojure.java.io :as io])
  (:import
   (java.io StringReader StringWriter)
   (javax.xml.transform TransformerFactory Templates URIResolver)
   (javax.xml.transform.stream StreamSource StreamResult)))

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
          factory (TransformerFactory/newInstance)]
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
