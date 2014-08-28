<?xml version="1.0" encoding="UTF-8"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" schemaVersion="ISO19757-3" queryBinding="xslt2">
  <sch:ns prefix="gmd" uri="http://www.isotc211.org/2005/gmd"/>
  <sch:ns prefix="gmi" uri="http://www.isotc211.org/2005/gmi"/>
  <sch:ns prefix="gml" uri="http://www.opengis.net/gml/3.2"/>
  <sch:ns prefix="gco" uri="http://www.isotc211.org/2005/gco"/>
  <!--
    SMAP Granular Metadata schematron rules    
    Created by thabermann@hdfgroup.org
    Version 0.0 20140515 
  -->
  <sch:pattern>
    <sch:title>ECS Identifiers</sch:title>
    <sch:p>The ECS ShortName and ECS Version ID must be present</sch:p>
    <sch:rule context="//gmd:DS_DataSet//gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:citation/gmd:CI_Citation">
      <sch:assert test="count(gmd:identifier/gmd:MD_Identifier[gmd:description/gco:CharacterString='The ECS Short Name']) &gt; 0">Provide an ECS ShortName</sch:assert>
      <sch:assert test="count(gmd:identifier/gmd:MD_Identifier[gmd:description/gco:CharacterString='The ECS Version ID']) &gt; 0">Provide an ECS Version ID</sch:assert>
    </sch:rule>
  </sch:pattern>
  <sch:pattern>
    <sch:title>ECS Short Name</sch:title>
    <sch:p>The ECS ShortName must be 8 or fewer characters long, and can contain only upper or lower case letters, digits, underscores or dashes </sch:p>
    <sch:rule context="//gmd:DS_DataSet//gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:citation/gmd:CI_Citation/gmd:identifier/gmd:MD_Identifier[gmd:description/gco:CharacterString='The ECS Short Name']">
      <sch:assert test="string-length(gmd:code/gco:CharacterString) &lt; 9">Make the ECS ShortName 8 or less characters long</sch:assert>
      <sch:assert test="matches(gmd:code/gco:CharacterString,
        '^[\w|-]*$')">The ECS ShortName can only contain upper or lower case letters, digits, underscores or dashes</sch:assert>
    </sch:rule>
  </sch:pattern>
  <sch:pattern>
    <sch:title>ECS Version ID</sch:title>
    <sch:p>The ECS Version ID must be in the range 0 to 255</sch:p>
    <sch:rule context="//gmd:DS_DataSet//gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:citation/gmd:CI_Citation/gmd:identifier/gmd:MD_Identifier[gmd:description/gco:CharacterString='The ECS Version ID']">
      <sch:assert test="number(gmd:code/gco:CharacterString) &gt; -1 and 
        number(gmd:code/gco:CharacterString) &lt; 256">Version ID must be between 0 and 255</sch:assert>
    </sch:rule>
  </sch:pattern>
  <sch:pattern>
    <sch:title>Spatial and Temporal Extent</sch:title>
    <sch:p>The Spatial and Temporal Extent must include a bounding box and beginning and an ending time</sch:p>
    <sch:rule context="//gmd:DS_DataSet//gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:extent/gmd:EX_Extent">
      <sch:assert test="count(gmd:temporalElement/gmd:EX_TemporalExtent/gmd:extent/gml:TimePeriod/gml:beginPosition) &gt; 0">Provide a beginning DateTime</sch:assert>
      <sch:assert test="count(gmd:temporalElement/gmd:EX_TemporalExtent/gmd:extent/gml:TimePeriod/gml:endPosition) &gt; 0">Provide an ending DateTime</sch:assert>
      <sch:assert test="gmd:geographicElement/gmd:EX_GeographicBoundingBox/gmd:westBoundLongitude/gco:Decimal">Add westBoundLongitude to the EX_GeographicBoundingBox</sch:assert>
      <sch:assert test="gmd:geographicElement/gmd:EX_GeographicBoundingBox/gmd:eastBoundLongitude/gco:Decimal">Add eastBoundLongitude to the EX_GeographicBoundingBox</sch:assert>
      <sch:assert test="gmd:geographicElement/gmd:EX_GeographicBoundingBox/gmd:northBoundLatitude/gco:Decimal">Add northBoundLatitude to the EX_GeographicBoundingBox</sch:assert>
      <sch:assert test="gmd:geographicElement/gmd:EX_GeographicBoundingBox/gmd:southBoundLatitude/gco:Decimal">Add southBoundLatitude to the EX_GeographicBoundingBox</sch:assert>
    </sch:rule>
  </sch:pattern>
  <sch:pattern>
    <!-- The standard form for codeLists includes the value as an attribute and as content in the element -->
    <sch:title>Check the form of CodeLists</sch:title>
    <sch:rule context="//*[ends-with(name(./*[1]),'Code') and count(./*)=1 and not(name(.)='gmd:topicCategory')]">
      <sch:assert test="./*[1] = ./*[1]/@codeListValue">[ISOStyle:codeListForm] Make the codeListValue attribute match the content of the codeList element</sch:assert>
      <sch:assert test="local-name(./*[1]) = substring-after(./*[1]/@codeList,'#')">[ISOStyle:codeListName] The codeList name must match the element name</sch:assert>
    </sch:rule>
  </sch:pattern>
</sch:schema>
