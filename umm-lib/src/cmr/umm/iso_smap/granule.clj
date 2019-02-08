(ns cmr.umm.iso-smap.granule
  "Contains functions for parsing and generating the SMAP ISO dialect."
  (:require
   [clj-time.format :as f]
   [clojure.data.xml :as x]
   [clojure.java.io :as io]
   [cmr.common.xml :as cx]
   [cmr.common.xml :as v]
   [cmr.umm.echo10.echo10-core]
   [cmr.umm.iso-smap.granule.related-url :as ru]
   [cmr.umm.iso-smap.granule.spatial :as s]
   [cmr.umm.iso-smap.granule.temporal :as gt]
   [cmr.umm.iso-smap.helper :as h]
   [cmr.umm.iso-smap.iso-smap-collection :as c]
   [cmr.umm.umm-granule :as g])
  (:import
   (cmr.umm.umm_granule UmmGranule)))

(defn- xml-elem->CollectionRef
  "Returns a UMM ref element from a parsed Granule XML structure"
  [id-elems]
  (let [dataset-id-elem (h/xml-elem-with-title-tag id-elems "DataSetId")
        entry-title (cx/string-at-path
                      dataset-id-elem
                      [:aggregationInfo :MD_AggregateInformation :aggregateDataSetIdentifier
                       :MD_Identifier :code :CharacterString])
        coll-elems (mapcat
                     #(cx/elements-at-path % [:citation :CI_Citation :identifier :MD_Identifier])
                     id-elems)
        parse-fn (fn [tag]
                   (let [elem (h/xml-elem-with-path-value
                                coll-elems
                                [:description :CharacterString] tag)]
                     (cx/string-at-path elem [:code :CharacterString])))
        short-name (parse-fn "The ECS Short Name")
        version-id (parse-fn "The ECS Version ID")]
    (g/map->CollectionRef {:entry-title entry-title
                           :short-name short-name
                           :version-id version-id})))

(defn- xml-elem->granule-ur
  "Returns a UMM granule ur from a parsed Granule XML structure"
  [id-elems]
  (let [granule-ur-elem (h/xml-elem-with-path-value
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
      (g/map->DataGranule {:size size
                           :producer-gran-id producer-gran-id
                           :production-date-time production-date-time}))))

(defn- xml-elem->access-value
  "Returns a UMM access value from a parsed Granule XML structure"
  [id-elems]
  (let [restriction-flag-elem (h/xml-elem-with-title-tag id-elems "RestrictionFlag")]
    (cx/double-at-path
      restriction-flag-elem
      [:resourceConstraints :MD_LegalConstraints :otherConstraints :CharacterString])))

(defn xml-elem->DataProviderTimestamps
  "Returns a UMM DataProviderTimestamps from a parsed XML structure"
  [id-elems]
  (let [insert-time-elem (h/xml-elem-with-title-tag id-elems "InsertTime")
        update-time-elem (h/xml-elem-with-title-tag id-elems "UpdateTime")
        insert-time (cx/datetime-at-path insert-time-elem [:citation :CI_Citation :date :CI_Date :date :DateTime])
        update-time (cx/datetime-at-path update-time-elem [:citation :CI_Citation :date :CI_Date :date :DateTime])]
    (when (or insert-time update-time)
      (g/map->DataProviderTimestamps
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
    (g/map->UmmGranule
      {:granule-ur (xml-elem->granule-ur id-elems)
       :data-provider-timestamps (xml-elem->DataProviderTimestamps id-elems)
       :collection-ref (xml-elem->CollectionRef id-elems)
       :data-granule (xml-elem->DataGranule xml-struct)
       :access-value (xml-elem->access-value id-elems)
       :temporal (gt/xml-elem->Temporal xml-struct)
       :orbit-calculated-spatial-domains (s/xml-elem->OrbitCalculatedSpatialDomains xml-struct)
       :spatial-coverage (s/xml-elem->SpatialCoverage xml-struct)
       :related-urls (ru/xml-elem->related-urls xml-struct)})))

(defn parse-granule
  "Parses SMAP XML into a UMM Granule record."
  [xml]
  (xml-elem->Granule (x/parse-str xml)))

(defn parse-temporal
  "Parses the XML and extracts the temporal data."
  [xml]
  (gt/xml-elem->Temporal (x/parse-str xml)))

(defn parse-spatial
  "Parses the XML and extracts the SpatialCoverage data."
  [xml]
  (s/xml-elem->SpatialCoverage (x/parse-str xml)))

(defn parse-access-value
  "Parses the XML and extracts the access value"
  [xml]
  (xml-elem->access-value (get-id-elems (x/parse-str xml))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generators

(defn- generate-granule-ur-element
  [granule-ur update-time]
  (x/element :gmd:identificationInfo {}
             (x/element :gmd:MD_DataIdentification {}
                        (x/element :gmd:citation {}
                                   (x/element :gmd:CI_Citation {}
                                              (h/iso-string-element :gmd:title granule-ur)
                                              (h/iso-date-element "creation" update-time)))
                        (h/iso-string-element :gmd:abstract "GranuleUR")
                        (h/iso-string-element :gmd:purpose "GranuleUR")
                        (h/iso-string-element :gmd:language "eng"))))

(def identification-abstract
  "Returns the abstract. Not sure if we can hard-code it like this."
  (str "The SMAP Level 1C_S0_HiRes product provides multilooked Synthetic Aperture Radar "
       "normalized cross sections in an instrument swath based array with 1 km resolution."))

(defn- generate-identification-info-element
  "Returns the main identification info element that contains most of the SMAP ISO granule fields"
  [producer-gran-id update-time short-name version-id spatial-coverage temporal ocsds]
  (x/element
    :gmd:identificationInfo {}
    (x/element
      :gmd:MD_DataIdentification {}
      (x/element
        :gmd:citation {}
        (x/element
          :gmd:CI_Citation {}
          (x/element :gmd:title {}
                     (x/element :gmx:FileName {} producer-gran-id))
          (h/iso-date-element "creation" update-time)
          (h/iso-string-element :gmd:edition version-id)
          (h/generate-short-name-element short-name)
          (h/generate-version-id-element version-id)))
      ;; This probably shouldn't be hard-coded
      (h/iso-string-element :gmd:abstract identification-abstract)
      (h/iso-string-element :gmd:language "eng")
      (x/element :gmd:extent {}
                 (x/element :gmd:EX_Extent {}
                            (s/generate-spatial spatial-coverage)
                            (s/generate-orbit-calculated-spatial-domains ocsds)
                            (gt/generate-temporal temporal))))))

(defn- generate-restriction-flag-element
  "Returns the smap iso restriction flag element"
  [access-value update-time]
  (x/element
    :gmd:identificationInfo {}
    (x/element
      :gmd:MD_DataIdentification {}
      (h/generate-citation-element "RestrictionFlag" "revision" update-time)
      (h/iso-string-element :gmd:abstract "RestrictionFlag")
      (h/iso-string-element :gmd:purpose "RestrictionFlag")
      (x/element :gmd:resourceConstraints {}
                 (x/element :gmd:MD_LegalConstraints {}
                            (h/iso-string-element :gmd:otherConstraints access-value)))
      (h/iso-string-element :gmd:language "eng"))))

(defn- generate-distribution-info-element
  [related-urls]
  (x/element
    :gmd:distributionInfo {}
    (x/element
      :gmd:MD_Distribution {}
      (x/element :gmd:distributor {}
                 (x/element :gmd:MD_Distributor {}
                            (x/element :gmd:distributorContact {})
                            (ru/generate-related-urls related-urls))))))

(def processing-step-description
  "Returns the processing step description. Not sure if we can hard-code it like this."
  (str "Converts instrument telemetry into a data set that contains horizonally polarized, "
       "vertically polarized and cross polarized normalized radar cross sections, "
       "each of which are multilooked onto a 1 km swath oriented grid."))

(defn- generate-data-quality-info-element
  [production-date-time]
  (when production-date-time
    (x/element
      :gmd:dataQualityInfo {}
      (x/element
        :gmd:DQ_DataQuality {}
        (x/element :gmd:scope {})
        (x/element
          :gmd:lineage {}
          (x/element
            :gmd:LI_Lineage {}
            (x/element
              :gmd:processStep {}
              (x/element
                :gmi:LE_ProcessStep {}
                (h/iso-string-element :gmd:description processing-step-description)
                (x/element :gmd:dateTime {}
                           (x/element :gco:DateTime {} (str production-date-time)))))))))))

(extend-protocol cmr.umm.iso-smap.iso-smap-core/UmmToIsoSmapXml
  UmmGranule
  (umm->iso-smap-xml
    ([granule]
     (let [{{:keys [entry-title short-name version-id]} :collection-ref
            {:keys [insert-time update-time]} :data-provider-timestamps
            :keys [granule-ur data-granule access-value temporal orbit-calculated-spatial-domains
                   related-urls spatial-coverage]} granule
           {:keys [producer-gran-id production-date-time]} data-granule]
       (x/emit-str
         (x/element
           :gmd:DS_Series h/iso-header-attributes
           (x/element
             :gmd:composedOf {}
             (x/element
               :gmd:DS_DataSet {}
               (x/element
                 :gmd:has {}
                 (x/element
                   :gmi:MI_Metadata {}
                   (x/element :gmd:fileIdentifier {}
                              (x/element :gmx:FileName {} producer-gran-id))
                   (h/iso-string-element :gmd:language "eng")
                   h/iso-charset-element
                   (h/iso-hierarchy-level-element "dataset")
                   (x/element :gmd:contact {})
                   (x/element :gmd:dateStamp {}
                              (x/element :gco:Date {} (f/unparse (f/formatters :date) update-time)))
                   (generate-granule-ur-element granule-ur update-time)
                   (generate-identification-info-element
                     producer-gran-id update-time short-name version-id
                     spatial-coverage temporal orbit-calculated-spatial-domains)
                   (h/generate-dataset-id-element entry-title update-time)
                   (h/generate-datetime-element "InsertTime" "creation" insert-time)
                   (h/generate-datetime-element "UpdateTime" "revision" update-time)
                   (generate-restriction-flag-element access-value update-time)
                   (generate-distribution-info-element related-urls)
                   (generate-data-quality-info-element production-date-time)))))
           (x/element :gmd:seriesMetadata {:gco:nilReason "inapplicable"})))))))

(defn validate-xml
  "Validates the XML against the SMAP ISO schema."
  [xml]
  (v/validate-xml (io/resource "schema/iso_smap/schema.xsd") xml))
