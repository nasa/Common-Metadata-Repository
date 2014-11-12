(ns cmr.umm.iso-smap.collection
  "Contains functions for parsing and generating the SMAP ISO dialect."
  (:require [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [clj-time.core :as time]
            [clj-time.format :as f]
            [cmr.common.xml :as cx]
            [cmr.umm.iso-smap.core :as core]
            [cmr.umm.collection :as c]
            [cmr.common.xml :as v]
            [cmr.umm.iso-smap.collection.org :as org]
            [cmr.umm.iso-smap.collection.temporal :as t]
            [cmr.umm.iso-smap.collection.spatial :as spatial]
            [cmr.umm.iso-smap.helper :as h])
  (:import cmr.umm.collection.UmmCollection))

(defn- xml-elem-with-id-tag
  "Returns the identification element with the given tag"
  [id-elems tag]
  (h/xml-elem-with-path-value id-elems [:citation :CI_Citation :identifier :MD_Identifier
                                        :description :CharacterString] tag))

(defn- xml-elem->Product
  "Returns a UMM Product from a parsed XML structure"
  [product-elem]
  (let [long-name (cx/string-at-path product-elem [:citation :CI_Citation :title :CharacterString])
        id-elems (cx/elements-at-path product-elem [:citation :CI_Citation :identifier :MD_Identifier])
        short-name-elem (h/xml-elem-with-path-value id-elems [:description :CharacterString] "The ECS Short Name")
        short-name (cx/string-at-path short-name-elem [:code :CharacterString])
        version-elem (h/xml-elem-with-path-value id-elems [:description :CharacterString] "The ECS Version ID")
        version-id (cx/string-at-path version-elem [:code :CharacterString])]
    (c/map->Product {:short-name short-name
                     :long-name long-name
                     :version-id version-id})))

(defn xml-elem->DataProviderTimestamps
  "Returns a UMM DataProviderTimestamps from a parsed XML structure"
  [id-elems]
  (let [insert-time-elem (h/xml-elem-with-title-tag id-elems "InsertTime")
        update-time-elem (h/xml-elem-with-title-tag id-elems "UpdateTime")
        insert-time (cx/datetime-at-path insert-time-elem [:citation :CI_Citation :date :CI_Date :date :DateTime])
        update-time (cx/datetime-at-path update-time-elem [:citation :CI_Citation :date :CI_Date :date :DateTime])]
    (when (or insert-time update-time)
      (c/map->DataProviderTimestamps
        {:insert-time insert-time
         :update-time update-time}))))

(defn- xml-elem->associated-difs
  "Returns associated difs from a parsed XML structure"
  [id-elems]
  ;; There can be no more than one DIF ID for SMAP ISO
  (let [dif-elem (h/xml-elem-with-title-tag id-elems "DIFID")
        dif-id (cx/string-at-path
                 dif-elem
                 [:citation :CI_Citation :identifier :MD_Identifier :code :CharacterString])]
    (when dif-id [dif-id])))

(defn- xml-elem->contact-name
  "Returns the contact name from a parsed IdentificationInfo XML structure"
  [xml-struct]
  (cmr.common.dev.capture-reveal/capture xml-struct)
  (let [person-name (cx/string-at-path xml-struct [:pointOfContact
                                                   :CI_ResponsibleParty
                                                   :individualName
                                                   :CharacterString])
        org-name (or (cx/string-at-path xml-struct [:citation
                                                    :CI_Citation
                                                    :citedResponsibleParty
                                                    :CI_ResponsibleParty
                                                    :organisationName
                                                    :CharacterString])
                     (cx/string-at-path xml-struct [:pointOfContact
                                                    :CI_ResponsibleParty
                                                    :organisationName
                                                    :CharacterString]))
        contact-name (or person-name org-name)]
    (if contact-name
      contact-name
      "undefined")))

(defn- xml-elem->email
  "Returns the contact email from a parsed IdentificationInfo XML structure"
  [xml-struct]
  (or (cx/string-at-path xml-struct [:pointOfContact
                                     :CI_ResponsibleParty
                                     :contactInfo
                                     :CI_Contact
                                     :address
                                     :CI_Address
                                     :electronicMailAddress
                                     :CharacterString])
      "support@earthdata.nasa.gov"))

(defn- xml-elem->Collection
  "Returns a UMM Product from a parsed Collection XML structure"
  [xml-struct]
  (let [id-elems (cx/elements-at-path xml-struct [:seriesMetadata :MI_Metadata :identificationInfo
                                                  :MD_DataIdentification])
        product-elem (xml-elem-with-id-tag id-elems "The ECS Short Name")
        product (xml-elem->Product product-elem)
        data-provider-timestamps (xml-elem->DataProviderTimestamps id-elems)
        dataset-id-elem (h/xml-elem-with-title-tag id-elems "DataSetId")]
    (c/map->UmmCollection
      {:entry-id (str (:short-name product) "_" (:version-id product))
       :entry-title (cx/string-at-path
                      dataset-id-elem
                      [:aggregationInfo :MD_AggregateInformation :aggregateDataSetIdentifier
                       :MD_Identifier :code :CharacterString])
       :summary (cx/string-at-path product-elem [:abstract :CharacterString])
       :product product
       :data-provider-timestamps data-provider-timestamps
       :temporal (t/xml-elem->Temporal xml-struct)
       :spatial-coverage (spatial/xml-elem->SpatialCoverage xml-struct)
       :organizations (org/xml-elem->Organizations id-elems)
       :associated-difs (xml-elem->associated-difs id-elems)
       :contact-email (some #(xml-elem->email %)
                            id-elems)
       :contact-name (some #(xml-elem->contact-name %)
                           id-elems)})))

(defn parse-collection
  "Parses ISO XML into a UMM Collection record."
  [xml]
  (xml-elem->Collection (x/parse-str xml)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generators

(def iso-status-element
  "Defines the iso-status element"
  (x/element
    :gmd:status {}
    (x/element
      :gmd:MD_ProgressCode
      {:codeList "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#MD_ProgressCode"
       :codeListValue "ongoing"}
      "ongoing")))

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
  "Returns the smap iso update-time/insert-time element"
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

(extend-protocol cmr.umm.iso-smap.core/UmmToIsoSmapXml
  UmmCollection
  (umm->iso-smap-xml
    ([collection]
     (cmr.umm.iso-smap.core/umm->iso-smap-xml collection false))
    ([collection indent?]
     (let [{{:keys [short-name long-name version-id]} :product
            dataset-id :entry-title
            {:keys [insert-time update-time]} :data-provider-timestamps
            :keys [organizations temporal spatial-coverage summary associated-difs]} collection
           emit-fn (if indent? x/indent-str x/emit-str)]
       (emit-fn
         (x/element
           :gmd:DS_Series h/iso-header-attributes
           (x/element :gmd:composedOf {:gco:nilReason "inapplicable"})
           (x/element
             :gmd:seriesMetadata {}
             (x/element
               :gmi:MI_Metadata {}
               (h/iso-string-element :gmd:language "eng")
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
                       ;; This should be the RevisionDate, but we don't really index it
                       ;; and the type for ECHO10 is datetime, for DIF and SMAP ISO is date.
                       ;; We need to work out how to handle it.
                       ;; For now, just use the update time to replace the revision date.
                       (h/iso-date-element "revision" update-time true)
                       (h/generate-short-name-element short-name)
                       (h/generate-version-id-element version-id)
                       (org/generate-processing-center organizations)))
                   (h/iso-string-element :gmd:abstract summary)
                   (h/iso-string-element :gmd:credit "National Aeronautics and Space Administration (NASA)")
                   iso-status-element
                   (org/generate-archive-center organizations)
                   (iso-aggregation-info-element dataset-id)
                   (h/iso-string-element :gmd:language "eng")
                   (x/element
                     :gmd:extent {}
                     (x/element
                       :gmd:EX_Extent {}
                       (spatial/generate-spatial spatial-coverage)
                       (t/generate-temporal temporal)))))
               (h/generate-dataset-id-element dataset-id update-time)
               (h/generate-datetime-element "InsertTime" "creation" insert-time)
               (h/generate-datetime-element "UpdateTime" "revision" update-time)
               (generate-dif-element (first associated-difs) update-time)))))))))


(defn validate-xml
  "Validates the XML against the ISO schema."
  [xml]
  (v/validate-xml (io/resource "schema/iso_smap/schema.xsd") xml))


