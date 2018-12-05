(ns cmr.umm-spec.test.xml-to-umm-mappings.dif10.additional-attribute
  (:require [clj-time.core :as t]
            [clojure.test :refer :all]
            [cmr.umm-spec.util :as u]
            [cmr.umm-spec.additional-attribute :as umm-aa]
            [cmr.umm-spec.models.umm-common-models :as cmn]
            [cmr.umm-spec.xml-to-umm-mappings.dif10.additional-attribute :as aa]))


(def example-dif1
   "<DIF>
       <Extended_Metadata>
         <Metadata>
           <Group>gov.nasa.gsfc.gcmd</Group>
           <Name>metadata.uuid</Name>
           <DataType>STRING</DataType>
           <Value>743933e5-1404-4502-915f-83cde56af440</Value>
         </Metadata>
         <Metadata>
           <Group>gov.nasa.gsfc.gcmd</Group>
           <Name>metadata.extraction_date</Name>
           <Value>2013-09-30 09:45:15</Value>
         </Metadata>
         <Metadata>
           <Group>custom.group.additionalattribute</Group>
           <Name>String attribute</Name>
           <Description>something string</Description>
           <Value>alpha</Value>
         </Metadata>
         <Metadata>
           <Group>custom.group.additionalattribute</Group>
           <Name>Float attribute</Name>
           <Description>something float</Description>
           <Value>12.3</Value>
         </Metadata>
         <Metadata>
           <Group>custom.group.additionalattribute</Group>
           <Name>Int attribute</Name>
           <Description>something int</Description>
           <Value>42</Value>
         </Metadata>
         <Metadata>
           <Group>custom.group.additionalattribute</Group>
           <Name>Date attribute</Name>
           <Description>something date</Description>
           <Value>2015-09-14</Value>
         </Metadata>
         <Metadata>
           <Group>custom.group.additionalattribute</Group>
           <Name>Datetime attribute</Name>
           <Description>something datetime</Description>
           <Value>2015-09-14T13:01:00Z</Value>
         </Metadata>
         <Metadata>
           <Group>custom.group.additionalattribute</Group>
           <Name>Time attribute</Name>
           <Description>something time</Description>
           <Value>13:01:00Z</Value>
         </Metadata>
         <Metadata>
           <Group>custom.group.additionalattribute</Group>
           <Name>Bool attribute</Name>
           <Description>something bool</Description>
           <Value>false</Value>
         </Metadata>
         <Metadata>
           <Group>gov.nasa.earthdata.cmr</Group>
           <Name>Restriction</Name>
           <Value>1</Value>
         </Metadata>
         <Metadata>
           <Group>gov.nasa.earthdata.cmr</Group>
           <Name>ProcessingLevelId</Name>
           <Value>1</Value>
         </Metadata>
       </Extended_Metadata>
     </DIF>")

(def expected-parsed-additional-attributes1
  [{:Group "custom.group.additionalattribute"
    :Name "String attribute"
    :Description "something string"
    :DataType "STRING"
    :Value "alpha"}
   {:Group "custom.group.additionalattribute"
    :Name "Float attribute"
    :Description "something float"
    :DataType "FLOAT"
    :Value "12.3"}
   {:Group "custom.group.additionalattribute"
    :Name "Int attribute"
    :Description "something int"
    :DataType "INT"
    :Value "42"}
   {:Group "custom.group.additionalattribute"
    :Name "Date attribute"
    :Description "something date"
    :DataType "DATE"
    :Value "2015-09-14"}
   {:Group "custom.group.additionalattribute"
    :Name "Datetime attribute"
    :Description "something datetime"
    :DataType "DATETIME"
    :Value "2015-09-14T13:01:00Z"}
   {:Group "custom.group.additionalattribute"
    :Name "Time attribute"
    :Description "something time"
    :DataType "TIME"
    :Value "13:01:00Z"}
   {:Group "custom.group.additionalattribute"
    :Name "Bool attribute"
    :Description "something bool"
    :DataType "BOOLEAN"
    :Value "false"}])

(def example-dif2
  "<DIF>
      <Additional_Attributes>
        <Name>String add attrib</Name>
        <DataType>STRING</DataType>
        <Description>something string</Description>
        <ParameterRangeBegin>alpha</ParameterRangeBegin>
        <ParameterRangeEnd>bravo</ParameterRangeEnd>
        <Value>alpha1</Value>
      </Additional_Attributes>
      <Additional_Attributes>
        <Name>Float add attrib</Name>
        <DataType>FLOAT</DataType>
        <Description>something float</Description>
        <ParameterRangeBegin>0.1</ParameterRangeBegin>
        <ParameterRangeEnd>100.43</ParameterRangeEnd>
        <Value>12.3</Value>
      </Additional_Attributes>
      <Extended_Metadata>
        <Metadata>
          <Group>gov.nasa.gsfc.gcmd.additionalattribute</Group>
          <Name>metadata.uuid</Name>
          <Value>743933e5-1404-4502-915f-83cde56af440</Value>
        </Metadata>
        <Metadata>
          <Group>gov.nasa.earthdata.cmr</Group>
          <Name>Restriction</Name>
          <Value>1</Value>
        </Metadata>
      </Extended_Metadata>
    </DIF>")

(def expected-parsed-additional-attributes2
  [{:Name "String add attrib"
    :Description "something string"
    :DataType "STRING"
    :ParameterRangeBegin "alpha"
    :ParameterRangeEnd "bravo"
    :Value "alpha1"}
   {:Name "Float add attrib"
    :Description "something float"
    :DataType "FLOAT"
    :ParameterRangeBegin "0.1"
    :ParameterRangeEnd "100.43"
    :Value "12.3"}
   {:Group "gov.nasa.gsfc.gcmd.additionalattribute"
    :Name "metadata.uuid"
    :DataType "STRING"
    :Value "743933e5-1404-4502-915f-83cde56af440"
    :Description "Not provided"}])

(deftest dif10-metadata-additional-attributes-test
  (testing "parse additional attributes from Extended_Metadata"
    (is (= expected-parsed-additional-attributes1
           (aa/xml-elem->AdditionalAttributes
             example-dif1 (:sanitize? u/default-parsing-options)))))

  (testing "parse additional attributes from Additional_Attributes and Extended_Metadata together"
    (is (= expected-parsed-additional-attributes2
           (aa/xml-elem->AdditionalAttributes
            example-dif2 (:sanitize? u/default-parsing-options))))))
