(ns cmr.umm.echo10.granule
  "Contains functions for parsing and generating the ECHO10 dialect."
  (:require [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [cmr.common.xml :as cx]
            [cmr.umm.granule :as g]
            [cmr.umm.xml-schema-validator :as v]))

(defn- xml-elem->CollectionRef
  "Returns a UMM ref element from a parsed Granule XML structure"
  [granule-content-node]
  (if-let [entry-title (cx/string-at-path granule-content-node [:Collection :DataSetId])]
    (g/collection-ref entry-title)
    (let [short-name (cx/string-at-path granule-content-node [:Collection :ShortName])
          version-id (cx/string-at-path granule-content-node [:Collection :VersionId])]
      (g/collection-ref short-name version-id))))

(defn- xml-elem->Granule
  "Returns a UMM Product from a parsed Granule XML structure"
  [xml-struct]
  (let [coll-ref (xml-elem->CollectionRef xml-struct)]
    (g/map->UmmGranule {:granule-ur (cx/string-at-path xml-struct [:GranuleUR])
                        :collection-ref coll-ref})))

(defn parse-granule
  "Parses ECHO10 XML into a UMM Granule record."
  [xml]
  (xml-elem->Granule (x/parse-str xml)))

(defn generate-granule
  "Generates ECHO10 Granule XML from a UMM Granule record."
  [granule]
  (let [{{:keys [entry-title short-name version-id]} :collection-ref
         granule-ur :granule-ur} granule]
    (x/emit-str
      (x/element :Granule {}
                 (x/element :GranuleUR {} granule-ur)
                 (x/element :InsertTime {} "2012-12-31T19:00:00Z")
                 (x/element :LastUpdate {} "2013-11-30T19:00:00Z")
                 (cond (not (nil? entry-title))
                       (x/element :Collection {}
                                  (x/element :DataSetId {} entry-title))
                       :else (x/element :Collection {}
                                        (x/element :ShortName {} short-name)
                                        (x/element :VersionId {} version-id)))
                 (x/element :RestrictionFlag {} "0.0")
                 (x/element :Orderable {} "true")))))

(defn validate-xml
  "Validates the XML against the Granule ECHO10 schema."
  [xml]
  (v/validate-xml (io/resource "schema/echo10/Granule.xsd") xml))


