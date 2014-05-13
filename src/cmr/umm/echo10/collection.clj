(ns cmr.umm.echo10.collection
  "Contains functions for parsing and generating the ECHO10 dialect."
  (:require [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]
            [cmr.umm.xml-schema-validator :as v]
            [cmr.umm.echo10.collection.temporal :as t]
            [cmr.umm.echo10.collection.product-specific-attribute :as psa]
            [cmr.umm.echo10.collection.platform :as platform]
            [cmr.umm.echo10.collection.campaign :as cmpgn]
            [cmr.umm.echo10.collection.two-d-coordinate-system :as two-d]
            [cmr.umm.echo10.collection.org :as org]
            [cmr.umm.echo10.core]
            [camel-snake-kebab :as csk])
  (:import cmr.umm.collection.UmmCollection))

(defn- xml-elem->Product
  "Returns a UMM Product from a parsed Collection Content XML structure"
  [collection-content]
  (c/map->Product {:short-name (cx/string-at-path collection-content [:ShortName])
                   :long-name (cx/string-at-path collection-content [:LongName])
                   :version-id (cx/string-at-path collection-content [:VersionId])
                   :processing-level-id (cx/string-at-path collection-content [:ProcessingLevelId])}))

(defn- xml-elem->SpatialCoverage
  "Returns a UMM Product from a parsed Collection XML structure"
  [xml-struct]
  (if-let [spatial-elem (cx/element-at-path xml-struct [:Spatial])]
    (let [gsr (csk/->kebab-case-keyword (cx/string-at-path spatial-elem [:GranuleSpatialRepresentation]))]
      (c/map->SpatialCoverage
        {:granule-spatial-representation gsr}))))

(defn- xml-elem->Collection
  "Returns a UMM Product from a parsed Collection XML structure"
  [xml-struct]
  (let [product (xml-elem->Product xml-struct)]
    (c/map->UmmCollection
      {:entry-id (str (:short-name product) "_" (:version-id product))
       :entry-title (cx/string-at-path xml-struct [:DataSetId])
       :product product
       :spatial-keywords (seq (cx/strings-at-path xml-struct [:SpatialKeywords :Keyword]))
       :temporal (t/xml-elem->Temporal xml-struct)
       :platforms (platform/xml-elem->Platforms xml-struct)
       :product-specific-attributes (psa/xml-elem->ProductSpecificAttributes xml-struct)
       :projects (cmpgn/xml-elem->Campaigns xml-struct)
       :two-d-coordinate-systems (two-d/xml-elem->TwoDCoordinateSystems xml-struct)
       :spatial-coverage (xml-elem->SpatialCoverage xml-struct)
       :organizations (org/xml-elem->Organizations xml-struct)})))

(defn generate-spatial
  "Generates the Spatial element from spatial coverage"
  [spatial-coverage]
  (when spatial-coverage
    (let [gsr (csk/->SNAKE_CASE_STRING (:granule-spatial-representation spatial-coverage))]
      (x/element :Spatial {}
                 (x/element :GranuleSpatialRepresentation {} gsr)))))

(defn parse-collection
  "Parses ECHO10 XML into a UMM Collection record."
  [xml]
  (xml-elem->Collection (x/parse-str xml)))

(extend-protocol cmr.umm.echo10.core/UmmToEcho10Xml
  UmmCollection
  (umm->echo10-xml
    ([collection]
     (cmr.umm.echo10.core/umm->echo10-xml collection false))
    ([collection indent?]
    (let [{{:keys [short-name long-name version-id processing-level-id]} :product
           dataset-id :entry-title
           :keys [organizations spatial-keywords temporal platforms product-specific-attributes
                  projects two-d-coordinate-systems spatial-coverage]} collection
           emit-fn (if indent? x/indent-str x/emit-str)]
      (emit-fn
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
                   (x/element :Visible {} "true")
                   ;; archive center to follow processing center
                   (org/generate-processing-center organizations)
                   (when processing-level-id
                     (x/element :ProcessingLevelId {} processing-level-id))
                   (org/generate-archive-center organizations)
                   (when spatial-keywords
                     (x/element :SpatialKeywords {}
                                (for [spatial-keyword spatial-keywords]
                                  (x/element :Keyword {} spatial-keyword))))
                   (t/generate-temporal temporal)
                   (platform/generate-platforms platforms)
                   (psa/generate-product-specific-attributes product-specific-attributes)
                   (cmpgn/generate-campaigns projects)
                   (two-d/generate-two-ds two-d-coordinate-systems)
                   (generate-spatial spatial-coverage)))))))

(defn validate-xml
  "Validates the XML against the ECHO10 schema."
  [xml]
  (v/validate-xml (io/resource "schema/echo10/Collection.xsd") xml))

