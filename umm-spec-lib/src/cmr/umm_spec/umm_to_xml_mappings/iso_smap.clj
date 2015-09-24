(ns cmr.umm-spec.umm-to-xml-mappings.iso-smap
  "Defines mappings from UMM records into ISO SMAP XML."
  (:require [clojure.string :as str]
            [cmr.umm-spec.iso-utils :as iso]
            [cmr.umm-spec.xml.gen :refer :all]
            [cmr.umm-spec.util :as su :refer [with-default]]))

(def iso-smap-xml-namespaces
  {:xmlns:gmd "http://www.isotc211.org/2005/gmd"
   :xmlns:gco "http://www.isotc211.org/2005/gco"
   :xmlns:gmi "http://www.isotc211.org/2005/gmi"
   :xmlns:gml "http://www.opengis.net/gml/3.2"
   :xmlns:gmx "http://www.isotc211.org/2005/gmx"
   :xmlns:gsr "http://www.isotc211.org/2005/gsr"
   :xmlns:gss "http://www.isotc211.org/2005/gss"
   :xmlns:gts "http://www.isotc211.org/2005/gts"
   :xmlns:srv "http://www.isotc211.org/2005/srv"
   :xmlns:xlink "http://www.w3.org/1999/xlink"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"})

(defn- date-mapping
  "Returns the date element mapping for the given name and date value in string format."
  [date-name value]
  [:gmd:date
   [:gmd:CI_Date
    [:gmd:date
     [:gco:DateTime value]]
    [:gmd:dateType
     [:gmd:CI_DateTypeCode {:codeList "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_DateTypeCode"
                            :codeListValue date-name}
      date-name]]]])

(defn- generate-collection-progress
  "Returns ISO SMAP CollectionProgress element from UMM-C collection c."
  [c]
  (when-let [collection-progress (:CollectionProgress c)]
    [:gmd:MD_ProgressCode
     {:codeList "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#MD_ProgressCode"
      :codeListValue (str/lower-case collection-progress)}
     collection-progress]))

(defn umm-c-to-iso-smap-xml
  "Returns ISO SMAP XML from UMM-C record c."
  [c]
  (xml
   [:gmd:DS_Series
    iso-smap-xml-namespaces
    [:gmd:composedOf {:gco:nilReason "inapplicable"}]
    [:gmd:seriesMetadata
     [:gmi:MI_Metadata
      [:gmd:language (char-string "eng")]
      [:gmd:contact {:xlink:href "#alaskaSARContact"}]
      [:gmd:dateStamp
       [:gco:Date "2013-01-02"]]
      [:gmd:identificationInfo
       [:gmd:MD_DataIdentification
        [:gmd:citation
         [:gmd:CI_Citation
          [:gmd:title (char-string "SMAP Level 1A Parsed Radar Instrument Telemetry")]
          (date-mapping "revision" "2000-12-31T19:00:00-05:00")

          [:gmd:identifier
           [:gmd:MD_Identifier
            [:gmd:code (char-string (:EntryId c))]
            [:gmd:description [:gco:CharacterString "The ECS Short Name"]]]]

          [:gmd:identifier
           [:gmd:MD_Identifier
            [:gmd:code (char-string (with-default (:Version c)))]
            [:gmd:description [:gco:CharacterString "The ECS Version ID"]]]]]]
        [:gmd:abstract (char-string (:Abstract c))]
        [:gmd:purpose {:gco:nilReason "missing"} (char-string (:Purpose c))]
        [:gmd:status (generate-collection-progress c)]
        [:gmd:descriptiveKeywords
         [:gmd:MD_Keywords
          (for [platform (:Platforms c)]
            [:gmd:keyword
             (char-string (iso/smap-keyword-str platform))])
          (for [instrument (mapcat :Instruments (:Platforms c))]
            [:gmd:keyword
             (char-string (iso/smap-keyword-str instrument))])]]
        [:gmd:language (char-string (or (:DataLanguage c) "eng"))]
        [:gmd:extent
         [:gmd:EX_Extent
          (for [temporal (:TemporalExtents c)
                rdt (:RangeDateTimes temporal)]
            [:gmd:temporalElement
             [:gmd:EX_TemporalExtent
              [:gmd:extent
               [:gml:TimePeriod {:gml:id (iso/generate-id)}
                [:gml:beginPosition (:BeginningDateTime rdt)]
                [:gml:endPosition (su/nil-to-empty-string (:EndingDateTime rdt))]]]]])
          (for [temporal (:TemporalExtents c)
                date (:SingleDateTimes temporal)]
            [:gmd:temporalElement
             [:gmd:EX_TemporalExtent
              [:gmd:extent
               [:gml:TimeInstant {:gml:id (iso/generate-id)}
                [:gml:timePosition date]]]]])]]]]
      [:gmd:identificationInfo
       [:gmd:MD_DataIdentification
        [:gmd:citation
         [:gmd:CI_Citation
          [:gmd:title (char-string "DataSetId")]
          (date-mapping "revision" "2000-12-31T19:00:00-05:00")]]
        [:gmd:abstract (char-string "DataSetId")]
        [:gmd:aggregationInfo
         [:gmd:MD_AggregateInformation
          [:gmd:aggregateDataSetIdentifier
           [:gmd:MD_Identifier
            [:gmd:code (char-string (:EntryTitle c))]]]
          [:gmd:associationType
           [:gmd:DS_AssociationTypeCode {:codeList "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#DS_AssociationTypeCode"
                                         :codeListValue "largerWorkCitation"}
            "largerWorkCitation"]]]]
        [:gmd:language (char-string "eng")]]]]]]))

