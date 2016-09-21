(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2.additional-attribute
  "Functions for generating ISO19115-2 XML elements from UMM additional attribute records."
  (:require
   [cmr.common.xml.gen :refer :all]
   [cmr.umm-spec.iso19115-2-util :as iso]
   [cmr.umm-spec.util :refer [char-string]]))

(def data-quality-info-attributes
  "Defines the additional attributes that should go to dataQualityInfo section of the ISO xml"
  #{ "AquisitionQuality" "Band10_Available" "Band11_Available" "Band12_Available" "Band13_Available"
    "Band14_Available" "Band1_Available" "Band2_Available" "Band3B_Available" "Band3N_Available"
    "Band4_Available" "Band5_Available" "Band6_Available" "Band6Missing" "Band7_Available"
    "Band8_Available" "Band9_Available" "ImageQualityVcid1" "ImageQualityVcid2" "NDAYS_COMPOSITED"
    "OrbitQuality" "PERCENTSUBSTITUTEBRDFS" "QAFRACTIONGOODQUALITY" "QAFRACTIONNOTPRODUCEDCLOUD"
    "QAFRACTIONNOTPRODUCEDOTHER" "QAFRACTIONOTHERQUALITY" "QualityBand1" "QualityBand2"
    "QualityBand3" "QualityBand4" "QualityBand5" "QualityBand6" "QualityBand7" "QualityBand8"
    "SIPSMetGenVersion"})

(defn- content-info-additional-attributes
  "Returns the additional attributes that should go to contentInfo section of the ISO xml
  from the given list of additional attributes."
  [aas]
  ;; For now, everything other than additional attributes that go to dataQualityInfo goes here
  ;; but things will change once we start implementing CMR-3088
  (seq (remove #(data-quality-info-attributes (:Name %)) aas)))

(defn- data-quality-info-additional-attributes
  "Returns the additional attributes that should go to dataQualityInfo section of the ISO xml
  from the given list of additional attributes."
  [aas]
  (seq (filter #(data-quality-info-attributes (:Name %)) aas)))

(defn- generate-record-additional-attributes
  "Returns the content generator instructions for generating ISO19115 additional attributes
  under gco:Record element."
  [aas code-list-value]
  [:gco:Record
   [:eos:AdditionalAttributes
    (for [aa aas]
      [:eos:AdditionalAttribute
       [:eos:reference
        [:eos:EOS_AdditionalAttributeDescription
         [:eos:type
          [:eos:EOS_AdditionalAttributeTypeCode
           {:codeList (str (:earthdata iso/code-lists) "#EOS_AdditionalAttributeTypeCode")
            :codeListValue code-list-value} code-list-value]]
         [:eos:identifier
          [:gmd:MD_Identifier
           [:gmd:code
            (char-string (:Group aa))]]]
         [:eos:name
          (char-string (:Name aa))]
         [:eos:description
          (char-string (:Description aa))]
         [:eos:dataType
          [:eos:EOS_AdditionalAttributeDataTypeCode
           {:codeList (str (:earthdata iso/code-lists) "#EOS_AdditionalAttributeDataTypeCode")
            :codeListValue (:DataType aa)} (:DataType aa)]]
         [:eos:measurementResolution
          (char-string (:MeasurementResolution aa))]
         [:eos:parameterRangeBegin
          (char-string (:ParameterRangeBegin aa))]
         [:eos:parameterRangeEnd
          (char-string (:ParameterRangeEnd aa))]
         [:eos:parameterUnitsOfMeasure
          (char-string (:ParameterUnitsOfMeasure aa))]
         [:eos:parameterValueAccuracy
          (char-string (:ParameterValueAccuracy aa))]
         [:eos:valueAccuracyExplanation
          (char-string (:ValueAccuracyExplanation aa))]]]
       [:eos:value
        (char-string (:Value aa))]])]])

(defn generate-content-info-additional-attributes
  "Returns the content generator instructions for generating ISO19115 additional attributes."
  [additional-attributes]
  (when-let [aas (content-info-additional-attributes additional-attributes)]
    [:gmd:contentInfo
     [:gmd:MD_CoverageDescription
      [:gmd:attributeDescription {:gco:nilReason "missing"}]
      [:gmd:contentType
       [:gmd:MD_CoverageContentTypeCode
        {:codeList (str (:ngdc iso/code-lists) "#MD_CoverageContentTypeCode")
         :codeListValue "physicalMeasurement"} "physicalMeasurement"]]
      [:gmd:dimension
       [:gmd:MD_Band
        iso/gmd-echo-attributes-info
        [:gmd:otherProperty
         (generate-record-additional-attributes aas "contentInformation")]]]]]))

(defn generate-data-quality-info-additional-attributes
  "Returns the content generator instructions for generating ISO19115 additional attributes.
  It does not generate the whole dataQualityInfo stack, it starts at gmd:processStep."
  [additional-attributes]
  (if-let [aas (data-quality-info-additional-attributes additional-attributes)]
    [:gmd:processStep
     [:gmi:LE_ProcessStep
      [:gmd:description {:gco:nilReason "unknown"}]
      [:gmi:processingInformation
       [:eos:EOS_Processing
        [:gmi:identifier {:gco:nilReason "unknown"}]
        [:eos:otherProperty
         (generate-record-additional-attributes aas "processingInformation")]]]]]
    ;; default
    [:gmd:processStep
     [:gmi:LE_ProcessStep
      [:gmd:description {:gco:nilReason "unknown"}]]]))
