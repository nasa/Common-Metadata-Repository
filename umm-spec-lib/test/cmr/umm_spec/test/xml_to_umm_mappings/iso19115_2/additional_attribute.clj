(ns cmr.umm-spec.test.xml-to-umm-mappings.iso19115-2.additional-attribute
  (:require
   [clojure.test :refer :all]
   [cmr.umm-spec.util :as spec-util]
   [cmr.common.util :as util]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.additional-attribute :as aa]))

(def example-iso-additional-attributes-xml
  "<gmi:MI_Metadata xmlns:gmi=\"http://www.isotc211.org/2005/gmi\"
    xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\"
    xmlns:gmx=\"http://www.isotc211.org/2005/gmx\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\"
    xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\"
    xmlns:srv=\"http://www.isotc211.org/2005/srv\" xmlns:gml=\"http://www.opengis.net/gml/3.2\"
    xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
    xmlns:eos=\"http://earthdata.nasa.gov/schema/eos\"
    xsi:schemaLocation=\"http://earthdata.nasa.gov/schema/eos https://cdn.earthdata.nasa.gov/iso/eos/1.0/eos.xsd\">
    <gmd:dataQualityInfo>
      <gmd:DQ_DataQuality>
        <gmd:lineage>
          <gmd:LI_Lineage>
            <gmd:processStep>
              <gmi:LE_ProcessStep>
                <gmd:description gco:nilReason=\"missing\"/>
                <gmi:processingInformation>
                  <eos:EOS_Processing>
                    <gmi:identifier/>
                    <eos:otherPropertyType/>
                    <eos:otherProperty>
                      <gco:Record>
                        <eos:AdditionalAttributes>
                          <eos:AdditionalAttribute>
                            <eos:reference>
                              <eos:EOS_AdditionalAttributeDescription>
                                <eos:type>
                                  <eos:EOS_AdditionalAttributeTypeCode codeList=\"http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeTypeCode\" codeListValue=\"processingInformation\">processingInformation                                            </eos:EOS_AdditionalAttributeTypeCode>
                                </eos:type>
                                <eos:name>
                                  <gco:CharacterString>SIPSMetGenVersion</gco:CharacterString>
                                </eos:name>
                                <eos:description>
                                  <gco:CharacterString>The version of the SIPSMetGen software used to produce the metadata file for this                                                granule                                            </gco:CharacterString>
                                </eos:description>
                                <eos:dataType>
                                  <eos:EOS_AdditionalAttributeDataTypeCode codeList=\"http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeDataTypeCode\" codeListValue=\"STRING\">STRING                                            </eos:EOS_AdditionalAttributeDataTypeCode>
                                </eos:dataType>
                              </eos:EOS_AdditionalAttributeDescription>
                              <eos:EOS_AdditionalAttributeDescription>
                                <eos:type>
                                  <eos:EOS_AdditionalAttributeTypeCode codeList=\"http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeTypeCode\" codeListValue=\"processingInformation\">processingInformation</eos:EOS_AdditionalAttributeTypeCode>
                                </eos:type>
                                <eos:name>
                                  <gco:CharacterString>ThemeID</gco:CharacterString>
                                </eos:name>
                                <eos:description>
                                  <gco:CharacterString>The identifier of the theme under which data are logically grouped</gco:CharacterString>
                                </eos:description>
                                <eos:dataType>
                                  <eos:EOS_AdditionalAttributeDataTypeCode codeList=\"http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeDataTypeCode\" codeListValue=\"STRING\">STRING</eos:EOS_AdditionalAttributeDataTypeCode>
                                </eos:dataType>
                              </eos:EOS_AdditionalAttributeDescription>
                            </eos:reference>
                          </eos:AdditionalAttribute>
                          <eos:AdditionalAttribute>
                            <eos:reference>
                              <eos:EOS_AdditionalAttributeDescription>
                                <eos:type>
                                  <eos:EOS_AdditionalAttributeTypeCode codeList=\"http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeTypeCode\" codeListValue=\"processingInformation\">processingInformation</eos:EOS_AdditionalAttributeTypeCode>
                                </eos:type>
                                <eos:name>
                                  <gco:CharacterString>AircraftID</gco:CharacterString>
                                </eos:name>
                                <eos:description>
                                  <gco:CharacterString>The identifier of the airplane used by the FAA to uniquely identify each aircraft</gco:CharacterString>
                                </eos:description>
                                <eos:dataType>
                                  <eos:EOS_AdditionalAttributeDataTypeCode codeList=\"http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeDataTypeCode\" codeListValue=\"STRING\">STRING</eos:EOS_AdditionalAttributeDataTypeCode>
                                </eos:dataType>
                              </eos:EOS_AdditionalAttributeDescription>
                            </eos:reference>
                          </eos:AdditionalAttribute>
                        </eos:AdditionalAttributes>
                      </gco:Record>
                    </eos:otherProperty>
                  </eos:EOS_Processing>
                </gmi:processingInformation>
              </gmi:LE_ProcessStep>
            </gmd:processStep>
          </gmd:LI_Lineage>
        </gmd:lineage>
      </gmd:DQ_DataQuality>
     </gmd:dataQualityInfo>
    </gmi:MI_Metadata>")

(def expected-parsed-additional-attributes
  [{:Name "SIPSMetGenVersion"
    :Description "The version of the SIPSMetGen software used to produce the metadata file for this                                                granule                                            "
    :DataType "STRING"}
   {:Name "ThemeID"
    :Description "The identifier of the theme under which data are logically grouped"
    :DataType "STRING"}
   {:Name "AircraftID"
    :Description "The identifier of the airplane used by the FAA to uniquely identify each aircraft"
    :DataType "STRING"}])

(deftest iso-metadata-additional-attributes-test
  (testing (str "Parse additional attributes from dataQualityInfo where there are multiple "
                "eos:EOS_AdditionalAttributeDescription under eos:reference.")
    ;; Note this should be invalid according to xml schema, but xml validation is not catching
    ;; the error. Here we just parse out all the descriptions as additional attributes
    (is (= expected-parsed-additional-attributes
           (map util/remove-nil-keys
                (#'aa/parse-data-quality-info-additional-attributes
                  example-iso-additional-attributes-xml
                  (:sanitize? spec-util/default-parsing-options)))))))
