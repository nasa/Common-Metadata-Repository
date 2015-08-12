(ns cmr.umm-spec.util
  "This contains utilities for the UMM Spec code."
  (:require [cheshire.core :as json]
            [cheshire.factory :as factory]
            [clojure.data.xml :as x]
            [cmr.common.xml :as v]
            [clojure.java.io :as io]))

(defn load-json-resource
  "Loads a json resource from the classpath. The JSON file may contain comments which are ignored"
  [json-resource]
  (binding [factory/*json-factory* (factory/make-json-factory
                                     {:allow-comments true})]
    (json/decode (slurp json-resource) true)))

(def metadata-format->schema
  {:echo10 "xml-schemas/echo10/Collection.xsd"
   :dif "xml-schemas/dif9/dif_v9.9.3.xsd"
   :dif10 "xml-schemas/dif10/dif_v10.1.xsd"
   :iso19115 "xml-schemas/iso19115_2/schema/1.0/ISO19115-2_EOS.xsd"
   :iso-smap "xml-schemas/iso_smap/schema.xsd"})

(defn validate-xml
  "Validates the XML against the schema for the given format."
  [metadata-format xml]
  (v/validate-xml (io/resource (metadata-format->schema metadata-format)) xml))