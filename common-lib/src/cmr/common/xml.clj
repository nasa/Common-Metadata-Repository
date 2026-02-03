(ns cmr.common.xml
  "Contains XML helpers for extracting data from XML structs created using clojure.data.xml.
  See the test file for examples."
  (:require
   [clojure.string :as string]
   [cmr.common.log :refer [warn]]
   [cmr.common.date-time-parser :as p])
  #_{:clj-kondo/ignore [:unused-import]}
  (:import
   java.io.StringReader
   java.io.StringWriter
   javax.xml.parsers.DocumentBuilderFactory
   javax.xml.parsers.SAXParser
   javax.xml.parsers.SAXParserFactory
   javax.xml.transform.sax.SAXSource
   javax.xml.transform.stream.StreamSource
   javax.xml.validation.Schema
   javax.xml.validation.SchemaFactory
   javax.xml.validation.Validator
   javax.xml.XMLConstants
   org.w3c.dom.Node
   org.w3c.dom.bootstrap.DOMImplementationRegistry
   org.w3c.dom.ls.DOMImplementationLS
   org.w3c.dom.ls.LSSerializer
   org.xml.sax.EntityResolver
   org.xml.sax.ext.DefaultHandler2
   org.xml.sax.InputSource
   org.xml.sax.SAXException
   org.xml.sax.SAXParseException
   org.xml.sax.XMLReader))

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
   calling the function. This has not been optimized for speed. Works by recursively replacing
   elements that are the parents of the updated nodes. Calls updater-fn with the element and any
   supplied args."
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

(defn string-at-path
  "Extracts a string from the given path in the XML structure."
  ^String [xml-struct path]
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
      (catch Exception _e
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

(defn create-sax-parser-factory
  "Creates a SAX parser factory instance and sets specific features."
  []
  (doto (SAXParserFactory/newInstance)
    ;Important: disables DTD validation specific features
    (.setValidating false)
    (.setNamespaceAware true)
    ;; Disable external general and parameter entities
    (.setFeature "http://xml.org/sax/features/external-general-entities" false)
    (.setFeature "http://xml.org/sax/features/external-parameter-entities" false)
    (.setFeature "http://apache.org/xml/features/nonvalidating/load-external-dtd" false)
    ;; Enable secure processing feature
    (.setFeature XMLConstants/FEATURE_SECURE_PROCESSING true)))

(defn create-schema-factory
  "Creates a schema factory instance and does not allow access to an external DTD."
  []
  (doto (SchemaFactory/newInstance XMLConstants/W3C_XML_SCHEMA_NS_URI)
    (.setProperty XMLConstants/ACCESS_EXTERNAL_DTD "")))

(defn validate-xml
  "Validates the XML against the schema in the given resource. schema-resource should be a classpath
  resource as returned by clojure.java.io/resource.
  Returns a list of errors in the XML schema.
  DTD validation is turned off and an error message is logged."
  [^java.net.URL schema-resource xml]
  (let [sax-parser-factory (create-sax-parser-factory)
        schema-factory (create-schema-factory)
        schema (try
                 ;; Loads the schema as a file url
                 (.newSchema schema-factory schema-resource)
                 (catch Exception e
                   (warn "Failed to load schema as URL, attempting as string" (.getMessage e))
                   ;; Loads the schema as a string needed for unit test
                   (.newSchema schema-factory (StreamSource. (StringReader. schema-resource)))))
        sax-parser (.newSAXParser sax-parser-factory)
        xml-reader (.getXMLReader sax-parser)
        ;; Set a "blanking" EntityResolver on the XMLReader
        ;; This ensures that even if a DOCTYPE is present, the resolver prevents fetching any external DTD files.
        _ (.setEntityResolver xml-reader
                              (reify EntityResolver
                                (resolveEntity [_ _public-id _system-id]
                                  (InputSource. (StringReader. "")))))
        ;; Create a SAXSource with the configured XMLReader.
        input-source (InputSource. (StringReader. xml))
        sax-source   (SAXSource. xml-reader input-source)
        validator    (.newValidator schema)
        errors-atom (atom [])]
    (try
      (.setErrorHandler validator (create-error-handler errors-atom))
      (.validate validator sax-source)
      (catch SAXParseException e
        ;; An exception can be thrown if it is completely invalid XML.
        (reset! errors-atom [(sax-parse-exception->str e)]))
      (catch SAXException e
        (reset! errors-atom [(.getMessage e)]))
      (catch Exception e
        ;; This is to catch XML bomb.
        (reset! errors-atom [(.getMessage e)])))
    ;; Check if a DOCTYPE is used. If so log the issue as a warning. For CMR-11010
    (when-not (nil? xml)
      (let [string-to-check "<!doctype"
            xml-lowercase (string/lower-case xml)]
        (when (string/includes? xml-lowercase string-to-check)
          (warn "XML record includes DOCTYPE declaration, which is discouraged for security reasons."))))
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
        _dom-config (doto (.getDomConfig writer)
                      (.setParameter "format-pretty-print" true)
                      (.setParameter "xml-declaration" keep-declaration))
        output (doto (.createLSOutput impl)
                 (.setCharacterStream (StringWriter.))
                 (.setEncoding "UTF-8"))]
    (.write writer document output)
    ;; manual massage to handle newer JDK 9 and later behavior with missing newlines
    (-> (str (.getCharacterStream output))
        (as-> data (if keep-declaration
                     (string/replace-first data #">" ">\n")
                     data))
        (string/replace #"\s+xmlns:" "\n    xmlns:"))))
