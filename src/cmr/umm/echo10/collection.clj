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
            [cmr.umm.echo10.related-url :as ru]
            [cmr.umm.echo10.collection.org :as org]
            [cmr.umm.echo10.collection.science-keyword :as sk]
            [cmr.umm.echo10.core]
            [camel-snake-kebab :as csk])
  (:import cmr.umm.collection.UmmCollection))

(defn- xml-elem->Product
  "Returns a UMM Product from a parsed Collection Content XML structure"
  [collection-content]
  (c/map->Product {:short-name (cx/string-at-path collection-content [:ShortName])
                   :long-name (cx/string-at-path collection-content [:LongName])
                   :version-id (cx/string-at-path collection-content [:VersionId])
                   :processing-level-id (cx/string-at-path collection-content [:ProcessingLevelId])
                   :collection-data-type (cx/string-at-path collection-content [:CollectionDataType])}))

(defn xml-elem->DataProviderTimestamps
  "Returns a UMM DataProviderTimestamps from a parsed Collection Content XML structure"
  [collection-content]
  (c/map->DataProviderTimestamps {:insert-time (cx/datetime-at-path collection-content [:InsertTime])
                                  :update-time (cx/datetime-at-path collection-content [:LastUpdate])
                                  :delete-time (cx/datetime-at-path collection-content [:DeleteTime])}))

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
  (let [product (xml-elem->Product xml-struct)
        data-provider-timestamps (xml-elem->DataProviderTimestamps xml-struct)]
    (c/map->UmmCollection
      {:entry-id (str (:short-name product) "_" (:version-id product))
       :entry-title (cx/string-at-path xml-struct [:DataSetId])
       :product product
       :data-provider-timestamps data-provider-timestamps
       :spatial-keywords (seq (cx/strings-at-path xml-struct [:SpatialKeywords :Keyword]))
       :temporal (t/xml-elem->Temporal xml-struct)
       :science-keywords (sk/xml-elem->ScienceKeywords xml-struct)
       :platforms (platform/xml-elem->Platforms xml-struct)
       :product-specific-attributes (psa/xml-elem->ProductSpecificAttributes xml-struct)
       :projects (cmpgn/xml-elem->Campaigns xml-struct)
       :two-d-coordinate-systems (two-d/xml-elem->TwoDCoordinateSystems xml-struct)
       :related-urls (ru/xml-elem->related-urls xml-struct)
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
     (let [{{:keys [short-name long-name version-id processing-level-id collection-data-type]} :product
            dataset-id :entry-title
            {:keys [insert-time update-time delete-time]} :data-provider-timestamps
            :keys [organizations spatial-keywords temporal science-keywords platforms product-specific-attributes
                   projects two-d-coordinate-systems related-urls spatial-coverage]} collection
           emit-fn (if indent? x/indent-str x/emit-str)]
       (emit-fn
         (x/element :Collection {}
                    (x/element :ShortName {} short-name)
                    (x/element :VersionId {} version-id)
                    (x/element :InsertTime {} (str insert-time))
                    (x/element :LastUpdate {} (str update-time))
                    (when delete-time
                      (x/element :DeleteTime {} (str delete-time)))
                    (x/element :LongName {} long-name)
                    (x/element :DataSetId {} dataset-id)
                    (x/element :Description {} "stubbed")
                    (when collection-data-type
                      (x/element :CollectionDataType {} collection-data-type))
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
                    (sk/generate-science-keywords science-keywords)
                    (platform/generate-platforms platforms)
                    (psa/generate-product-specific-attributes product-specific-attributes)
                    (cmpgn/generate-campaigns projects)
                    (two-d/generate-two-ds two-d-coordinate-systems)
                    (ru/generate-access-urls related-urls)
                    (ru/generate-resource-urls related-urls)
                    (generate-spatial spatial-coverage)))))))

(defn validate-xml
  "Validates the XML against the ECHO10 schema."
  [xml]
  (v/validate-xml (io/resource "schema/echo10/Collection.xsd") xml))
