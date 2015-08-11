(ns cmr.umm-spec.umm-to-xml-mappings.iso-smap
  "Defines mappings from UMM records into ISO SMAP XML."
  (:require [cmr.umm-spec.umm-to-xml-mappings.dsl :refer :all]))

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

(defn char-string-from
  "Defines a mapping for a ISO CharacterString element with a value from the given XPath."
  [xpath-str]
  [:gco:CharacterString (xpath xpath-str)])

(def umm-c-to-iso-smap-xml
  [:gmd:DS_Series
   iso-smap-xml-namespaces
   [:gmd:seriesMetadata
    [:gmi:MI_Metadata
     [:gmd:identificationInfo
      [:gmd:MD_DataIdentification
       [:gmd:citation
        [:gmd:CI_Citation
         [:gmd:identifier
          [:gmd:MD_Identifier
           [:gmd:code (char-string-from "/EntryId/Id")]
           [:gmd:description [:gco:CharacterString "The ECS Short Name"]]]]]]]]

     [:gmd:identificationInfo
      [:gmd:MD_DataIdentification
       [:gmd:citation
        [:gmd:CI_Citation
         [:gmd:title [:gco:CharacterString "DataSetId"]]]]
       [:gmd:aggregationInfo
        [:gmd:MD_AggregateInformation
         [:gmd:aggregateDataSetIdentifier
          [:gmd:MD_Identifier
           [:gmd:code (char-string-from "/EntryTitle")]]]]]]]]]])


