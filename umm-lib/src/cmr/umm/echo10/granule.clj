(ns cmr.umm.echo10.granule
  "Contains functions for parsing and generating the ECHO10 dialect."
  (:require [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-granule :as g]
            [cmr.umm.umm-collection :as umm-c]
            [cmr.umm.echo10.echo10-collection :as c]
            [cmr.umm.echo10.spatial :as s]
            [cmr.umm.echo10.granule.temporal :as gt]
            [cmr.umm.echo10.granule.platform-ref :as p-ref]
            [cmr.umm.echo10.related-url :as ru]
            [cmr.umm.echo10.granule.product-specific-attribute-ref :as psa]
            [cmr.umm.echo10.granule.orbit-calculated-spatial-domain :as ocsd]
            [cmr.umm.echo10.granule.two-d-coordinate-system :as two-d]
            [cmr.umm.echo10.granule.measured-parameter :as mp]
            [cmr.common.xml :as v]
            [cmr.common.util :as util]
            [cmr.umm.echo10.echo10-core])
  (:import cmr.umm.umm_granule.UmmGranule))

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

(defn- get-size-in-mb
  "Get size in megabytes based on the size-unit."
  [size size-unit]
  (condp = size-unit
    "KB" (/ size 1024)
    "MB" size
    "GB" (* size 1024)
    "TB" (* size 1024 1024)
    "PB" (* size 1024 1024 1024)
    size))

(defn generate-data-granule
  "Generates the DataGranule element of an ECHO10 XML from a UMM Granule data-granule entry."
  [data-granule]
  (when data-granule
    (let [{:keys [producer-gran-id
                  day-night
                  production-date-time
                  size
                  size-unit
                  size-in-bytes
                  checksum]} data-granule
          day-night (or day-night "UNSPECIFIED")]
      (x/element :DataGranule {}
                 (when size-in-bytes
                   (x/element :DataGranuleSizeInBytes {} size-in-bytes))
                 (when size
                   (x/element :SizeMBDataGranule {} (get-size-in-mb size size-unit)))
                 (when checksum
                   (x/element :Checksum {}
                     (x/element :Value {} (:value checksum))
                     (x/element :Algorithm {} (:algorithm checksum))))
                 (when producer-gran-id
                   (x/element :ProducerGranuleId {} producer-gran-id))
                 (x/element :DayNightFlag {} day-night)
                 (x/element :ProductionDateTime {} (str production-date-time))))))

(defn generate-pge-version-class
  "Generates the PGEVersionClass element of an ECHO10 XML from a UMM Granule pge-version-class entry."
  [pge-version-class]
  (when pge-version-class
    (let [{:keys [pge-name pge-version]} pge-version-class]
      (x/element :PGEVersionClass {}
                 (when pge-name
                   (x/element :PGEName {} pge-name))
                 (when pge-version
                   (x/element :PGEVersion {} pge-version))))))

(defn- xml-elem->CollectionRef
  "Returns a UMM ref element from a parsed Granule XML structure"
  [granule-content-node]
  (let [entry-title (cx/string-at-path granule-content-node [:Collection :DataSetId])
        short-name (cx/string-at-path granule-content-node [:Collection :ShortName])
        version-id (cx/string-at-path granule-content-node [:Collection :VersionId])
        entry-id (cx/string-at-path granule-content-node [:Collection :EntryId])]
    (g/map->CollectionRef {:entry-title entry-title
                           :short-name short-name
                           :version-id version-id
                           :entry-id entry-id})))

(defn xml-elem->Checksum
  "Returns a UMM Checksum from a parsed DataGranule Content XML structure"
  [data-granule-element]
  (when-let [checksum-element (cx/element-at-path data-granule-element [:Checksum])]
    (g/map->Checksum
      {:value (cx/string-at-path checksum-element [:Value])
       :algorithm (cx/string-at-path checksum-element [:Algorithm])})))

(defn- xml-elem->DataGranule
  "Returns a UMM data-granule element from a parsed Granule XML structure"
  [granule-content-node]
  (let [data-gran-node (cx/element-at-path granule-content-node [:DataGranule])
        size-in-bytes (cx/integer-at-path data-gran-node [:DataGranuleSizeInBytes])
        size (cx/double-at-path data-gran-node [:SizeMBDataGranule])
        checksum (xml-elem->Checksum data-gran-node)
        producer-gran-id (cx/string-at-path data-gran-node [:ProducerGranuleId])
        day-night (cx/string-at-path data-gran-node [:DayNightFlag])
        production-date-time (cx/datetime-at-path data-gran-node [:ProductionDateTime])]
    (when (or size-in-bytes size checksum producer-gran-id day-night production-date-time)
      (g/map->DataGranule {:size-in-bytes size-in-bytes
                           :size size
                           :size-unit "MB"
                           :checksum (when checksum
                                       (g/map->Checksum {:value (:value checksum)
                                                         :algorithm (:algorithm checksum)}))
                           :producer-gran-id producer-gran-id
                           :day-night (if day-night day-night "UNSPECIFIED")
                           :production-date-time production-date-time}))))

(defn- xml-elem->PGEVersionClass
  "Returns a UMM pge-version-class element from a parsed Granule XML structure"
  [granule-element]
  (when-let [pge-version-class-element (cx/element-at-path granule-element [:PGEVersionClass])]
    (g/map->PGEVersionClass {:pge-name (cx/string-at-path pge-version-class-element [:PGEName])
                             :pge-version (cx/string-at-path pge-version-class-element [:PGEVersion])})))

(defn- xml-elem->SpatialCoverage
  [granule-content-node]
  (let [geom-elem (cx/element-at-path granule-content-node [:Spatial :HorizontalSpatialDomain :Geometry])
        orbit-elem (cx/element-at-path granule-content-node [:Spatial :HorizontalSpatialDomain :Orbit])]
    (when (or geom-elem orbit-elem)
      (g/map->SpatialCoverage {:geometries (when geom-elem (s/geometry-element->geometries geom-elem))
                               :orbit (when orbit-elem (s/xml-elem->Orbit orbit-elem))}))))

(defn generate-spatial
  [spatial-coverage]
  (when spatial-coverage
    (let [{:keys [geometries orbit]} spatial-coverage]
      (x/element :Spatial {}
                 (x/element :HorizontalSpatialDomain {}
                            (if geometries
                              (s/generate-geometry-xml geometries)
                              (s/generate-orbit-xml orbit)))))))

(defn xml-elem->DataProviderTimestamps
  "Returns a UMM DataProviderTimestamps from a parsed Collection Content XML structure"
  [granule-content]
  (g/map->DataProviderTimestamps {:insert-time (cx/datetime-at-path granule-content [:InsertTime])
                                  :update-time (cx/datetime-at-path granule-content [:LastUpdate])
                                  :delete-time (cx/datetime-at-path granule-content [:DeleteTime])}))

(defn- xml-elem->Granule
  "Returns a UMM Product from a parsed Granule XML structure"
  [xml-struct]
  (let [coll-ref (xml-elem->CollectionRef xml-struct)
        data-provider-timestamps (xml-elem->DataProviderTimestamps xml-struct)]
    (g/map->UmmGranule {:granule-ur (cx/string-at-path xml-struct [:GranuleUR])
                        :data-provider-timestamps data-provider-timestamps
                        :collection-ref coll-ref
                        :data-granule (xml-elem->DataGranule xml-struct)
                        :pge-version-class (xml-elem->PGEVersionClass xml-struct)
                        :access-value (cx/double-at-path xml-struct [:RestrictionFlag])
                        :temporal (gt/xml-elem->Temporal xml-struct)
                        :orbit-calculated-spatial-domains (ocsd/xml-elem->orbit-calculated-spatial-domains xml-struct)
                        :platform-refs (p-ref/xml-elem->PlatformRefs xml-struct)
                        :project-refs (xml-elem->project-refs xml-struct)
                        :cloud-cover (cx/double-at-path xml-struct [:CloudCover])
                        :two-d-coordinate-system (two-d/xml-elem->TwoDCoordinateSystem xml-struct)
                        :related-urls (ru/xml-elem->related-urls xml-struct)
                        :spatial-coverage (xml-elem->SpatialCoverage xml-struct)
                        :measured-parameters (mp/xml-elem->MeasuredParameters xml-struct)
                        :product-specific-attributes (psa/xml-elem->ProductSpecificAttributeRefs xml-struct)})))

(defn parse-granule
  "Parses ECHO10 XML into a UMM Granule record."
  [xml]
  (xml-elem->Granule (x/parse-str xml)))

(defn parse-temporal
  "Parses the XML and extracts the temporal data."
  [xml]
  ;; The extraction here provides a small benefit of about 100 microseconds per granule. This is
  ;; significant when parsing out 2000 granules worth of temporal. That's equivalent ot 0.2 seconds
  ;; The temporal parsing could likely be sped up even more if we wrote more specific extraction code.
  ;; We could parse out the exact date strings, sort them and return the first and last.
  (when-let [single-element (util/extract-between-strings xml "<Temporal>" "</Temporal>")]
    (let [smaller-xml (str "<Granule>" single-element "</Granule>")]
      (gt/xml-elem->Temporal (x/parse-str smaller-xml)))))

(defn parse-access-value
  "Parses the XML and extracts the access value"
  [^String xml]
  (when-let [value (util/extract-between-strings xml "<RestrictionFlag>" "</RestrictionFlag>" false)]
    (Double/parseDouble value)))

(extend-protocol cmr.umm.echo10.echo10-core/UmmToEcho10Xml
  UmmGranule
  (umm->echo10-xml
    ([granule]
     (let [{{:keys [entry-title short-name version-id entry-id]} :collection-ref
            {:keys [insert-time update-time delete-time]} :data-provider-timestamps
            :keys [granule-ur data-granule access-value temporal orbit-calculated-spatial-domains
                   platform-refs project-refs cloud-cover related-urls product-specific-attributes
                   spatial-coverage orbit two-d-coordinate-system measured-parameters pge-version-class]} granule]
       (x/emit-str
         (x/element :Granule {}
                    (x/element :GranuleUR {} granule-ur)
                    (x/element :InsertTime {} (str insert-time))
                    (x/element :LastUpdate {} (str update-time))
                    (when delete-time
                      (x/element :DeleteTime {} (str delete-time)))
                    (cond (some? entry-title)
                          (x/element :Collection {}
                                     (x/element :DataSetId {} entry-title))
                          (some? entry-id)
                          (x/element :Collection {}
                                     (x/element :EntryId {} entry-id))
                          :else (x/element :Collection {}
                                           (x/element :ShortName {} short-name)
                                           (x/element :VersionId {} version-id)))
                    (when access-value
                      (x/element :RestrictionFlag {} access-value))
                    (generate-data-granule data-granule)
                    (when pge-version-class
                      (generate-pge-version-class pge-version-class))
                    (gt/generate-temporal temporal)
                    (generate-spatial spatial-coverage)
                    (ocsd/generate-orbit-calculated-spatial-domains orbit-calculated-spatial-domains)
                    (mp/generate-measured-parameters measured-parameters)
                    (p-ref/generate-platform-refs platform-refs)
                    (generate-project-refs project-refs)
                    (psa/generate-product-specific-attribute-refs product-specific-attributes)
                    (two-d/generate-two-d-coordinate-system two-d-coordinate-system)
                    (ru/generate-access-urls related-urls)
                    (ru/generate-resource-urls related-urls)
                    (when cloud-cover
                      (x/element :CloudCover {} cloud-cover))
                    (ru/generate-browse-urls related-urls)))))))

(defn validate-xml
  "Validates the XML against the Granule ECHO10 schema."
  [xml]
  (v/validate-xml (io/resource "schema/echo10/Granule.xsd") xml))
