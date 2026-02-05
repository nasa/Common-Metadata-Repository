(ns cmr.umm.echo10.echo10-collection
  "Contains functions for parsing and generating the ECHO10 dialect."
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [cmr.common.xml :as cx]
   [cmr.umm.umm-collection :as coll]
   [cmr.common.util :as util]
   [cmr.umm.echo10.collection.temporal :as temporal]
   [cmr.umm.echo10.collection.personnel :as pe]
   [cmr.umm.echo10.collection.product-specific-attribute :as psa]
   [cmr.umm.echo10.collection.progress :as progress]
   [cmr.umm.echo10.collection.platform :as platform]
   [cmr.umm.echo10.collection.campaign :as cmpgn]
   [cmr.umm.echo10.collection.collection-association :as ca]
   [cmr.umm.echo10.collection.two-d-coordinate-system :as two-d]
   [cmr.umm.echo10.related-url :as ru]
   [cmr.umm.echo10.collection.org :as org]
   [cmr.umm.echo10.collection.science-keyword :as sk]
   [cmr.umm.echo10.spatial :as spatial]
   [cmr.umm.echo10.echo10-core])
  (:import cmr.umm.umm_collection.UmmCollection))

;; Parsing XML Structures

(defn- xml-elem->Product
  "Returns a UMM Product from a parsed Collection Content XML structure"
  [collection-content]
  (coll/map->Product {:short-name (cx/string-at-path collection-content [:ShortName])
                      :long-name (cx/string-at-path collection-content [:LongName])
                      :version-id (cx/string-at-path collection-content [:VersionId])
                      :version-description (cx/string-at-path collection-content [:VersionDescription])
                      :processing-level-id (cx/string-at-path collection-content [:ProcessingLevelId])
                      :collection-data-type (cx/string-at-path collection-content [:CollectionDataType])}))

(defn xml-elem->DataProviderTimestamps
  "Returns a UMM DataProviderTimestamps from a parsed Collection Content XML structure"
  [collection-content]
  (coll/map->DataProviderTimestamps {:insert-time (cx/datetime-at-path collection-content [:InsertTime])
                                     :update-time (cx/datetime-at-path collection-content [:LastUpdate])
                                     :delete-time (cx/datetime-at-path collection-content [:DeleteTime])
                                     :revision-date-time (cx/datetime-at-path collection-content [:RevisionDate])}))

(defn- xml-elem->OrbitParameters
  "Returns a UMM OrbitParameters record from a parsed OrbitParameters XML structure"
  [orbit-params]
  (when orbit-params
    (coll/map->OrbitParameters {:swath-width (cx/double-at-path orbit-params [:SwathWidth])
                                :period (cx/double-at-path orbit-params [:Period])
                                :inclination-angle (cx/double-at-path orbit-params [:InclinationAngle])
                                :number-of-orbits (cx/double-at-path orbit-params [:NumberOfOrbits])
                                :start-circular-latitude (cx/double-at-path orbit-params
                                                                            [:StartCircularLatitude])})))

(defn- xml-elem->SpatialCoverage
  "Returns a UMM SpatialCoverage from a parsed Collection XML structure"
  [xml-struct]
  (when-let [spatial-elem (cx/element-at-path xml-struct [:Spatial])]
    (let [gsr (some-> (cx/string-at-path spatial-elem [:GranuleSpatialRepresentation])
                      csk/->kebab-case-keyword)
          orbit-params (cx/element-at-path spatial-elem [:OrbitParameters])]
      (if-let [geom-elem (cx/element-at-path spatial-elem [:HorizontalSpatialDomain :Geometry])]
        (coll/map->SpatialCoverage
          {:granule-spatial-representation gsr
           :orbit-parameters (xml-elem->OrbitParameters orbit-params)
           :spatial-representation (csk/->kebab-case-keyword (cx/string-at-path geom-elem [:CoordinateSystem]))
           :geometries (spatial/geometry-element->geometries geom-elem)})
        (coll/map->SpatialCoverage
          {:granule-spatial-representation gsr
           :orbit-parameters (xml-elem->OrbitParameters orbit-params)})))))

(defn generate-orbit-parameters
  "Generates the OrbitParameters element from orbit-params"
  [orbit-params]
  (when orbit-params
    (let [{:keys [swath-width period inclination-angle number-of-orbits start-circular-latitude]}
          orbit-params]
      (xml/element :OrbitParameters {}
                   (xml/element :SwathWidth {} swath-width)
                   (xml/element :Period {} period)
                   (xml/element :InclinationAngle {} inclination-angle)
                   (xml/element :NumberOfOrbits {} number-of-orbits)
                   (when start-circular-latitude
                     (xml/element :StartCircularLatitude {} start-circular-latitude))))))

(defn generate-spatial
  "Generates the Spatial element from spatial coverage"
  [spatial-coverage]
  (when spatial-coverage
    (let [{:keys [granule-spatial-representation
                  spatial-representation
                  geometries
                  orbit-parameters]} spatial-coverage
          gsr (some-> granule-spatial-representation csk/->SCREAMING_SNAKE_CASE_STRING)
          sr (some-> spatial-representation csk/->SCREAMING_SNAKE_CASE_STRING)]
      (if sr
        (xml/element :Spatial {}
                     (xml/element :HorizontalSpatialDomain {}
                                  (xml/element :Geometry {}
                                         (xml/element :CoordinateSystem {} sr)
                                         (map spatial/shape-to-xml geometries)))
                   (generate-orbit-parameters orbit-parameters)
                   (xml/element :GranuleSpatialRepresentation {} gsr))
        (xml/element :Spatial {}
                     (generate-orbit-parameters orbit-parameters)
                     (xml/element :GranuleSpatialRepresentation {} gsr))))))

(defn- xml-elem->Collection
  "Returns a UMM Product from a parsed Collection XML structure"
  [xml-struct]
  (let [product (xml-elem->Product xml-struct)
        data-provider-timestamps (xml-elem->DataProviderTimestamps xml-struct)]
    (coll/map->UmmCollection
      {:entry-title (cx/string-at-path xml-struct [:DataSetId])
       :summary (cx/string-at-path xml-struct [:Description])
       :purpose (cx/string-at-path xml-struct [:SuggestedUsage])
       :product product
       :access-value (cx/double-at-path xml-struct [:RestrictionFlag])
       :data-provider-timestamps data-provider-timestamps
       :spatial-keywords (seq (cx/strings-at-path xml-struct [:SpatialKeywords :Keyword]))
       :temporal-keywords (seq (cx/strings-at-path xml-struct [:TemporalKeywords :Keyword]))
       :temporal (temporal/xml-elem->Temporal xml-struct)
       :science-keywords (sk/xml-elem->ScienceKeywords xml-struct)
       :platforms (platform/xml-elem->Platforms xml-struct)
       :product-specific-attributes (psa/xml-elem->ProductSpecificAttributes xml-struct)
       :collection-associations (ca/xml-elem->CollectionAssociations xml-struct)
       :projects (cmpgn/xml-elem->Campaigns xml-struct)
       :two-d-coordinate-systems (two-d/xml-elem->TwoDCoordinateSystems xml-struct)
       :related-urls (ru/xml-elem->related-urls xml-struct)
       :spatial-coverage (xml-elem->SpatialCoverage xml-struct)
       :organizations (org/xml-elem->Organizations xml-struct)
       :personnel (pe/xml-elem->personnel xml-struct)
       :associated-difs (seq (cx/strings-at-path xml-struct [:AssociatedDIFs :DIF :EntryId]))
       :collection-progress (progress/parse xml-struct)
       :collection-citations (seq (cx/strings-at-path xml-struct [:CitationForExternalPublication]))})))

(defn parse-collection
  "Parses ECHO10 XML into a UMM Collection record."
  [xml]
  (xml-elem->Collection (cx/parse-str xml)))

(defn parse-temporal
  "Parses the XML and extracts the temporal data."
  [xml]
  (when-let [single-element (util/extract-between-strings xml "<Temporal>" "</Temporal>")]
    (let [smaller-xml (str "<Collection>" single-element "</Collection>")]
      (temporal/xml-elem->Temporal (cx/parse-str smaller-xml)))))

(defn parse-access-value
  "Parses the XML and extracts the access value"
  [xml]
  (when-let [value (util/extract-between-strings xml "<RestrictionFlag>" "</RestrictionFlag>" false)]
    (Double/parseDouble value)))

;; Generating XML

(extend-protocol cmr.umm.echo10.echo10-core/UmmToEcho10Xml
  UmmCollection
  (umm->echo10-xml
    ([collection]
     (let [{{:keys [short-name long-name version-id version-description
                    processing-level-id collection-data-type]} :product
            dataset-id :entry-title
            restriction-flag :access-value
            {:keys [insert-time update-time revision-date-time delete-time]} :data-provider-timestamps
            :keys [organizations spatial-keywords temporal-keywords temporal science-keywords
                   platforms product-specific-attributes collection-associations projects
                   two-d-coordinate-systems related-urls spatial-coverage summary purpose associated-difs
                   personnel collection-citations]} collection]
       (xml/emit-str
         (xml/element :Collection {}
                      (xml/element :ShortName {} short-name)
                      (xml/element :VersionId {} version-id)
                      (xml/element :InsertTime {} (str insert-time))
                      (xml/element :LastUpdate {} (str update-time))
                      (when delete-time
                        (xml/element :DeleteTime {} (str delete-time)))
                      (xml/element :LongName {} long-name)
                      (xml/element :DataSetId {} dataset-id)
                      (xml/element :Description {} summary)
                      (when collection-data-type
                        (xml/element :CollectionDataType {} collection-data-type))
                      (when revision-date-time
                        (xml/element :RevisionDate {} (str revision-date-time)))
                      ;; archive center to follow processing center
                      (when purpose
                        (xml/element :SuggestedUsage {} purpose))
                      (org/generate-processing-center organizations)
                      (when processing-level-id
                        (xml/element :ProcessingLevelId {} processing-level-id))
                      (org/generate-archive-center organizations)
                      (when version-description
                        (xml/element :VersionDescription {} version-description))
                      (when-let [citation (first collection-citations)]
                        (xml/element :CitationForExternalPublication {} citation))
                      (progress/generate collection)
                      (when restriction-flag
                        (xml/element :RestrictionFlag {} restriction-flag))
                      (when spatial-keywords
                        (xml/element :SpatialKeywords {}
                                   (for [spatial-keyword spatial-keywords]
                                     (xml/element :Keyword {} spatial-keyword))))
                      (when temporal-keywords
                        (xml/element :TemporalKeywords {}
                                   (for [temporal-keyword temporal-keywords]
                                     (xml/element :Keyword {} temporal-keyword))))
                      (temporal/generate-temporal temporal)
                      (pe/generate-personnel personnel)
                      (sk/generate-science-keywords science-keywords)
                      (platform/generate-platforms platforms)
                      (psa/generate-product-specific-attributes product-specific-attributes)
                      (ca/generate-collection-associations collection-associations)
                      (cmpgn/generate-campaigns projects)
                      (two-d/generate-two-ds two-d-coordinate-systems)
                      (ru/generate-access-urls related-urls)
                      (ru/generate-resource-urls related-urls)
                      (when associated-difs
                        (xml/element :AssociatedDIFs {}
                                   (for [associated-dif associated-difs]
                                     (xml/element :DIF {}
                                                (xml/element :EntryId {} associated-dif)))))
                      (generate-spatial spatial-coverage)
                      (ru/generate-browse-urls related-urls)))))))

(defn validate-xml
  "Validates the XML against the ECHO10 schema."
  [xml]
  (cx/validate-xml (io/resource "schema/echo10/Collection.xsd") xml))
