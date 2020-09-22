(ns cmr.umm.iso-smap.iso-smap-collection
  "Contains functions for parsing and generating the SMAP ISO dialect."
  (:require [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [clj-time.core :as time]
            [clj-time.format :as f]
            [cmr.common.xml :as cx]
            [cmr.umm.iso-smap.iso-smap-core :as core]
            [cmr.umm.umm-collection :as c]
            [cmr.common.xml :as v]
            [cmr.umm.iso-smap.collection.personnel :as pe]
            [cmr.umm.iso-smap.collection.org :as org]
            [cmr.umm.iso-smap.collection.keyword :as kw]
            [cmr.umm.iso-smap.collection.progress :as progress]
            [cmr.umm.iso-smap.collection.spatial :as spatial]
            [cmr.umm.iso-smap.collection.temporal :as t]
            [cmr.umm.iso-smap.helper :as h])
  (:import cmr.umm.umm_collection.UmmCollection))

(defn- xml-elem-with-id-tag
  "Returns the identification element with the given tag"
  [id-elems tag]
  (h/xml-elem-with-path-value id-elems [:citation :CI_Citation :identifier :MD_Identifier
                                        :description :CharacterString] tag))

(defn- xml-elem->Product
  "Returns a UMM Product from a parsed XML structure"
  [product-elem version-description]
  (let [long-name (cx/string-at-path product-elem [:citation :CI_Citation :title :CharacterString])
        id-elems (cx/elements-at-path product-elem [:citation :CI_Citation :identifier :MD_Identifier])
        short-name-elem (h/xml-elem-with-path-value id-elems [:description :CharacterString] "The ECS Short Name")
        short-name (cx/string-at-path short-name-elem [:code :CharacterString])
        version-elem (h/xml-elem-with-path-value id-elems [:description :CharacterString] "The ECS Version ID")
        version-id (cx/string-at-path version-elem [:code :CharacterString])]
    (c/map->Product {:short-name short-name
                     :long-name long-name
                     :version-id version-id
                     :version-description version-description})))

(defn- xml-elem->RevisionDate
  "Returns revision date from a parsed XML structure"
  [revision-elem]
  (let [date-elem (cx/element-at-path revision-elem [:citation :CI_Citation :date :CI_Date])
        date-type-code (cx/string-at-path date-elem [:dateType :CI_DateTypeCode])]
    (when date-type-code
      (cx/datetime-at-path date-elem [:date :Date]))))

(defn xml-elem->DataProviderTimestamps
  "Returns a UMM DataProviderTimestamps from a parsed XML structure"
  [id-elems]
  (let [insert-time-elem (h/xml-elem-with-title-tag id-elems "InsertTime")
        update-time-elem (h/xml-elem-with-title-tag id-elems "UpdateTime")
        insert-time (cx/datetime-at-path insert-time-elem [:citation :CI_Citation :date :CI_Date :date :DateTime])
        update-time (cx/datetime-at-path update-time-elem [:citation :CI_Citation :date :CI_Date :date :DateTime])
        revision-elem (xml-elem-with-id-tag id-elems "The ECS Short Name")
        revision-date (xml-elem->RevisionDate revision-elem)]
    (when (or insert-time update-time)
      (c/map->DataProviderTimestamps
        {:insert-time insert-time
         :update-time update-time
         :revision-date-time revision-date}))))

(defn- xml-elem->associated-difs
  "Returns associated difs from a parsed XML structure"
  [id-elems]
  ;; There can be no more than one DIF ID for SMAP ISO
  (let [dif-elem (h/xml-elem-with-title-tag id-elems "DIFID")
        dif-id (cx/string-at-path
                 dif-elem
                 [:citation :CI_Citation :identifier :MD_Identifier :code :CharacterString])]
    (when dif-id [dif-id])))

(defn- xml-elem->Collection
  "Returns a UMM Product from a parsed Collection XML structure"
  [xml-struct]
  (let [id-elems (cx/elements-at-path xml-struct [:seriesMetadata :MI_Metadata :identificationInfo
                                                  :MD_DataIdentification])
        version-description (cx/string-at-path
                              xml-struct
                              [:seriesMetadata :MI_Metadata :identificationInfo :MD_DataIdentification
                               :citation :CI_Citation :otherCitationDetails :CharacterString])
        product-elem (xml-elem-with-id-tag id-elems "The ECS Short Name")
        product (xml-elem->Product product-elem version-description)
        {:keys [short-name version-id]} product
        data-provider-timestamps (xml-elem->DataProviderTimestamps id-elems)
        dataset-id-elem (h/xml-elem-with-title-tag id-elems "DataSetId")
        keywords (kw/xml-elem->keywords xml-struct)]
    (c/map->UmmCollection
      {:entry-title (cx/string-at-path
                      dataset-id-elem
                      [:aggregationInfo :MD_AggregateInformation :aggregateDataSetIdentifier
                       :MD_Identifier :code :CharacterString])
       :summary (cx/string-at-path product-elem [:abstract :CharacterString])
       :purpose (cx/string-at-path product-elem [:purpose :CharacterString])
       :metadata-language (cx/string-at-path xml-struct [:seriesMetadata :MI_Metadata :language
                                                         :CharacterString])
       :product product
       :data-provider-timestamps data-provider-timestamps
       :temporal (t/xml-elem->Temporal xml-struct)
       :science-keywords (kw/keywords->ScienceKeywords keywords)
       :platforms (kw/keywords->Platforms keywords)
       :spatial-coverage (spatial/xml-elem->SpatialCoverage xml-struct)
       :organizations (org/xml-elem->Organizations id-elems)
       :associated-difs (xml-elem->associated-difs id-elems)
       :personnel (pe/xml-elem->personnel xml-struct)
       :collection-progress (progress/parse xml-struct)})))

(defn parse-collection
  "Parses ISO XML into a UMM Collection record."
  [xml]
  (xml-elem->Collection (x/parse-str xml)))

(defn parse-temporal
  "Parses the XML and extracts the temporal data."
  [xml]
  (t/xml-elem->Temporal (x/parse-str xml)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generators

(defn- iso-aggregation-info-element
  "Defines the iso-aggregation-info element"
  [dataset-id]
  (x/element
    :gmd:aggregationInfo {}
    (x/element
      :gmd:MD_AggregateInformation {}
      (x/element :gmd:aggregateDataSetIdentifier {}
                 (x/element :gmd:MD_Identifier {}
                            (h/iso-string-element :gmd:code dataset-id)))
      (x/element :gmd:associationType {}
                 (x/element :gmd:DS_AssociationTypeCode
                            {:codeList "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#DS_AssociationTypeCode"
                             :codeListValue "largerWorkCitation"}
                            "largerWorkCitation"))
      (x/element :gmd:initiativeType {}
                 (x/element :gmd:DS_InitiativeTypeCode
                            {:codeList "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#DS_AssociationTypeCode"
                             :codeListValue "mission"}
                            "mission")))))

(defn- generate-dif-element
  "Returns the smap iso DIFID element"
  [dif-id datetime]
  (x/element
    :gmd:identificationInfo {}
    (x/element
      :gmd:MD_DataIdentification {}
      (x/element :gmd:citation {}
                 (x/element :gmd:CI_Citation {}
                            (h/iso-string-element :gmd:title "DIFID")
                            (h/iso-date-element "revision" datetime)
                            (x/element :gmd:identifier {}
                                       (x/element :gmd:MD_Identifier {}
                                                  (h/iso-string-element :gmd:code dif-id)))))
      (h/iso-string-element :gmd:abstract "DIFID")
      (h/iso-string-element :gmd:purpose "DIFID")
      (h/iso-string-element :gmd:language "eng"))))

(def publication-title
  "Product Specification Document for the SMAP Level 1A Radar Product (L1A_Radar)")

(def publication-abstract
  "The Product Specification Document that fully describes the content and format of this data product.")

(defn- generate-version-description-element
  "Returns the smap iso version description element."
  [version-description update-time]
  (x/element
    :gmd:identificationInfo {}
    (x/element
      :gmd:MD_DataIdentification {}
      (x/element :gmd:citation {}
                 (x/element :gmd:CI_Citation {}
                            (h/iso-string-element :gmd:title publication-title)
                            (h/iso-date-element "publication" update-time)
                            (h/iso-string-element :gmd:otherCitationDetails version-description)))
      (h/iso-string-element :gmd:abstract publication-abstract)
      (h/iso-string-element :gmd:language "eng"))))

(extend-protocol cmr.umm.iso-smap.iso-smap-core/UmmToIsoSmapXml
  UmmCollection
  (umm->iso-smap-xml
    ([collection]
     (let [{{:keys [short-name long-name version-id version-description]} :product
            dataset-id :entry-title
            {:keys [insert-time update-time revision-date-time]} :data-provider-timestamps
            :keys [organizations temporal platforms spatial-coverage summary purpose
                   associated-difs science-keywords metadata-language]} collection
           ;; UMM model has a nested relationship between instruments and platforms,
           ;; but there is no nested relationship between instruments and platforms in SMAP ISO xml.
           ;; To work around this problem, we list all instruments under each platform.
           ;; In other words, all platforms will have the same instruments.
           instruments (when (first platforms) (:instruments (first platforms)))]
       (x/emit-str
         (x/element
           :gmd:DS_Series h/iso-header-attributes
           (x/element :gmd:composedOf {:gco:nilReason "inapplicable"})
           (x/element
             :gmd:seriesMetadata {}
             (x/element
               :gmi:MI_Metadata {}
               (h/iso-string-element :gmd:language metadata-language)
               h/iso-charset-element
               (h/iso-hierarchy-level-element "series")
               (x/element :gmd:contact {})
               (x/element :gmd:dateStamp {}
                          (x/element :gco:Date {} (f/unparse (f/formatters :date) update-time)))
               (x/element
                 :gmd:identificationInfo {}
                 (x/element
                   :gmd:MD_DataIdentification {}
                   (x/element
                     :gmd:citation {}
                     (x/element
                       :gmd:CI_Citation {}
                       (h/iso-string-element :gmd:title long-name)
                       (h/iso-date-element "revision" revision-date-time true)
                       (h/generate-short-name-element short-name)
                       (h/generate-version-id-element version-id)
                       (org/generate-processing-center organizations)))
                   (h/iso-string-element :gmd:abstract summary)
                   (h/iso-string-element :gmd:purpose purpose)
                   (h/iso-string-element :gmd:credit "National Aeronautics and Space Administration (NASA)")
                   (progress/generate collection)
                   (org/generate-archive-center organizations)
                   (kw/generate-keywords science-keywords)
                   (kw/generate-keywords instruments)
                   (kw/generate-keywords platforms)
                   (iso-aggregation-info-element dataset-id)
                   (h/iso-string-element :gmd:language "eng")
                   (x/element
                     :gmd:extent {}
                     (x/element
                       :gmd:EX_Extent {}
                       (spatial/generate-spatial spatial-coverage)
                       (t/generate-temporal temporal)))))
               (generate-version-description-element version-description update-time)
               (h/generate-dataset-id-element dataset-id update-time)
               (h/generate-datetime-element "InsertTime" "creation" insert-time)
               (h/generate-datetime-element "UpdateTime" "revision" update-time)
               (generate-dif-element (first associated-difs) update-time)))))))))


(defn validate-xml
  "Validates the XML against the ISO schema."
  [xml]
  (v/validate-xml (io/resource "schema/iso_smap/schema.xsd") xml))


