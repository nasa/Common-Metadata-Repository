(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2
  "Defines mappings from UMM records into ISO19115-2 XML."
  (:require [cmr.umm-spec.umm-to-xml-mappings.dsl :refer :all]))

(defn- gen-id
  []
  (str "d" (java.util.UUID/randomUUID)))

(def iso19115-2-xml-namespaces
  {:xmlns:xs "http://www.w3.org/2001/XMLSchema"
   :xmlns:gmx "http://www.isotc211.org/2005/gmx"
   :xmlns:gss "http://www.isotc211.org/2005/gss"
   :xmlns:gco "http://www.isotc211.org/2005/gco"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
   :xmlns:gmd "http://www.isotc211.org/2005/gmd"
   :xmlns:gmi "http://www.isotc211.org/2005/gmi"
   :xmlns:gml "http://www.opengis.net/gml/3.2"
   :xmlns:xlink "http://www.w3.org/1999/xlink"
   :xmlns:eos "http://earthdata.nasa.gov/schema/eos"
   :xmlns:srv "http://www.isotc211.org/2005/srv"
   :xmlns:gts "http://www.isotc211.org/2005/gts"
   :xmlns:swe "http://schemas.opengis.net/sweCommon/2.0/"
   :xmlns:gsr "http://www.isotc211.org/2005/gsr"})

(defn- date-mapping
  "Returns the date element mapping for the given name and date value in string format."
  [date-name value]
  [:gmd:date
   [:gmd:CI_Date
    [:gmd:date
     [:gco:DateTime value]]
    [:gmd:dateType
     [:gmd:CI_DateTypeCode {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_DateTypeCode"
                            :codeListValue date-name} date-name]]]])

(def umm-c-to-iso19115-2-xml
  [:gmi:MI_Metadata
   iso19115-2-xml-namespaces
   [:gmd:fileIdentifier (char-string-from "/EntryTitle")]
   [:gmd:language (char-string "eng")]
   [:gmd:characterSet
    [:gmd:MD_CharacterSetCode {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_CharacterSetCode"
                               :codeListValue "utf8"} "utf8"]]
   [:gmd:hierarchyLevel
    [:gmd:MD_ScopeCode {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ScopeCode"
                        :codeListValue "series"} "series"]]
   [:gmd:contact {:gco:nilReason "missing"}]
   [:gmd:dateStamp
    [:gco:DateTime "2014-08-25T15:25:44.641-04:00"]]
   [:gmd:metadataStandardName (char-string "ISO 19115-2 Geographic Information - Metadata Part 2 Extensions for imagery and gridded data")]
   [:gmd:metadataStandardVersion (char-string "ISO 19115-2:2009(E)")]


   [:gmd:identificationInfo
    [:gmd:MD_DataIdentification
     [:gmd:citation
      [:gmd:CI_Citation
       [:gmd:title (char-string-from "/EntryTitle")]
       (date-mapping "revision" "2000-12-31T19:00:00-05:00")
       (date-mapping "creation" "2000-12-31T19:00:00-05:00")
       [:gmd:identifier
        [:gmd:MD_Identifier
         [:gmd:code (char-string-from "/EntryId/Id")]]]]]
     [:gmd:abstract (char-string-from "/Abstract")]
     [:gmd:purpose {:gco:nilReason "missing"}]
     [:gmd:language (char-string "eng")]
     [:gmd:extent
      [:gmd:EX_Extent
       (for-each "/TemporalExtent/RangeDateTime"
         [:gmd:temporalElement
          [:gmd:EX_TemporalExtent
           [:gmd:extent
            [:gml:TimePeriod {:gml:id gen-id}
             [:gml:beginPosition (xpath "BeginningDateTime")]
             [:gml:endPosition (xpath "EndingDateTime")]]]]])
       (for-each "/TemporalExtent/SingleDateTime"
         [:gmd:temporalElement
          [:gmd:EX_TemporalExtent
           [:gmd:extent
            [:gml:TimeInstant {:gml:id gen-id}
             [:gml:timePosition (xpath ".")]]]]])]]]]])
