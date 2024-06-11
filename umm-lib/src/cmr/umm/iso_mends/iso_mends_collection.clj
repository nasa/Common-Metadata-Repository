(ns cmr.umm.iso-mends.iso-mends-collection
  "Contains functions for parsing and generating the MENDS ISO dialect."
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common.util :as util]
   [cmr.common.xml :as cx]
   [cmr.umm.iso-mends.iso-mends-core :as core]
   [cmr.umm.umm-collection :as coll]
   [cmr.umm.iso-mends.collection.related-url :as ru]
   [cmr.umm.iso-mends.collection.personnel :as pe]
   [cmr.umm.iso-mends.collection.org :as org]
   [cmr.umm.iso-mends.collection.temporal :as temporal]
   [cmr.umm.iso-mends.collection.platform :as platform]
   [cmr.umm.iso-mends.collection.keyword :as k-word]
   [cmr.umm.iso-mends.collection.project-element :as proj]
   [cmr.umm.iso-mends.collection.associated-difs :as dif]
   [cmr.umm.iso-mends.collection.collection-association :as ca]
   [cmr.umm.iso-mends.collection.product-specific-attribute :as psa]
   [cmr.umm.iso-mends.collection.helper :as helper]
   [cmr.umm.iso-mends.spatial :as sp])
  (:import cmr.umm.umm_collection.UmmCollection))

(defn- xml-elem->Product
  "Returns a UMM Product from a parsed XML structure"
  [id-elem]
  (let [long-name (cx/string-at-path
                    id-elem
                    [:citation :CI_Citation :identifier :MD_Identifier :description :CharacterString])
        long-name (util/trunc long-name 1024)
        short-name (cx/string-at-path
                     id-elem
                     [:citation :CI_Citation :identifier :MD_Identifier :code :CharacterString])
        version-id (cx/string-at-path id-elem [:citation :CI_Citation :edition :CharacterString])
        processing-level-id (cx/string-at-path
                              id-elem
                              [:processingLevel :MD_Identifier :code :CharacterString])]
    (coll/map->Product {:long-name long-name
                        :short-name short-name
                        :version-id version-id
                        :processing-level-id processing-level-id})))

(defn- xml-elem->DataProviderTimestamps
  "Returns a UMM DataProviderTimestamps from a parsed XML structure"
  [id-elem]
  (let [parse-date-fn
        (fn [tag]
          (let [date-elements (cx/elements-at-path id-elem [:citation :CI_Citation :date :CI_Date])
                tag-elem (first (filter
                                  #(= tag (cx/string-at-path % [:dateType :CI_DateTypeCode]))
                                  date-elements))]
            (cx/datetime-at-path tag-elem [:date :DateTime])))
        insert-time (parse-date-fn "creation")
        revision-date-time (parse-date-fn "revision")]
    (when (or insert-time revision-date-time)
      (coll/map->DataProviderTimestamps
        {:insert-time insert-time
         ;; ISO MENDS does not have a distinct update time
         :update-time revision-date-time
         :revision-date-time revision-date-time}))))

(defn- xml-elem->access-value
  "Returns a UMM access-value from a parsed XML structure"
  [id-elem]
  (let [constraints (cx/strings-at-path id-elem [:resourceConstraints :MD_LegalConstraints
                                                 :otherConstraints :CharacterString])
        restriction (first (filter (partial re-find #"Restriction Flag:") constraints))
        restriction-flag (when restriction (string/replace restriction #"Restriction Flag:" ""))]
    (when (seq restriction-flag) (Double. restriction-flag))))

(defn- xml-elem->Collection
  "Returns a UMM Product from a parsed Collection XML structure"
  [xml-struct]
  (let [id-elem (core/id-elem xml-struct)
        product (xml-elem->Product id-elem)
        data-provider-timestamps (xml-elem->DataProviderTimestamps id-elem)]
    (coll/map->UmmCollection
      {:entry-title (cx/string-at-path id-elem [:citation :CI_Citation :title :CharacterString])
       :summary (cx/string-at-path id-elem [:abstract :CharacterString])
       :purpose (cx/string-at-path id-elem [:purpose :CharacterString])
       :product product
       :access-value (xml-elem->access-value id-elem)
       :use-constraints (cx/string-at-path id-elem [:resourceConstraints :MD_LegalConstraints :useLimitation :CharacterString])
       :metadata-language (cx/string-at-path xml-struct [:language :CharacterString])
       :data-provider-timestamps data-provider-timestamps
       :spatial-keywords (k-word/xml-elem->spatial-keywords id-elem)
       :temporal-keywords (k-word/xml-elem->temporal-keywords id-elem)
       :temporal (temporal/xml-elem->Temporal id-elem)
       :science-keywords (k-word/xml-elem->ScienceKeywords id-elem)
       :platforms (platform/xml-elem->Platforms xml-struct)
       :product-specific-attributes (psa/xml-elem->ProductSpecificAttributes xml-struct)
       :collection-associations (ca/xml-elem->CollectionAssociations id-elem)
       :projects (proj/xml-elem->Projects xml-struct)
       :related-urls (ru/xml-elem->related-urls xml-struct)
       :personnel (pe/xml-elem->personnel xml-struct)
       :spatial-coverage (sp/xml-elem->SpatialCoverage xml-struct)
       :organizations (org/xml-elem->Organizations xml-struct)
       :associated-difs (dif/xml-elem->associated-difs id-elem)})))

(defn parse-collection
  "Parses ISO XML into a UMM Collection record."
  [xml]
  (xml-elem->Collection (xml/parse-str xml)))

(defn parse-temporal
  "Parses the XML and extracts the temporal data."
  [xml]
  (temporal/xml-elem->Temporal (core/id-elem (xml/parse-str xml))))

(defn parse-access-value
  "Parses the XML and extracts the access value"
  [xml]
  (xml-elem->access-value (core/id-elem (xml/parse-str xml))))

(def iso-header-attributes
  "The set of attributes that go on the dif root element"
  {:xmlns:gmi "http://www.isotc211.org/2005/gmi"
   :xmlns:gco "http://www.isotc211.org/2005/gco"
   :xmlns:gmd "http://www.isotc211.org/2005/gmd"
   :xmlns:gmx "http://www.isotc211.org/2005/gmx"
   :xmlns:gsr "http://www.isotc211.org/2005/gsr"
   :xmlns:gss "http://www.isotc211.org/2005/gss"
   :xmlns:gts "http://www.isotc211.org/2005/gts"
   :xmlns:srv "http://www.isotc211.org/2005/srv"
   :xmlns:gml "http://www.opengis.net/gml/3.2"
   :xmlns:xlink "http://www.w3.org/1999/xlink"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
   :xmlns:swe "http://schemas.opengis.net/sweCommon/2.0/"
   :xmlns:eos "http://earthdata.nasa.gov/schema/eos"
   :xmlns:xs "http://www.w3.org/2001/XMLSchema"})

(def iso-charset-element
  "Defines the iso-charset-element"
  (let [iso-code-list-attributes
        {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_CharacterSetCode"
         :codeListValue "utf8"}]
    (xml/element :gmd:characterSet {}
                 (xml/element :gmd:MD_CharacterSetCode iso-code-list-attributes "utf8"))))

(def iso-hierarchy-level-element
  "Defines the iso-hierarchy-level-element"
  (xml/element :gmd:hierarchyLevel {} helper/scope-code-element))

(def data-quality-scope
  (xml/element :gmd:scope {}
               (xml/element :gmd:DQ_Scope {}
                            (xml/element :gmd:level {} helper/scope-code-element))))

(defn- iso-date-type-element
  "Returns the iso date type element for the given type"
  [type]
  (xml/element
    :gmd:dateType {}
    (xml/element
      :gmd:CI_DateTypeCode
      {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_DateTypeCode"
       :codeListValue type} type)))

(defn- iso-date-element
  "Returns the iso date element based on the given type and date"
  [type date]
  (xml/element :gmd:date {}
               (xml/element :gmd:CI_Date {}
                            (xml/element :gmd:date {}
                                         (xml/element :gco:DateTime {} (str date)))
                            (iso-date-type-element type))))

(defn- iso-resource-constraints-element
  "Returns the iso resource constraints element"
  [restriction-flag use-constraints]
  (xml/element
    :gmd:resourceConstraints {}
    (xml/element
      :gmd:MD_LegalConstraints {}
      (helper/iso-string-element :gmd:useLimitation use-constraints)
      (helper/iso-string-element :gmd:otherConstraints (str "Restriction Flag:" restriction-flag)))))

(defn- iso-processing-level-id-element
  "Returns the iso processing level id element"
  [processing-level-id]
  (xml/element
    :gmd:processingLevel {}
    (xml/element
      :gmd:MD_Identifier {}
      (helper/iso-string-element :gmd:code processing-level-id))))

(defn- first-email
  "Return the first email address from the contact list, or nil."
  [personnel]
  (when-let [contacts (:contacts (first personnel))]
    (when-let [emails (filter #(= :email (:type %)) contacts)]
      (:value (first emails)))))

(defn- generate-distributor-contact
  "Generate the distributorContact element"
  [archive-center personnel]
  (let [email (first-email personnel)]
    (xml/element
     :gmd:distributorContact {}
     (xml/element
      :gmd:CI_ResponsibleParty {}
      (helper/iso-string-element :gmd:organisationName archive-center)
      (when email
        (xml/element
         :gmd:contactInfo {}
         (xml/element
          :gmd:CI_Contact {}
          (xml/element
           :gmd:address {}
           (xml/element
            :gmd:CI_Address {}
            (xml/element
             :gmd:electronicMailAddress {}
             (xml/element :gco:CharacterString {} email)))))))
      (xml/element :gmd:role {}
                   (xml/element :gmd:CI_RoleCode (helper/role-code-attributes "distributor") "distributor"))))))

(defn- generate-distributor-transfer-options
  "Generate the distributorTransferOptions element"
  [related-urls]
  (xml/element
    :gmd:distributorTransferOptions {}
    (xml/element
      :gmd:MD_DigitalTransferOptions {}
      (ru/generate-resource-urls related-urls))))

(defn- generate-distribution-info
  "Generate the ISO distribution info part of the ISO xml with the given archive center and related urls"
  [archive-center related-urls personnel]
  (xml/element
    :gmd:distributionInfo {}
    (xml/element :gmd:MD_Distribution {}
               (xml/element :gmd:distributor {}
                          (xml/element :gmd:MD_Distributor {}
                                     (generate-distributor-contact archive-center personnel)
                                     (generate-distributor-transfer-options related-urls))))))

(defn- generate-data-quality-info
  "Generate the ISO data quality info part of the ISO xml with the given organizations and
  product specific attributes"
  [organizations product-specific-attributes]
  (xml/element
    :gmd:dataQualityInfo {}
    (xml/element
      :gmd:DQ_DataQuality {}
      data-quality-scope
      (xml/element
        :gmd:lineage {}
        (xml/element
          :gmd:LI_Lineage {}
          (xml/element
            :gmd:processStep {}
            (xml/element
              :gmi:LE_ProcessStep {}
              (xml/element :gmd:description {:gco:nilReason "unknown"})
              (org/generate-processing-center organizations)
              (psa/generate-product-specific-attributes product-specific-attributes))))))))

(extend-protocol cmr.umm.iso-mends.iso-mends-core/UmmToIsoMendsXml
  UmmCollection
  (umm->iso-mends-xml
    ([collection]
     (let [{{:keys [short-name long-name version-id processing-level-id]} :product
            dataset-id :entry-title
            restriction-flag :access-value
            {:keys [insert-time update-time revision-date-time]} :data-provider-timestamps
            :keys [organizations spatial-keywords temporal-keywords temporal science-keywords
                   platforms collection-associations projects
                   related-urls spatial-coverage summary purpose
                   associated-difs personnel metadata-language use-constraints
                   product-specific-attributes]} collection
           archive-center (org/get-organization-name :archive-center organizations)
           platforms (platform/platforms-with-id platforms)]
       (xml/emit-str
         (xml/element :gmi:MI_Metadata iso-header-attributes
                    (helper/iso-string-element :gmd:fileIdentifier dataset-id)
                    (helper/iso-string-element :gmd:language metadata-language)
                    iso-charset-element
                    iso-hierarchy-level-element
                    (xml/element :gmd:contact {:gco:nilReason "missing"})
                    ;; NOTE: it does not make sense to put the current date time here
                    ;; and it would cause metadata comparision issues. So we use update time here.
                    (xml/element :gmd:dateStamp {}
                               (xml/element :gco:DateTime {} (str update-time)))
                    (xml/element :gmd:metadataStandardName {}
                               (xml/element :gco:CharacterString {}
                                          "ISO 19115-2 Geographic Information - Metadata Part 2 Extensions for imagery and gridded data"))
                    (xml/element :gmd:metadataStandardVersion {}
                               (xml/element :gco:CharacterString {}
                                          "ISO 19115-2:2009(E)"))
                    (sp/spatial-coverage->coordinate-system-xml spatial-coverage)
                    (xml/element
                      :gmd:identificationInfo {}
                      (xml/element
                        :gmd:MD_DataIdentification {}
                        (xml/element
                          :gmd:citation {}
                          (xml/element
                            :gmd:CI_Citation {}
                            (helper/iso-string-element :gmd:title dataset-id)
                            (when revision-date-time (iso-date-element "revision" revision-date-time))
                            (iso-date-element "creation" insert-time)
                            (helper/iso-string-element :gmd:edition version-id)
                            (xml/element :gmd:identifier {}
                                       (xml/element :gmd:MD_Identifier {}
                                                  (helper/iso-string-element :gmd:code short-name)
                                                  (helper/iso-string-element :gmd:description long-name)))
                            (dif/generate-associated-difs associated-difs)))
                        (helper/iso-string-element :gmd:abstract summary)
                        (if purpose
                          (helper/iso-string-element :gmd:purpose purpose)
                          (xml/element :gmd:purpose {:gco:nilReason "missing"}))
                        (k-word/generate-science-keywords science-keywords)
                        (k-word/generate-spatial-keywords spatial-keywords)
                        (k-word/generate-temporal-keywords temporal-keywords)
                        (org/generate-archive-center archive-center)
                        (proj/generate-project-keywords projects)
                        (platform/generate-platform-keywords platforms)
                        (platform/generate-instrument-keywords platforms)
                        (iso-resource-constraints-element restriction-flag use-constraints)
                        (ca/generate-collection-associations collection-associations)
                        (helper/iso-string-element :gmd:language "eng")
                        (xml/element
                          :gmd:extent {}
                          (xml/element
                            :gmd:EX_Extent {:id "boundingExtent"}
                            (sp/spatial-coverage->extent-description-xml spatial-coverage)
                            (sp/spatial-coverage->extent-xml spatial-coverage)
                            (temporal/generate-temporal temporal)))
                        (iso-processing-level-id-element processing-level-id)))
                    (generate-distribution-info archive-center related-urls personnel)
                    (generate-data-quality-info organizations product-specific-attributes)
                    (xml/element :gmi:acquisitionInformation {}
                               (xml/element :gmi:MI_AcquisitionInformation {}
                                          (platform/generate-instruments platforms)
                                          (proj/generate-projects projects)
                                          (platform/generate-platforms platforms)))))))))

(def schema-location "schema/iso_mends/schema/1.0/ISO19115-2_EOS.xsd")

(defn validate-xml
  "Validates the XML against the ISO schema."
  [xml]
  (cx/validate-xml (io/resource schema-location) xml))
