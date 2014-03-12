(ns cmr.umm.echo10.collection
  "Contains functions for parsing and generating the ECHO10 dialect."
  (:require [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [cmr.umm.collection :as c]
            [cmr.umm.xml-schema-validator :as v]))

(defn- content-at-path
  "This is a helper that will pull the XML content from the xml-struct at the given path.
  Note this implementation is not likely to be particularly fast. We should investigate that as part
  of another future issue."
  [xml-struct path]
  (if (sequential? path)
    (reduce content-at-path xml-struct path)
    (cond
      (sequential? xml-struct) (->> xml-struct (filter #(= path (:tag %))) first :content)
      (map? xml-struct) (when (= path (:tag xml-struct)) (:content xml-struct))
      :else (throw (Exception.
                     (format
                       "Unexpected xml-struct at path. path: [%s] xml-struct: [%s]"
                       path xml-struct))))))

(defn- string-at-path
  "Extracts a string from the given path in the XML structure."
  [xml-struct path]
  (-> (content-at-path xml-struct path) first str))

(defn- xml-elem->Product
  "Returns a UMM Product from a parsed Collection XML structure"
  [xml-struct]
  (let [collection-content (content-at-path xml-struct [:Collection])]
    (c/map->Product {:short-name (string-at-path collection-content [:ShortName])
                     :long-name (string-at-path collection-content [:LongName])
                     :version-id (string-at-path collection-content [:VersionId])})))

(defn- xml-elem->Collection
  "Returns a UMM Product from a parsed Collection XML structure"
  [xml-struct]
  (let [collection-content (content-at-path xml-struct [:Collection])
        product (xml-elem->Product xml-struct)]
    (c/map->UmmCollection {:entry-id (str (:short-name product) "_" (:version-id product))
                           :entry-title (string-at-path collection-content [:DataSetId])
                           :product product})))

(defn parse-collection
  "Parses ECHO10 XML into a UMM Collection record."
  [xml]
  (xml-elem->Collection (x/parse-str xml)))

(defn generate-collection
  "Generates ECHO10 XML from a UMM Collection record."
  [collection]

  (let [{{:keys [short-name long-name version-id]} :product
         dataset-id :entry-title} collection]
    (x/emit-str
      (x/element :Collection {}
                 (x/element :ShortName {} short-name)
                 (x/element :VersionId {} version-id)
                 ;; required fields that are not implemented yet are stubbed out.
                 (x/element :InsertTime {} "1999-12-31T19:00:00Z")
                 (x/element :LastUpdate {} "1999-12-31T19:00:00Z")
                 (x/element :LongName {} long-name)
                 (x/element :DataSetId {} dataset-id)
                 (x/element :Description {} "stubbed")
                 (x/element :Orderable {} "true")
                 (x/element :Visible {} "true")))))

(defn validate-xml
  "Validates the XML against the ECHO10 schema."
  [xml]
  (v/validate-xml (io/resource "schema/echo10/Collection.xsd") xml))


