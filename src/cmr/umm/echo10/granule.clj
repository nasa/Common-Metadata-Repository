(ns cmr.umm.echo10.granule
  "Contains functions for parsing and generating the ECHO10 dialect."
  (:require [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [cmr.common.xml :as cx]
            [cmr.umm.granule :as g]
            [cmr.umm.echo10.spatial :as s]
            [cmr.umm.echo10.granule.temporal :as gt]
            [cmr.umm.echo10.granule.platform-ref :as p-ref]
            [cmr.umm.echo10.related-url :as ru]
            [cmr.umm.echo10.granule.product-specific-attribute-ref :as psa]
            [cmr.umm.echo10.granule.orbit-calculated-spatial-domain :as ocsd]
            [cmr.umm.xml-schema-validator :as v]
            [cmr.umm.echo10.core])
  (:import cmr.umm.granule.UmmGranule))

(defn xml-elem->project-refs
  "Parses an ECHO10 Campaigns element of a Granule XML document and returns the short names."
  [granule-elem]
  (seq (cx/strings-at-path
         granule-elem
         [:Campaigns :Campaign :ShortName])))

(defn generate-project-refs
  "Generates the Campaigns element of an ECHO10 XML from a UMM Granule project-refs entry."
  [prefs]
  (when (not (empty? prefs))
    (x/element :Campaigns {}
               (for [pref prefs]
                 (x/element :Campaign {}
                            (x/element :ShortName {} pref))))))

(defn generate-data-granule
  "Generates the DataGranule element of an ECHO10 XML from a UMM Granule data-granule entry."
  [data-granule]
  (when data-granule
    (let [{:keys [producer-gran-id
                  day-night
                  production-date-time
                  size]} data-granule]
      (x/element :DataGranule {}
                 (when size
                   (x/element :SizeMBDataGranule {} size))
                 (when producer-gran-id
                   (x/element :ProducerGranuleId {} producer-gran-id))
                 (x/element :DayNightFlag {} day-night)
                 (x/element :ProductionDateTime {} (str production-date-time))))))

(defn- xml-elem->CollectionRef
  "Returns a UMM ref element from a parsed Granule XML structure"
  [granule-content-node]
  (if-let [entry-title (cx/string-at-path granule-content-node [:Collection :DataSetId])]
    (g/collection-ref entry-title)
    (let [short-name (cx/string-at-path granule-content-node [:Collection :ShortName])
          version-id (cx/string-at-path granule-content-node [:Collection :VersionId])]
      (g/collection-ref short-name version-id))))

(defn- xml-elem->DataGranule
  "Returns a UMM data-granule element from a parsed Granule XML structure"
  [granule-content-node]
  (let [data-gran-node (cx/element-at-path granule-content-node [:DataGranule])
        size (cx/double-at-path data-gran-node [:SizeMBDataGranule])
        producer-gran-id (cx/string-at-path data-gran-node [:ProducerGranuleId])
        day-night (cx/string-at-path data-gran-node [:DayNightFlag])
        production-date-time (cx/datetime-at-path data-gran-node [:ProductionDateTime])]
    (when (or size producer-gran-id day-night production-date-time)
      (g/map->DataGranule {:size size
                           :producer-gran-id producer-gran-id
                           :day-night day-night
                           :production-date-time production-date-time}))))

(defn- xml-elem->SpatialCoverage
  [granule-content-node]
  (when-let [geom-elem (cx/element-at-path granule-content-node [:Spatial :HorizontalSpatialDomain :Geometry])]
    (g/->SpatialCoverage (s/geometry-element->geometries geom-elem))))

(defn generate-spatial
  [spatial-coverage]
  (when spatial-coverage
    (x/element :Spatial {}
               (x/element :HorizontalSpatialDomain {}
                          (s/generate-geometry-xml (:geometries spatial-coverage))))))

(defn- xml-elem->Granule
  "Returns a UMM Product from a parsed Granule XML structure"
  [xml-struct]
  (let [coll-ref (xml-elem->CollectionRef xml-struct)]
    (g/map->UmmGranule {:granule-ur (cx/string-at-path xml-struct [:GranuleUR])
                        :collection-ref coll-ref
                        :data-granule (xml-elem->DataGranule xml-struct)
                        :temporal (gt/xml-elem->Temporal xml-struct)
                        :orbit-calculated-spatial-domains (ocsd/xml-elem->orbit-calculated-spatial-domains xml-struct)
                        :platform-refs (p-ref/xml-elem->PlatformRefs xml-struct)
                        :project-refs (xml-elem->project-refs xml-struct)
                        :cloud-cover (cx/double-at-path xml-struct [:CloudCover])
                        :related-urls (ru/xml-elem->related-urls xml-struct)
                        :spatial-coverage (xml-elem->SpatialCoverage xml-struct)
                        :product-specific-attributes (psa/xml-elem->ProductSpecificAttributeRefs xml-struct)})))

(defn parse-granule
  "Parses ECHO10 XML into a UMM Granule record."
  [xml]
  (xml-elem->Granule (x/parse-str xml)))

(extend-protocol cmr.umm.echo10.core/UmmToEcho10Xml
  UmmGranule
  (umm->echo10-xml
    ([granule]
     (cmr.umm.echo10.core/umm->echo10-xml granule false))
    ([granule indent?]
     (let [{{:keys [entry-title short-name version-id]} :collection-ref
            granule-ur :granule-ur
            data-granule :data-granule
            temporal :temporal
            ocsds :orbit-calculated-spatial-domains
            platform-refs :platform-refs
            prefs :project-refs
            cloud-cover :cloud-cover
            related-urls :related-urls
            psas :product-specific-attributes
            spatial :spatial-coverage} granule
           emit-fn (if indent? x/indent-str x/emit-str)]
       (emit-fn
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
                    (generate-data-granule data-granule)
                    (gt/generate-temporal temporal)
                    (generate-spatial spatial)
                    (ocsd/generate-orbit-calculated-spatial-domains ocsds)
                    (p-ref/generate-platform-refs platform-refs)
                    (generate-project-refs prefs)
                    (psa/generate-product-specific-attribute-refs psas)
                    (ru/generate-access-urls related-urls)
                    (ru/generate-resource-urls related-urls)
                    (x/element :Orderable {} "true")
                    (when cloud-cover
                      (x/element :CloudCover {} cloud-cover))))))))

(defn validate-xml
  "Validates the XML against the Granule ECHO10 schema."
  [xml]
  (v/validate-xml (io/resource "schema/echo10/Granule.xsd") xml))


