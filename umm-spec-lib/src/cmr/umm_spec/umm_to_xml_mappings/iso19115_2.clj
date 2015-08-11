(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2
  "Defines mappings from UMM records into ISO19115-2 XML."
  (:require [cmr.umm-spec.umm-to-xml-mappings.dsl :refer :all]))

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

(def umm-c-to-iso19115-2-xml
  [:gmi:MI_Metadata
   iso19115-2-xml-namespaces
   [:gmd:identificationInfo
    [:gmd:MD_DataIdentification
     [:gmd:citation
      [:gmd:CI_Citation
       [:gmd:title (char-string-from "/EntryTitle")]
       [:gmd:identifier
        [:gmd:MD_Identifier
         [:gmd:code (char-string-from "/EntryId/Id")]]]]]]]])


