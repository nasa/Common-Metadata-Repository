(ns cmr.umm.echo10.collection
  "Contains functions for parsing and generating the ECHO10 dialect."
  (:require [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [cmr.umm.xml-schema-validator :as v]))


(defn parse-collection
  "Parses ECHO10 XML into a UMM Collection record."
  [xml])

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



