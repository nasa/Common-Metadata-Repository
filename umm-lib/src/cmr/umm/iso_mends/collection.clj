(ns cmr.umm.iso-mends.collection
  "Contains functions for parsing and generating the MENDS ISO dialect."
  (:require [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clj-time.core :as time]
            [cmr.common.xml :as cx]
            [cmr.umm.iso-mends.core :as core]
            [cmr.umm.collection :as c]
            [cmr.common.xml :as v]
            [cmr.umm.iso-mends.collection.related-url :as ru]
            [cmr.umm.iso-mends.collection.org :as org]
            [cmr.umm.iso-mends.collection.temporal :as t]
            [cmr.umm.iso-mends.collection.platform :as platform]
            [cmr.umm.iso-mends.collection.keyword :as k]
            [cmr.umm.iso-mends.collection.project :as proj]
            [cmr.umm.iso-mends.collection.associated-difs :as dif]
            [cmr.umm.iso-mends.collection.helper :as h])
  (:import cmr.umm.collection.UmmCollection))

(defn trunc
  "Returns the given string truncated to n characters."
  [s n]
  (subs s 0 (min (count s) n)))

(defn- xml-elem->Product
  "Returns a UMM Product from a parsed XML structure"
  [id-elem]
  (let [short-name (cx/string-at-path
                     id-elem
                     [:citation :CI_Citation :identifier :MD_Identifier :code :CharacterString])
        long-name (cx/string-at-path
                    id-elem
                    [:citation :CI_Citation :identifier :MD_Identifier :description :CharacterString])
        long-name (trunc long-name 1024)
        version-id (cx/string-at-path id-elem [:citation :CI_Citation :edition :CharacterString])
        processing-level-id (cx/string-at-path
                              id-elem
                              [:processingLevel :MD_Identifier :code :CharacterString])]
    (c/map->Product {:short-name short-name
                     :long-name long-name
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
        update-time (parse-date-fn "revision")]
    (when (or insert-time update-time)
      (c/map->DataProviderTimestamps
        {:insert-time insert-time
         :update-time update-time}))))

(defn- xml-elem->access-value
  "Returns a UMM access-value from a parsed XML structure"
  [id-elem]
  (let [constraints (cx/strings-at-path id-elem [:resourceConstraints :MD_LegalConstraints
                                                 :otherConstraints :CharacterString])
        restriction (first (filter (partial re-find #"Restriction Flag:") constraints))
        restriction-flag (when restriction (str/replace restriction #"Restriction Flag:" ""))]
    (when (seq restriction-flag) (Double. restriction-flag))))

(defn- xml-elem->contact-name
  "Returns the contact name from a parsed IdentificationInfo XML structure"
  [xml-struct]
  (cmr.common.dev.capture-reveal/capture xml-struct)
  (let [person-name (cx/string-at-path xml-struct [:pointOfContact
                                                   :CI_ResponsibleParty
                                                   :individualName
                                                   :CharacterString])
        org-name (cx/string-at-path xml-struct [:pointOfContact
                                                :CI_ResponsibleParty
                                                :organisationName
                                                :CharacterString])
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
  (let [id-elem (cx/element-at-path xml-struct [:identificationInfo :MD_DataIdentification])
        product (xml-elem->Product id-elem)
        data-provider-timestamps (xml-elem->DataProviderTimestamps id-elem) ]
    (c/map->UmmCollection
      {:entry-id (str (:short-name product) "_" (:version-id product))
       :entry-title (cx/string-at-path xml-struct [:fileIdentifier :CharacterString])
       :summary (cx/string-at-path id-elem [:abstract :CharacterString])
       :product product
       :access-value (xml-elem->access-value id-elem)
       :data-provider-timestamps data-provider-timestamps
       :spatial-keywords (k/xml-elem->spatial-keywords id-elem)
       :temporal-keywords (k/xml-elem->temporal-keywords id-elem)
       :temporal (t/xml-elem->Temporal id-elem)
       :science-keywords (k/xml-elem->ScienceKeywords id-elem)
       :platforms (platform/xml-elem->Platforms xml-struct)
       ;; AdditionalAttributes is not fully supported as documented in CMR-692
       ; :product-specific-attributes (psa/xml-elem->ProductSpecificAttributes xml-struct)
       :projects (proj/xml-elem->Projects xml-struct)
       ;; TwoDCoordinateSystems is not fully supported as documented in CMR-693
       ; :two-d-coordinate-systems (two-d/xml-elem->TwoDCoordinateSystems xml-struct)
       :related-urls (ru/xml-elem->related-urls xml-struct)
       ;; TODO: Ted has updated the xsl today, will try to add spatial support next.
       ; :spatial-coverage (xml-elem->SpatialCoverage xml-struct)
       :organizations (org/xml-elem->Organizations xml-struct)
       :associated-difs (dif/xml-elem->associated-difs id-elem)
       :contact-email (xml-elem->email id-elem)
       :contact-name (xml-elem->contact-name id-elem)})))

(defn parse-collection
  "Parses ISO XML into a UMM Collection record."
  [xml]
  (xml-elem->Collection (x/parse-str xml)))

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
    (x/element :gmd:characterSet {}
               (x/element :gmd:MD_CharacterSetCode iso-code-list-attributes "utf8"))))

(def iso-hierarchy-level-element
  "Defines the iso-hierarchy-level-element"
  (x/element :gmd:hierarchyLevel {} h/scope-code-element))

(defn- iso-date-type-element
  "Returns the iso date type element for the given type"
  [type]
  (x/element
    :gmd:dateType {}
    (x/element
      :gmd:CI_DateTypeCode
      {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_DateTypeCode"
       :codeListValue type} type)))

(defn- iso-date-element
  "Returns the iso date element based on the given type and date"
  [type date]
  (x/element :gmd:date {}
             (x/element :gmd:CI_Date {}
                        (x/element :gmd:date {}
                                   (x/element :gco:DateTime {} (str date)))
                        (iso-date-type-element type))))

(defn- iso-resource-constraints-element
  "Returns the iso resource constraints element"
  [restriction-flag]
  (x/element
    :gmd:resourceConstraints {}
    (x/element
      :gmd:MD_LegalConstraints {}
      (h/iso-string-element :gmd:useLimitation "Restriction Comment:")
      (h/iso-string-element :gmd:otherConstraints (str "Restriction Flag:" restriction-flag)))))

(defn- iso-processing-level-id-element
  "Returns the iso processing level id element"
  [processing-level-id]
  (x/element
    :gmd:processingLevel {}
    (x/element
      :gmd:MD_Identifier {}
      (h/iso-string-element :gmd:code processing-level-id))))

(defn- generate-distributor-contact
  "Generate the distributorContact element"
  [archive-center]
  (x/element
    :gmd:distributorContact {}
    (x/element
      :gmd:CI_ResponsibleParty {}
      (h/iso-string-element :gmd:organisationName archive-center)
      (x/element :gmd:role {}
                 (x/element :gmd:CI_RoleCode (h/role-code-attributes "distributor") "distributor")))))

(defn- generate-distributor-transfer-options
  "Generate the distributorTransferOptions element"
  [related-urls]
  (x/element
    :gmd:distributorTransferOptions {}
    (x/element
      :gmd:MD_DigitalTransferOptions {}
      (ru/generate-resource-urls related-urls))))


(defn- generate-distribution-info
  "Generate the ISO distribution info part of the ISO xml with the given archive center and related urls"
  [archive-center related-urls]
  (x/element
    :gmd:distributionInfo {}
    (x/element :gmd:MD_Distribution {}
               (x/element :gmd:distributor {}
                          (x/element :gmd:MD_Distributor {}
                                     (generate-distributor-contact archive-center)
                                     (generate-distributor-transfer-options related-urls))))))

(extend-protocol cmr.umm.iso-mends.core/UmmToIsoMendsXml
  UmmCollection
  (umm->iso-mends-xml
    ([collection]
     (cmr.umm.iso-mends.core/umm->iso-mends-xml collection false))
    ([collection indent?]
     (let [{{:keys [short-name long-name version-id processing-level-id]} :product
            dataset-id :entry-title
            restriction-flag :access-value
            {:keys [insert-time update-time]} :data-provider-timestamps
            :keys [organizations spatial-keywords temporal-keywords temporal science-keywords
                   platforms product-specific-attributes projects two-d-coordinate-systems
                   related-urls spatial-coverage summary associated-difs]} collection
           archive-center (org/get-organization-name :archive-center organizations)
           platforms (platform/platforms-with-id platforms)
           emit-fn (if indent? x/indent-str x/emit-str)]
       (emit-fn
         (x/element :gmi:MI_Metadata iso-header-attributes
                    (h/iso-string-element :gmd:fileIdentifier dataset-id)
                    (h/iso-string-element :gmd:language "eng")
                    iso-charset-element
                    iso-hierarchy-level-element
                    (x/element :gmd:contact {:gco:nilReason "missing"})
                    ;; NOTE: it does not make sense to put the current date time here
                    ;; and it would cause metadata comparision issues. So we use update time here.
                    (x/element :gmd:dateStamp {}
                               (x/element :gco:DateTime {} (str update-time)))
                    (x/element :gmd:metadataStandardName {}
                               (x/element :gco:CharacterString {}
                                          "ISO 19115-2 Geographic Information - Metadata Part 2 Extensions for imagery and gridded data"))
                    (x/element :gmd:metadataStandardVersion {}
                               (x/element :gco:CharacterString {}
                                          "ISO 19115-2:2009(E)"))
                    (x/element
                      :gmd:identificationInfo {}
                      (x/element
                        :gmd:MD_DataIdentification {}
                        (x/element
                          :gmd:citation {}
                          (x/element
                            :gmd:CI_Citation {}
                            (h/iso-string-element :gmd:title (format "%s > %s" short-name long-name))
                            (iso-date-element "revision" update-time)
                            (iso-date-element "creation" insert-time)
                            (h/iso-string-element :gmd:edition version-id)
                            (x/element :gmd:identifier {}
                                       (x/element :gmd:MD_Identifier {}
                                                  (h/iso-string-element :gmd:code short-name)
                                                  (h/iso-string-element :gmd:description long-name)))
                            (dif/generate-associated-difs associated-difs)))

                        (h/iso-string-element :gmd:abstract summary)
                        (x/element :gmd:purpose {:gco:nilReason "missing"})
                        (k/generate-science-keywords science-keywords)
                        (k/generate-spatial-keywords spatial-keywords)
                        (k/generate-temporal-keywords temporal-keywords)
                        (org/generate-archive-center archive-center)
                        (proj/generate-project-keywords projects)
                        (platform/generate-platform-keywords platforms)
                        (platform/generate-instrument-keywords platforms)
                        (iso-resource-constraints-element restriction-flag)
                        (h/iso-string-element :gmd:language "eng")
                        (x/element :gmd:extent {}
                                   (x/element :gmd:EX_Extent {:id "boundingExtent"}
                                              ; (spatial/generate-spatial spatial-coverage)
                                              (t/generate-temporal temporal)))
                        (iso-processing-level-id-element processing-level-id)))
                    (generate-distribution-info archive-center related-urls)
                    (org/generate-processing-center organizations)
                    (x/element :gmi:acquisitionInformation {}
                               (x/element :gmi:MI_AcquisitionInformation {}
                                          (platform/generate-instruments platforms)
                                          (proj/generate-projects projects)
                                          (platform/generate-platforms platforms)))))))))

(defn validate-xml
  "Validates the XML against the ISO schema."
  [xml]
  (v/validate-xml (io/resource "schema/iso_mends/schema/1.0/ISO19115-2_EOS.xsd") xml))


