(ns cmr.common.xml
  "Contains XML helpers for extracting data from XML structs created using clojure.data.xml.
  See the test file for examples."
  (:require [cmr.common.date-time-parser :as p]
            [cmr.common.services.errors :as errors]
            [clojure.string :as string]
            [clojure.java.io :as io])
  (:import javax.xml.validation.SchemaFactory
           javax.xml.XMLConstants
           javax.xml.transform.stream.StreamSource
           org.xml.sax.ext.DefaultHandler2
           java.io.StringReader
           java.io.StringWriter
           org.w3c.dom.Node
           org.w3c.dom.bootstrap.DOMImplementationRegistry
           org.w3c.dom.ls.DOMImplementationLS
           org.w3c.dom.ls.LSSerializer
           org.xml.sax.InputSource
           org.xml.sax.SAXParseException
           javax.xml.parsers.DocumentBuilderFactory))

(defn remove-xml-processing-instructions
  "Removes xml processing instructions from XML so it can be embedded in another XML document"
  [xml]
  (let [processing-regex #"<\?.*?\?>"
        doctype-regex #"<!DOCTYPE.*?>"]
    (-> xml
        (string/replace processing-regex "")
        (string/replace doctype-regex ""))))

(defn escape-xml
  "Escape special characters in given string for xml representation."
  [s]
  (string/escape s {\< "&lt;"
                    \> "&gt;"
                    \& "&amp;"
                    \' "&apos;"
                    \" "&quot;"}))

(defn- children-by-tag
  "Extracts the child elements with the given tag name."
  [element tag]
  (filter #(= tag (:tag %)) (:content element)))

(defn elements-at-path
  "Extracts the children down the specified path."
  [element path]
  (reduce (fn [elements tag]
            (mapcat #(children-by-tag % tag) elements))
          [element]
          path))

(defn update-elements-at-path
  "Calls updater-fn with each element at the specified path. Replaces the element with the result of
  calling the function. This has not been optimized for speed. Works by recursively replacing elements
  that are the parents of the updated nodes. Calls updater-fn with the element and any supplied args."
  [element path updater-fn & args]
  (if (zero? (count path))
    (apply updater-fn (cons element args))
    (let [tag (first path)
          rest-of-path (rest path)]
      (update-in
        element [:content]
        (fn [children]
          (map (fn [child]
                 (if (= tag (:tag child))
                   (apply update-elements-at-path
                          (concat [child rest-of-path updater-fn] args))
                   child))
               children))))))

(defn element-at-path
  "Returns a single element from within an XML structure at the given path."
  [xml-struct path]
  (first (elements-at-path xml-struct path)))

(defn contents-at-path
  "Pulls the contents from the elements found at the given path."
  [xml-struct path]
  (map :content (elements-at-path xml-struct path)))

(defn content-at-path
  "Extracts the content from the first element at the given path."
  [xml-struct path]
  (first (contents-at-path xml-struct path)))

(defn attrs-at-path
  "This is a helper that will pull the XML attributes from the xml-struct at the given path."
  [xml-struct path]
  (some-> (element-at-path xml-struct path)
          :attrs))

(defn strings-at-path
  "Extracts all the strings from the given path in the XML structure."
  [xml-struct path]
  (map str (apply concat (contents-at-path xml-struct path))))

(defn ^String string-at-path
  "Extracts a string from the given path in the XML structure."
  [xml-struct path]
  (first (strings-at-path xml-struct path)))

(defn long-at-path
  "Extracts a long number from the given path in the XML structure."
  [xml-struct path]
  (when-let [^String s (string-at-path xml-struct path)]
    (Long. s)))

(defn double-at-path
  "Extracts a double number from the given path in the XML structure."
  [xml-struct path]
  (when-let [^String s (string-at-path xml-struct path)]
    (Double. s)))

(defn integer-at-path
  "Extracts a integer number from the given path in the XML structure."
  [xml-struct path]
  (when-let [^String s (string-at-path xml-struct path)]
    ;; This is used for Echo10 start-orbit-number and stop-orbit-number.
    ;; it's possible that they are not integers because xml schema
    ;; allows them to be double. But we need them to be integers.
    (try
      (Integer. s)
      (catch Exception e
        s))))

(defn bool-at-path
  "Extracts a boolean from the given path in the XML structure."
  [xml-struct path]
  (when-let [^String s (string-at-path xml-struct path)]
    ;; See http://www.w3.org/TR/2004/REC-xmlschema-2-20041028/datatypes.html#boolean
    (or (= "true" s) (= "1" s))))

(defn datetimes-at-path
  "Extracts all the datetimes from the given path in the XML structure."
  [xml-struct path]
  (map p/parse-datetime (strings-at-path xml-struct path)))

(defn datetime-at-path
  "Extracts a datetime from the given path in the XML structure."
  [xml-struct path]
  (first (datetimes-at-path xml-struct path)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; XML Schema Validation


(defn- sax-parse-exception->str
  "Converts a SaxParseException to a String"
  [^SAXParseException e]
  (format "Exception while parsing invalid XML: Line %d - %s" (.getLineNumber e) (.getMessage e)))

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
  [^java.net.URL schema-resource xml]
  (let [^SchemaFactory factory (SchemaFactory/newInstance XMLConstants/W3C_XML_SCHEMA_NS_URI)
        schema (.newSchema factory schema-resource)
        validator (.newValidator schema)
        errors-atom (atom [])]
    (.setErrorHandler validator (create-error-handler errors-atom))
    (try
      (.validate validator (StreamSource. (StringReader. xml)))
      (catch SAXParseException e
        ;; An exception can be thrown if it is completely invalid XML.
        (reset! errors-atom [(sax-parse-exception->str e)])))
    (seq @errors-atom)))

(defn pretty-print-xml
  "Returns the pretty printed xml for the given xml string"
  [^String xml]
  (let [src (InputSource. (StringReader. xml))
        builder (.newDocumentBuilder (DocumentBuilderFactory/newInstance))
        document (.getDocumentElement (.parse builder src))
        keep-declaration (.startsWith xml "<?xml")
        registry (DOMImplementationRegistry/newInstance)
        ^DOMImplementationLS impl (.getDOMImplementation registry "LS")
        writer (.createLSSerializer impl)
        dom-config (.getDomConfig writer)
        output (.createLSOutput impl)]
    (.setParameter dom-config "format-pretty-print" true)
    (.setParameter dom-config "xml-declaration" keep-declaration)
    (.setCharacterStream output (new StringWriter))
    (.setEncoding output "UTF-8")
    (.write writer document output)
    (str (.getCharacterStream output))))
