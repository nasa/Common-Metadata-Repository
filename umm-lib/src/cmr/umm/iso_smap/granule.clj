(ns cmr.umm.iso-smap.granule
  "Contains functions for parsing and generating the SMAP ISO dialect."
  (:require
   [clj-time.format :as time-format]
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [cmr.common.xml :as cx]
   [cmr.umm.echo10.echo10-core]
   [cmr.umm.iso-smap.granule.related-url :as ru]
   [cmr.umm.iso-smap.granule.spatial :as spatial]
   [cmr.umm.iso-smap.granule.temporal :as gt]
   [cmr.umm.iso-smap.helper :as helper]
   [cmr.umm.iso-smap.iso-smap-core :as iso-smap-core]
   [cmr.umm.umm-granule :as granule])
  (:import
   (cmr.umm.umm_granule UmmGranule)))

(defn- xml-elem->CollectionRef
  "Returns a UMM ref element from a parsed Granule XML structure"
  [id-elems]
  (let [dataset-id-elem (helper/xml-elem-with-title-tag id-elems "DataSetId")
        entry-title (cx/string-at-path
                      dataset-id-elem
                      [:aggregationInfo :MD_AggregateInformation :aggregateDataSetIdentifier
                       :MD_Identifier :code :CharacterString])
        coll-elems (mapcat
                     #(cx/elements-at-path % [:citation :CI_Citation :identifier :MD_Identifier])
                     id-elems)
        parse-fn (fn [tag]
                   (let [elem (helper/xml-elem-with-path-value
                                coll-elems
                                [:description :CharacterString] tag)]
                     (cx/string-at-path elem [:code :CharacterString])))
        short-name (parse-fn "The ECS Short Name")
        version-id (parse-fn "The ECS Version ID")]
    (granule/map->CollectionRef {:entry-title entry-title
                                 :short-name short-name
                                 :version-id version-id})))

(defn- xml-elem->granule-ur
  "Returns a UMM granule ur from a parsed Granule XML structure"
  [id-elems]
  (let [granule-ur-elem (helper/xml-elem-with-path-value
                          id-elems [:purpose :CharacterString] "GranuleUR")]
    (cx/string-at-path
      granule-ur-elem
      [:citation :CI_Citation :title :CharacterString])))

(defn- xml-elem->DataGranule
  "Returns a UMM data-granule element from a parsed Granule XML structure"
  [xml-struct]
  (let [size (cx/double-at-path
               xml-struct
               [:composedOf :DS_DataSet :has :MI_Metadata :distributionInfo :MD_Distribution
                :distributor :MD_Distributor :distributorTransferOptions :MD_DigitalTransferOptions
                :transferSize :Real])
        producer-gran-id (cx/string-at-path
                           xml-struct
                           [:composedOf :DS_DataSet :has :MI_Metadata :identificationInfo
                            :MD_DataIdentification :citation :CI_Citation :title :FileName])
        production-date-time (cx/datetime-at-path
                               xml-struct
                               [:composedOf :DS_DataSet :has :MI_Metadata :dataQualityInfo
                                :DQ_DataQuality :lineage :LI_Lineage :processStep :LE_ProcessStep
                                :dateTime :DateTime])]
    (when (or producer-gran-id production-date-time)
      (granule/map->DataGranule {:size size
                                 :producer-gran-id producer-gran-id
                                 :production-date-time production-date-time}))))

(defn- xml-elem->access-value
  "Returns a UMM access value from a parsed Granule XML structure"
  [id-elems]
  (let [restriction-flag-elem (helper/xml-elem-with-title-tag id-elems "RestrictionFlag")]
    (cx/double-at-path
      restriction-flag-elem
      [:resourceConstraints :MD_LegalConstraints :otherConstraints :CharacterString])))

(defn xml-elem->DataProviderTimestamps
  "Returns a UMM DataProviderTimestamps from a parsed XML structure"
  [id-elems]
  (let [insert-time-elem (helper/xml-elem-with-title-tag id-elems "InsertTime")
        update-time-elem (helper/xml-elem-with-title-tag id-elems "UpdateTime")
        insert-time (cx/datetime-at-path insert-time-elem [:citation :CI_Citation :date :CI_Date :date :DateTime])
        update-time (cx/datetime-at-path update-time-elem [:citation :CI_Citation :date :CI_Date :date :DateTime])]
    (when (or insert-time update-time)
      (granule/map->DataProviderTimestamps
        {:insert-time insert-time
         :update-time update-time}))))

(defn- get-id-elems
  "Extracts the id elements"
  [xml-struct]
  (cx/elements-at-path
    xml-struct
    [:composedOf :DS_DataSet :has :MI_Metadata
     :identificationInfo :MD_DataIdentification]))

(defn- xml-elem->Granule
  "Returns a UMM Product from a parsed Granule XML structure"
  [xml-struct]
  (let [id-elems (get-id-elems xml-struct)]
    (granule/map->UmmGranule
      {:granule-ur (xml-elem->granule-ur id-elems)
       :data-provider-timestamps (xml-elem->DataProviderTimestamps id-elems)
       :collection-ref (xml-elem->CollectionRef id-elems)
       :data-granule (xml-elem->DataGranule xml-struct)
       :access-value (xml-elem->access-value id-elems)
       :temporal (gt/xml-elem->Temporal xml-struct)
       :orbit-calculated-spatial-domains (spatial/xml-elem->OrbitCalculatedSpatialDomains xml-struct)
       :spatial-coverage (spatial/xml-elem->SpatialCoverage xml-struct)
       :related-urls (ru/xml-elem->related-urls xml-struct)})))

(defn parse-granule
  "Parses SMAP XML into a UMM Granule record."
  [xml]
  (xml-elem->Granule (cx/parse-str xml)))

(defn parse-temporal
  "Parses the XML and extracts the temporal data."
  [xml]
  (gt/xml-elem->Temporal (cx/parse-str xml)))

(defn parse-spatial
  "Parses the XML and extracts the SpatialCoverage data."
  [xml]
  (spatial/xml-elem->SpatialCoverage (cx/parse-str xml)))

(defn parse-access-value
  "Parses the XML and extracts the access value"
  [xml]
  (xml-elem->access-value (get-id-elems (cx/parse-str xml))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generators

(defn- generate-granule-ur-element
  [granule-ur update-time]
  (xml/element :gmd:identificationInfo {}
               (xml/element :gmd:MD_DataIdentification {}
                            (xml/element :gmd:citation {}
                                         (xml/element :gmd:CI_Citation {}
                                                      (helper/iso-string-element :gmd:title granule-ur)
                                                      (helper/iso-date-element "creation" update-time)))
                            (helper/iso-string-element :gmd:abstract "GranuleUR")
                            (helper/iso-string-element :gmd:purpose "GranuleUR")
                            (helper/iso-string-element :gmd:language "eng"))))

(def identification-abstract
  "Returns the abstract. Not sure if we can hard-code it like this."
  (str "The SMAP Level 1C_S0_HiRes product provides multilooked Synthetic Aperture Radar "
       "normalized cross sections in an instrument swath based array with 1 km resolution."))

(defn- generate-identification-info-element
  "Returns the main identification info element that contains most of the SMAP ISO granule fields"
  [producer-gran-id update-time short-name version-id spatial-coverage temporal ocsds]
  (xml/element
    :gmd:identificationInfo {}
    (xml/element
      :gmd:MD_DataIdentification {}
      (xml/element
        :gmd:citation {}
        (xml/element
          :gmd:CI_Citation {}
          (xml/element :gmd:title {}
                       (xml/element :gmx:FileName {} producer-gran-id))
          (helper/iso-date-element "creation" update-time)
          (helper/iso-string-element :gmd:edition version-id)
          (helper/generate-short-name-element short-name)
          (helper/generate-version-id-element version-id)))
      ;; This probably shouldn't be hard-coded
      (helper/iso-string-element :gmd:abstract identification-abstract)
      (helper/iso-string-element :gmd:language "eng")
      (xml/element :gmd:extent {}
                   (xml/element :gmd:EX_Extent {}
                                (spatial/generate-spatial spatial-coverage)
                                (spatial/generate-orbit-calculated-spatial-domains ocsds)
                                (gt/generate-temporal temporal))))))

(defn- generate-restriction-flag-element
  "Returns the smap iso restriction flag element"
  [access-value update-time]
  (xml/element
    :gmd:identificationInfo {}
    (xml/element
      :gmd:MD_DataIdentification {}
      (helper/generate-citation-element "RestrictionFlag" "revision" update-time)
      (helper/iso-string-element :gmd:abstract "RestrictionFlag")
      (helper/iso-string-element :gmd:purpose "RestrictionFlag")
      (xml/element :gmd:resourceConstraints {}
                   (xml/element :gmd:MD_LegalConstraints {}
                                (helper/iso-string-element :gmd:otherConstraints access-value)))
      (helper/iso-string-element :gmd:language "eng"))))

(defn- generate-distribution-info-element
  [related-urls]
  (xml/element
    :gmd:distributionInfo {}
    (xml/element
      :gmd:MD_Distribution {}
      (xml/element :gmd:distributor {}
                   (xml/element :gmd:MD_Distributor {}
                                (xml/element :gmd:distributorContact {})
                                (ru/generate-related-urls related-urls))))))

(def processing-step-description
  "Returns the processing step description. Not sure if we can hard-code it like this."
  (str "Converts instrument telemetry into a data set that contains horizonally polarized, "
       "vertically polarized and cross polarized normalized radar cross sections, "
       "each of which are multilooked onto a 1 km swath oriented grid."))

(defn- generate-data-quality-info-element
  [production-date-time]
  (when production-date-time
    (xml/element
      :gmd:dataQualityInfo {}
      (xml/element
        :gmd:DQ_DataQuality {}
        (xml/element :gmd:scope {})
        (xml/element
          :gmd:lineage {}
          (xml/element
            :gmd:LI_Lineage {}
            (xml/element
              :gmd:processStep {}
              (xml/element
                :gmi:LE_ProcessStep {}
                (helper/iso-string-element :gmd:description processing-step-description)
                (xml/element :gmd:dateTime {}
                             (xml/element :gco:DateTime {} (str production-date-time)))))))))))

(extend-protocol iso-smap-core/UmmToIsoSmapXml
  UmmGranule
  (umm->iso-smap-xml
    ([granule]
     (let [{{:keys [entry-title short-name version-id]} :collection-ref
            {:keys [insert-time update-time]} :data-provider-timestamps
            :keys [granule-ur data-granule access-value temporal orbit-calculated-spatial-domains
                   related-urls spatial-coverage]} granule
           {:keys [producer-gran-id production-date-time]} data-granule]
       (xml/emit-str
         (xml/element
           :gmd:DS_Series helper/iso-header-attributes
           (xml/element
             :gmd:composedOf {}
             (xml/element
               :gmd:DS_DataSet {}
               (xml/element
                 :gmd:has {}
                 (xml/element
                   :gmi:MI_Metadata {}
                   (xml/element :gmd:fileIdentifier {}
                              (xml/element :gmx:FileName {} producer-gran-id))
                   (helper/iso-string-element :gmd:language "eng")
                   helper/iso-charset-element
                   (helper/iso-hierarchy-level-element "dataset")
                   (xml/element :gmd:contact {})
                   (xml/element :gmd:dateStamp {}
                                (xml/element :gco:Date {} (time-format/unparse (time-format/formatters :date) update-time)))
                   (generate-granule-ur-element granule-ur update-time)
                   (generate-identification-info-element
                     producer-gran-id update-time short-name version-id
                     spatial-coverage temporal orbit-calculated-spatial-domains)
                   (helper/generate-dataset-id-element entry-title update-time)
                   (helper/generate-datetime-element "InsertTime" "creation" insert-time)
                   (helper/generate-datetime-element "UpdateTime" "revision" update-time)
                   (generate-restriction-flag-element access-value update-time)
                   (generate-distribution-info-element related-urls)
                   (generate-data-quality-info-element production-date-time)))))
           (xml/element :gmd:seriesMetadata {:gco:nilReason "inapplicable"})))))))

(def schema-location "schema/iso_smap/schema.xsd")

(defn validate-xml
  "Validates the XML against the SMAP ISO schema."
  [xml]
  (cx/validate-xml (io/resource schema-location) xml))
