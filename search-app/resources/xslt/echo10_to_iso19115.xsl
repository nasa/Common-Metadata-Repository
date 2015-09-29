<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0" xmlns:gmd="http://www.isotc211.org/2005/gmd" xmlns:gco="http://www.isotc211.org/2005/gco" xmlns:gmi="http://www.isotc211.org/2005/gmi" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:swe="http://schemas.opengis.net/sweCommon/2.0/" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:gml="http://www.opengis.net/gml/3.2" xmlns:eos="http://earthdata.nasa.gov/schema/eos">
  <xd:doc xmlns:xd="http://www.oxygenxml.com/ns/doc/xsl" scope="stylesheet">
    <xd:desc>
      <xd:p>
        <xd:b>Title:ECHO to ISO</xd:b>
      </xd:p>
      <xd:p><xd:b>Version:</xd:b>1.30</xd:p>
      <xd:p><xd:b>Created on:</xd:b>October 18, 2014</xd:p>
      <xd:p><xd:b>Author:</xd:b>thabermann@hdfgroup.org</xd:p>
      <xd:p>This stylesheets transforms ECHO collections and granules to ISO</xd:p>
      <xd:p>
        <xd:b>Version 0.2 (January 14, 2013)</xd:b>
      </xd:p>
      <xd:p>The eos namespace is used for additionalAttributes, introduces eos:EOS_AdditionalAttribute</xd:p>
      <xd:p>The additionalAttributes include a type element</xd:p>
      <xd:p>Instrument and Sensor Characteristics are written as additionalAttributes</xd:p>
      <xd:p>19115-1 Element /gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:processingLevel</xd:p>
      <xd:p>19115-1 Element //gmd:MD_Identifier/gmd:description</xd:p>
      <xd:p>contentInfo: No attriute groups</xd:p>
      <xd:p>Added sensorInformation AdditionalAttributes, Characteristic Counts</xd:p>
      <xd:p>
        <xd:b>Version 0.3 (January 15, 2013)</xd:b>
      </xd:p>
      <xd:p>Added ArchiveCenter as Metadata Contact</xd:p>
      <xd:p>Added ArchiveCenter as pointOfContact</xd:p>
      <xd:p>Added GCMD Science Keyword Thesaurus to theme keywords. GCMD Place Keyword Thesaurus is already in spatial keywords.</xd:p>
      <xd:p>Added ArchiveCenter as GCMD Data Center Keywords.</xd:p>
      <xd:p>Added Campaign as GCMD Project Keywords.</xd:p>
      <xd:p>Added Platform as GCMD Platform Keywords.</xd:p>
      <xd:p>Added Instrument as GCMD Instrument Keywords.</xd:p>
      <xd:p>
        <xd:b>Version 0.4 (January 17, 2013)</xd:b>
      </xd:p>
      <xd:p>Adjusted for multiple SingleDateTime in temporal extent</xd:p>
      <xd:p>
        <xd:b>Version 0.5 (February 13, 2013)</xd:b>
      </xd:p>
      <xd:p>Migrated AdditionaAttribute to new eos:19139 structure</xd:p>
      <xd:p>Added gml:id to LineString and corrected structure to lat lon</xd:p>
      <xd:p>changed resolution from gco:distance to gco:Measure</xd:p>
      <xd:p>
        <xd:b>Version 0.6 (February 20, 2013)</xd:b>
      </xd:p>
      <xd:p>Many changes to accomodate granule metadata</xd:p>
      <xd:p>
        <xd:b>Version 0.7 (February 21, 2013)</xd:b>
        <xd:p>Added documentation on collection/granule differences</xd:p>
        <xd:p>Added QAStats and QAFlags to contentInfo sections for each MeasuredParameter</xd:p>
        <xd:p>Changed OtherPropertyType schema to eos.xsd</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 0.8 (February 23, 2013)</xd:b>
        <xd:p>Added MD_Georectified with corner points for TwoDCoordinateSystems</xd:p>
        <xd:p>Added DataSetId as citation title</xd:p>
        <xd:p>Cleaned up collection aggregation identification</xd:p>
        <xd:p>Added Values/Value back into AdditionalAttributes</xd:p>
        <xd:p>Added dayNightFlag as AdditionalAttribute </xd:p>
        <xd:p>Fixed contact for granules (gco:nilReason = inapplicable)</xd:p>
        <xd:p>Fixed abstract for granules (gco:nilReason = inapplicable)</xd:p>
        <xd:p>Added Resource Maintenance for ReprocessingPlanned</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 0.9 (February 26, 2013)</xd:b>
        <xd:p>Added /*/DataGranule/SizeMBDataGranule</xd:p>
        <xd:p>Added /*/DataGranule/LocalVersionId</xd:p>
        <xd:p>Added description to DIF/EntryId</xd:p>
        <xd:p>Added granule representation of TwoDCoordinateSystem (different from Collection representation</xd:p>
        <xd:p>Generalized the bounding extent to handle multiple geometries (for-each instead of choice) </xd:p>
        <xd:p>Added ReferenceSystemIdentifier</xd:p>
        <xd:p>Added DateType</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.0 (March 1, 2013)</xd:b>
        <xd:p>Added PGEName and Version</xd:p>
        <xd:p>Added /*/Spatial/GranuleLocality/LocalityValue as place keyword</xd:p>
        <xd:p>Added Polygon and boundingBox Center points</xd:p>
        <xd:p>Added corrected referenceSystem code</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.01 (March 28, 2013)</xd:b>
        <xd:p>Added /*/Spatial/VerticalSpatialDomain/Type and /Value and /*/Temporal/TemporalRangeType to EX_Extent/description</xd:p>
        <xd:p>Added /*/SpatialInfo/HorizontalCoordinateSystem/GeodeticModel/EllipsoidName (other Geodetic Model elements by reference)</xd:p>
        <xd:p>Added /*/SpatialInfo/VerticalCoordinateSystem/AltitudeSystemDefinition/DatumName (only occurrences = Not Applicable)</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.02 (July 13, 2013)</xd:b>
        <xd:p>Added id="boundingExtent" to EX_Extent</xd:p>
        <xd:p>Added translation documentation to metadataMaintenance</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.03 (July 30, 2013)</xd:b>
        <xd:p>Moved Sensor/LongName to MD_Identifier/description from MD_Identifier/code where it was concatenated with ShortName</xd:p>
        <xd:p>Sensor/MD_Identifier/code now has only Sensor/ShortName</xd:p>
        <xd:p>Checking for empty extent/description to avoid xsl error</xd:p>
        <xd:p>Translate /*/DataFormat | /*/CSDTDescriptions/CSDTDescription/Implementation to /gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:resourceFormat/gmd:MD_Format/gmd:name/gco:CharacterString</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.04 (Aug 12, 2013)</xd:b>
        <xd:p>Adjusted contact to CI_ResponsibleParty translation to accomodate organizations with no people (typical pattern for archives)</xd:p>
        <xd:p>Implemented Role mapping for Contact/Role to CI_ResponsibleParty/CI_RoleCode</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.05 (Aug 13, 2013)</xd:b>
        <xd:p>Added lookup for ECHO /*/OnlineResources/OnlineResource/Type -> CI_OnlineFunctionCode (requires onlineResourceType.xml)</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.06 (Aug 14, 2013)</xd:b>
        <xd:p>Moved Platform LongName to identifier/description</xd:p>
        <xd:p>Added Instrument LongName to identifier/description</xd:p>
        <xd:p>Added Sensor Short and LongName to citation/title</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.07 (Aug 15, 2013)</xd:b>
        <xd:p>Clarified bounding box paths</xd:p>
        <xd:p>Added /*/CSDTDescriptions/CSDTDescription/Implementation to gmd:distributorFormat/gmd:MD_Format/gmd:name/gco:CharacterString</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.08 (Aug 26, 2013)</xd:b>
        <xd:p>Added ECHO User Services role to distribution contact</xd:p>
        <xd:p>Only one distributor contact is allowed, so I dropped the ArchiveCenter as contact if more complete information is available</xd:p>
        <xd:p>Correct namespace for eos:EOS_Sensor/eos:citation</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.09 (Sep.6, 2013)</xd:b>
        <xd:p>Moved Browse and Thumbnail URLs to graphicOverview (left them in distribution section as well)</xd:p>
        <xd:p>Added nilReason for missing contact (granule metadata)</xd:p>
        <xd:p>Added nilReason for missing distributor contact (granule metadata)</xd:p>
        <xd:p>Added multiple distributors</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.10 (Oct 3, 2013)</xd:b>
        <xd:p>Changed LI_Source to LE_Source in InputGranule transform</xd:p>
        <xd:p>Moved CollectionAsociation/VersionId to edition</xd:p>
        <xd:p>Moved PGEName and VersionId to softwareReference</xd:p>
        <xd:p>Added MeasuredParameter quality information (QAFlags and QAStats) to DQ_DataQuality</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.11 (Oct 19, 2013)</xd:b>
        <xd:p>Added placeholder for missing keyword levels</xd:p>
        <xd:p>Added extra distributors where necessary to avoid multiple distributor contacts</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.12 (Nov 6, 2013)</xd:b>
        <xd:p>Added TwoDCoordinateSystems for collections and granules</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.13 (Nov 7, 2013)</xd:b>
        <xd:p>Corrected TwoDCoordinateSystems for collections and granules (error in GranulePaths.xml)</xd:p>
        <xd:p>Removed TwoDCoordinateSystems as spatialRepresentation</xd:p>
        <xd:p>Made some other improvements to geographic identifiers</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.14 (Nov 18, 2013)</xd:b>
        <xd:p>Drop MD_ProgressCode code list for ECHO records that do not have CollectionState</xd:p>
        <xd:p>Only use extensions if they are needed (characteristics or additionalAttributes exist</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.15 (Feb 10, 2014)</xd:b>
        <xd:p>Adjusted conditions in writeGeographicIdentifiers</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.16 (Jan 27, 2014)</xd:b>
        <xd:p>Fixed namespaces in the acquisitionInformation section and adjusted order (gmi before eos)</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.17 (Mar 13, 2014)</xd:b>
        <xd:p>Reverted namespace changes (keeping eos:EOS_Platform/gmi:instrument) and added
          OperationsMode to needAcquisitionExtensions (it is an AdditionalAttribute)</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.18 (Apr 10, 2014)</xd:b>
        <xd:p>Dropped the eos:sensor from EOS_AcquisitionInformation in order to eliminate the need for that element</xd:p>
        <xd:p>This means that eos:EOS_Sensor can only occur as part of eos:EOS_Instrument</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.19 (May 6, 2014)</xd:b>
        <xd:p>Added names as measure names for qualityInformation type additionalAttributes</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.20 (May 15, 2014)</xd:b>
        <xd:p>Corrected omission of DayNightFlag in cases with no contentInformation additionalAttributes</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.21 (May 19, 2014)</xd:b>
        <xd:p>Added inclusion of QAFlags explanations in cases where there are no flag values (usually explanation is not calculated)</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.22 (May 27, 2014)</xd:b>
        <xd:p>Write platform additionalAttributes when there are no platforms</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.23 (June 6, 2014)</xd:b>
        <xd:p>Added CitationForExternalPublication > /gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:citation/gmd:CI_Citation/gmd:otherCitationDetails/gco:CharacterString</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.24 (July 18, 2014)</xd:b>
        <xd:p>Added test for LongName to instrument </xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.25 (July 25, 2014)</xd:b>
        <xd:p>Adjusted comments for platformInformation additionalAttributes</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.26 (Aug 13, 2014)</xd:b>
        <xd:p>Added ExclusiveZones to extent polygons</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.27 (Aug 27, 2014)</xd:b>
        <xd:p>Added detail to spatial geometry descriptions and updated gml types</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.28 (Sept. 5, 2014)</xd:b>
        <xd:p>Cleaned up the vertical extents</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.29 (Sept. 9, 2014)</xd:b>
        <xd:p>Corrected ExclusiveZones</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.30 (Oct. 18, 2014)</xd:b>
        <xd:p>Added citation.date additionalAttributes</xd:p>
        <xd:p>Added citation.website additionalAttributes</xd:p>
        <xd:p>Added citation.date additionalAttribute test case</xd:p>
        <xd:p>Added citation.website additionalAttribute test case</xd:p>
        <xd:p>Added precessingInformation additionalAttribute test case</xd:p>
      </xd:p>
      <xd:p>
        <xd:b>Version 1.31 (Nov. 3, 2014)</xd:b>
        <xd:p>Added distribution.url additionalAttribute to test data</xd:p>
        <xd:p>Added transform for distribution.url</xd:p>
      </xd:p>
    </xd:desc>
  </xd:doc>
  <xsl:variable name="translationName" select="'ECHOToISO.xsl'"/>
  <xsl:variable name="translationVersion" select="'1.31 (Nov. 3, 2014)'"/>
  <xsl:output method="xml" indent="yes"/>
  <xsl:strip-space elements="*"/>
  <xsl:param name="recordType"/>
  <!-- external lookups for additionalAttribute types and onlineResource types -->
  <xsl:key name="additionalAttributeLookup" match="//additionalAttribute" use="@name"/>
  <xsl:key name="onlineResourceTypeLookup" match="//onlineResourceType" use="@name"/>
  <xsl:variable name="additionalAttributeCount" select="count(//AdditionalAttribute)"/>
  <xsl:variable name="contentInformationCount">
    <xsl:value-of select="count(//AdditionalAttribute[key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='contentInformation']) + count(/Granule/DataGranule/DayNightFlag)"/>
  </xsl:variable>
  <xsl:variable name="instrumentInformationCount">
    <xsl:value-of select="count(//AdditionalAttribute[key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='instrumentInformation'])"/>
  </xsl:variable>
  <xsl:variable name="sensorInformationCount">
    <xsl:value-of select="count(//AdditionalAttribute[key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='sensorInformation'])"/>
  </xsl:variable>
  <xsl:variable name="platformInformationCount">
    <xsl:value-of select="count(//AdditionalAttribute[key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='platformInformation'])"/>
  </xsl:variable>
  <xsl:variable name="processingInformationCount">
    <xsl:value-of select="count(//AdditionalAttribute[key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='processingInformation'])"/>
  </xsl:variable>
  <xsl:variable name="qualityInformationCount">
    <xsl:value-of select="count(//AdditionalAttribute[key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='qualityInformation'])"/>
  </xsl:variable>
  <xsl:variable name="geographicIdentifierCount">
    <xsl:value-of select="count(//AdditionalAttribute[key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='geographicIdentifier'])"/>
  </xsl:variable>
  <xsl:variable name="citation.identifierCount">
    <xsl:value-of select="count(//AdditionalAttribute[key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='citation.identifier'])"/>
  </xsl:variable>
  <xsl:variable name="descriptiveKeywordCount">
    <xsl:value-of select="count(//AdditionalAttribute[key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='descriptiveKeyword'])"/>
  </xsl:variable>
  <xsl:variable name="platformCharacteristicCount" select="count(//Platforms/Platform/Characteristics/Characteristic)"/>
  <xsl:variable name="instrumentCharacteristicCount" select="count(//Instruments/Instrument/Characteristics/Characteristic)"/>
  <xsl:variable name="sensorCharacteristicCount" select="count(//Sensors/Sensor/Characteristics/Characteristic)"/>
  <xsl:variable name="needAcquisitionExtensions" select="$platformCharacteristicCount+$platformInformationCount+
    $instrumentCharacteristicCount+$instrumentInformationCount+
    $sensorCharacteristicCount+$sensorInformationCount+count(//Sensors/Sensor)
    +count(//Instrument/OperationModes/OperationMode)"/>
  <xsl:template match="/">
    <!-- metadataScope is determined from the root element -->
    <xsl:variable name="metadataScope">
      <xsl:choose>
        <xsl:when test="/Collection">
          <xsl:value-of select="'series'"/>
        </xsl:when>
        <xsl:when test="/Granule">
          <xsl:value-of select="'dataset'"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="'unknown'"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <gmi:MI_Metadata xmlns:gmi="http://www.isotc211.org/2005/gmi" xmlns:gco="http://www.isotc211.org/2005/gco" xmlns:gmd="http://www.isotc211.org/2005/gmd" xmlns:gmx="http://www.isotc211.org/2005/gmx" xmlns:gsr="http://www.isotc211.org/2005/gsr" xmlns:gss="http://www.isotc211.org/2005/gss" xmlns:gts="http://www.isotc211.org/2005/gts" xmlns:srv="http://www.isotc211.org/2005/srv" xmlns:gml="http://www.opengis.net/gml/3.2" xmlns:xlink="http://www.w3.org/1999/xlink"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:swe="http://schemas.opengis.net/sweCommon/2.0/" xmlns:eos="http://earthdata.nasa.gov/schema/eos">
      <xsl:comment>
        <xsl:value-of select="concat('Other Properties',', all:',$additionalAttributeCount,', coi:',$contentInformationCount,',ii:',$instrumentInformationCount,',si:',$sensorInformationCount,',pli:',$platformInformationCount,',pri:',$processingInformationCount,',qi:',$qualityInformationCount,',gi:',$geographicIdentifierCount,',ci:',$citation.identifierCount,',dk:',$descriptiveKeywordCount,',pcc:',$platformCharacteristicCount,',icc:',$instrumentCharacteristicCount,',scc:',$sensorCharacteristicCount)"/>
      </xsl:comment>
      <gmd:fileIdentifier>
        <gco:CharacterString>gov.nasa.echo:<xsl:value-of select="/*/DataSetId | /*/GranuleUR"/></gco:CharacterString>
      </gmd:fileIdentifier>
      <gmd:language>
        <gco:CharacterString>eng</gco:CharacterString>
      </gmd:language>
      <gmd:characterSet>
        <xsl:call-template name="writeCodelist">
          <xsl:with-param name="codeListName" select="'MD_CharacterSetCode'"/>
          <xsl:with-param name="codeListValue" select="'utf8'"/>
        </xsl:call-template>
      </gmd:characterSet>
      <gmd:hierarchyLevel>
        <xsl:call-template name="writeCodelist">
          <xsl:with-param name="codeListName" select="'MD_ScopeCode'"/>
          <xsl:with-param name="codeListValue" select="$metadataScope"/>
        </xsl:call-template>
      </gmd:hierarchyLevel>
      <xsl:for-each select="/*/Contacts/Contact[contains(Role,'DIF AUTHOR')]">
        <xsl:call-template name="contact2pointOfContact">
          <xsl:with-param name="roleName" select="'gmd:contact'"/>
          <xsl:with-param name="roleCode" select="'pointOfContact'"/>
        </xsl:call-template>
      </xsl:for-each>
      <xsl:choose>
        <xsl:when test="/*/ArchiveCenter">
          <gmd:contact>
            <gmd:CI_ResponsibleParty>
              <gmd:organisationName>
                <xsl:call-template name="writeCharacterString">
                  <xsl:with-param name="stringToWrite" select="/*/ArchiveCenter"/>
                </xsl:call-template>
              </gmd:organisationName>
              <gmd:role>
                <xsl:call-template name="writeCodelist">
                  <xsl:with-param name="codeListName" select="'CI_RoleCode'"/>
                  <xsl:with-param name="codeListValue" select="'pointOfContact'"/>
                </xsl:call-template>
              </gmd:role>
            </gmd:CI_ResponsibleParty>
          </gmd:contact>
        </xsl:when>
        <xsl:otherwise>
          <gmd:contact gco:nilReason="missing"/>
        </xsl:otherwise>
      </xsl:choose>
      <gmd:dateStamp>
        <gco:DateTime>
          <xsl:choose>
            <xsl:when test="/*/RevisionDate">
              <xsl:value-of select="/*/RevisionDate"/>
            </xsl:when>
            <xsl:otherwise>
              <xsl:value-of select="current-dateTime()"/>
            </xsl:otherwise>
          </xsl:choose>
        </gco:DateTime>
      </gmd:dateStamp>
      <gmd:metadataStandardName>
        <gco:CharacterString>ISO 19115-2 Geographic Information - Metadata Part 2 Extensions for imagery and gridded data</gco:CharacterString>
      </gmd:metadataStandardName>
      <gmd:metadataStandardVersion>
        <gco:CharacterString>ISO 19115-2:2009(E)</gco:CharacterString>
      </gmd:metadataStandardVersion>
      <xsl:for-each select="/*/SpatialInfo/VerticalCoordinateSystem">
        <!-- The SpatialInfo element only has real content in 16 records. They are all the same. -->
        <gmd:spatialRepresentationInfo>
          <gmd:MD_Georeferenceable>
            <gmd:numberOfDimensions>
              <gco:Integer>3</gco:Integer>
            </gmd:numberOfDimensions>
            <gmd:axisDimensionProperties>
              <gmd:MD_Dimension>
                <gmd:dimensionName>
                  <xsl:call-template name="writeCodelist">
                    <xsl:with-param name="codeListName" select="'MD_DimensionNameTypeCode'"/>
                    <xsl:with-param name="codeListValue" select="'column'"/>
                  </xsl:call-template>
                </gmd:dimensionName>
                <gmd:dimensionSize gco:nilReason="unknown"/>
                <gmd:resolution>
                  <xsl:element name="gco:Measure">
                    <xsl:attribute name="uom">
                      <xsl:value-of select="encode-for-uri(../HorizontalCoordinateSystem/GeographicCoordinateSystem/GeographicCoordinateUnits)"/>
                    </xsl:attribute>
                    <xsl:value-of select="../HorizontalCoordinateSystem/GeographicCoordinateSystem/LongitudeResolution"/>
                  </xsl:element>
                </gmd:resolution>
              </gmd:MD_Dimension>
            </gmd:axisDimensionProperties>
            <gmd:axisDimensionProperties>
              <gmd:MD_Dimension>
                <gmd:dimensionName>
                  <xsl:call-template name="writeCodelist">
                    <xsl:with-param name="codeListName" select="'MD_DimensionNameTypeCode'"/>
                    <xsl:with-param name="codeListValue" select="'row'"/>
                  </xsl:call-template>
                </gmd:dimensionName>
                <gmd:dimensionSize gco:nilReason="unknown"/>
                <gmd:resolution>
                  <xsl:element name="gco:Measure">
                    <xsl:attribute name="uom">
                      <xsl:value-of select="encode-for-uri(../HorizontalCoordinateSystem/GeographicCoordinateSystem/GeographicCoordinateUnits)"/>
                    </xsl:attribute>
                    <xsl:value-of select="../HorizontalCoordinateSystem/GeographicCoordinateSystem/LatitudeResolution"/>
                  </xsl:element>
                </gmd:resolution>
              </gmd:MD_Dimension>
            </gmd:axisDimensionProperties>
            <gmd:axisDimensionProperties>
              <gmd:MD_Dimension>
                <gmd:dimensionName>
                  <xsl:call-template name="writeCodelist">
                    <xsl:with-param name="codeListName" select="'MD_DimensionNameTypeCode'"/>
                    <xsl:with-param name="codeListValue" select="'vertical'"/>
                  </xsl:call-template>
                </gmd:dimensionName>
                <gmd:dimensionSize gco:nilReason="unknown"/>
                <gmd:resolution>
                  <xsl:element name="gco:Measure">
                    <xsl:attribute name="uom">
                      <xsl:value-of select="encode-for-uri(AltitudeSystemDefinition/DistanceUnits)"/>
                    </xsl:attribute>
                    <xsl:value-of select="AltitudeSystemDefinition/Resolutions/Resolution"/>
                  </xsl:element>
                </gmd:resolution>
              </gmd:MD_Dimension>
            </gmd:axisDimensionProperties>
            <gmd:cellGeometry/>
            <gmd:transformationParameterAvailability/>
            <gmd:controlPointAvailability/>
            <gmd:orientationParameterAvailability/>
            <gmd:georeferencedParameters/>
          </gmd:MD_Georeferenceable>
        </gmd:spatialRepresentationInfo>
      </xsl:for-each>
      <xsl:if test="/*/Spatial/HorizontalSpatialDomain/Geometry/CoordinateSystem">
        <gmd:referenceSystemInfo>
          <gmd:MD_ReferenceSystem>
            <gmd:referenceSystemIdentifier>
              <gmd:RS_Identifier>
                <gmd:code>
                  <gco:CharacterString>
                    <xsl:value-of select="/*/Spatial/HorizontalSpatialDomain/Geometry/CoordinateSystem"/>
                  </gco:CharacterString>
                </gmd:code>
              </gmd:RS_Identifier>
            </gmd:referenceSystemIdentifier>
          </gmd:MD_ReferenceSystem>
        </gmd:referenceSystemInfo>
      </xsl:if>
      <xsl:if test="/*/SpatialInfo/HorizontalCoordinateSystem/GeodeticModel/EllipsoidName">
        <gmd:referenceSystemInfo>
          <gmd:MD_ReferenceSystem>
            <gmd:referenceSystemIdentifier>
              <gmd:RS_Identifier>
                <gmd:code>
                  <gco:CharacterString>
                    <xsl:value-of select="/*/SpatialInfo/HorizontalCoordinateSystem/GeodeticModel/EllipsoidName"/>
                  </gco:CharacterString>
                </gmd:code>
              </gmd:RS_Identifier>
            </gmd:referenceSystemIdentifier>
          </gmd:MD_ReferenceSystem>
        </gmd:referenceSystemInfo>
      </xsl:if>
      <gmd:identificationInfo>
        <gmd:MD_DataIdentification>
          <gmd:citation>
            <gmd:CI_Citation>
              <gmd:title>
                <xsl:choose>
                  <xsl:when test="/*/ShortName | /*/LongName">
                    <!-- Collection Record -->
                    <xsl:call-template name="writeCharacterString">
                      <xsl:with-param name="stringToWrite">
                        <xsl:value-of select="/*/ShortName"/>
                        <xsl:if test="/*/LongName">
                          <xsl:value-of select="concat(' &gt; ',/*/LongName)"/>
                        </xsl:if>
                      </xsl:with-param>
                    </xsl:call-template>
                  </xsl:when>
                  <xsl:when test="/*/Collection/ShortName">
                    <!-- Granule Record -->
                    <xsl:call-template name="writeCharacterString">
                      <xsl:with-param name="stringToWrite">
                        <xsl:value-of select="/*/Collection/ShortName"/>
                      </xsl:with-param>
                    </xsl:call-template>
                  </xsl:when>
                  <xsl:when test="/*/Collection/DataSetId">
                    <!-- Granule Record -->
                    <xsl:call-template name="writeCharacterString">
                      <xsl:with-param name="stringToWrite">
                        <xsl:value-of select="/*/Collection/DataSetId"/>
                      </xsl:with-param>
                    </xsl:call-template>
                  </xsl:when>
                  <xsl:otherwise>
                    <!-- No title -->
                    <xsl:attribute name="gco:nilReason" select="'missing'"/>
                  </xsl:otherwise>
                </xsl:choose>
              </gmd:title>
              <gmd:date>
                <gmd:CI_Date>
                  <gmd:date>
                    <gco:DateTime>
                      <xsl:value-of select="/*/LastUpdate"/>
                    </gco:DateTime>
                  </gmd:date>
                  <gmd:dateType>
                    <xsl:call-template name="writeCodelist">
                      <xsl:with-param name="codeListName" select="'CI_DateTypeCode'"/>
                      <xsl:with-param name="codeListValue" select="'revision'"/>
                    </xsl:call-template>
                  </gmd:dateType>
                </gmd:CI_Date>
              </gmd:date>
              <gmd:date>
                <gmd:CI_Date>
                  <gmd:date>
                    <gco:DateTime>
                      <xsl:value-of select="/*/InsertTime"/>
                    </gco:DateTime>
                  </gmd:date>
                  <gmd:dateType>
                    <xsl:call-template name="writeCodelist">
                      <xsl:with-param name="codeListName" select="'CI_DateTypeCode'"/>
                      <xsl:with-param name="codeListValue" select="'creation'"/>
                    </xsl:call-template>
                  </gmd:dateType>
                </gmd:CI_Date>
              </gmd:date>
              <xsl:for-each select="/*/AdditionalAttributes/AdditionalAttribute">
                <xsl:if test="key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='citation.date'">
                  <xsl:choose>
                    <xsl:when test="Values/Value | Value">
                      <xsl:for-each select="Values/Value | Value">
                        <gmd:date>
                          <gmd:CI_Date>
                            <gmd:date>
                              <gco:DateTime>
                                <xsl:value-of select="."/>
                              </gco:DateTime>
                            </gmd:date>
                            <gmd:dateType>
                              <xsl:call-template name="writeCodelist">
                                <xsl:with-param name="codeListName" select="'CI_DateTypeCode'"/>
                                <xsl:with-param name="codeListValue" select="ancestor::AdditionalAttribute/Name"/>
                              </xsl:call-template>
                            </gmd:dateType>
                          </gmd:CI_Date>
                        </gmd:date>
                      </xsl:for-each>
                    </xsl:when>
                    <xsl:otherwise>
                      <!-- date attribute with no value -->
                    </xsl:otherwise>
                  </xsl:choose>
                </xsl:if>
              </xsl:for-each>
              <gmd:edition>
                <xsl:choose>
                  <xsl:when test="/*/Collection/VersionId">
                    <!-- Granule Record -->
                    <xsl:call-template name="writeCharacterString">
                      <xsl:with-param name="stringToWrite" select="/*/Collection/VersionId"/>
                    </xsl:call-template>
                  </xsl:when>
                  <xsl:when test="/*/VersionId">
                    <!-- Collection -->
                    <xsl:call-template name="writeCharacterString">
                      <xsl:with-param name="stringToWrite" select="/*/VersionId"/>
                    </xsl:call-template>
                  </xsl:when>
                </xsl:choose>
              </gmd:edition>
              <gmd:identifier>
                <gmd:MD_Identifier>
                  <!-- The first identifier is GranuleUR or ShortName > LongName -->
                  <xsl:choose>
                    <xsl:when test="/*/GranuleUR">
                      <!-- Granule Record -->
                      <gmd:code>
                        <xsl:call-template name="writeCharacterString">
                          <xsl:with-param name="stringToWrite" select="/*/GranuleUR"/>
                        </xsl:call-template>
                      </gmd:code>
                    </xsl:when>
                    <xsl:when test="/*/Collection/ShortName">
                      <!-- Granule Record with no GranuleUR-->
                      <gmd:code>
                        <xsl:call-template name="writeCharacterString">
                          <xsl:with-param name="stringToWrite" select="/*/Collection/ShortName"/>
                        </xsl:call-template>
                      </gmd:code>
                    </xsl:when>
                    <xsl:when test="/*/ShortName | /*/LongName">
                      <!-- Collection Record -->
                      <gmd:code>
                        <xsl:call-template name="writeCharacterString">
                          <xsl:with-param name="stringToWrite" select="concat(/*/ShortName,'_', /*/VersionId)"/>
                        </xsl:call-template>
                      </gmd:code>
                      <gmd:description>
                        <xsl:call-template name="writeCharacterString">
                          <xsl:with-param name="stringToWrite" select="/*/LongName"/>
                        </xsl:call-template>
                      </gmd:description>
                    </xsl:when>
                  </xsl:choose>
                </gmd:MD_Identifier>
              </gmd:identifier>
              <xsl:if test="/Granule/DataGranule/ProducerGranuleId">
                <gmd:identifier>
                  <gmd:MD_Identifier>
                    <gmd:code>
                      <xsl:call-template name="writeCharacterString">
                        <xsl:with-param name="stringToWrite" select="/Granule/DataGranule/ProducerGranuleId"/>
                      </xsl:call-template>
                    </gmd:code>
                    <gmd:description>
                      <gco:CharacterString>ProducerGranuleId</gco:CharacterString>
                    </gmd:description>
                  </gmd:MD_Identifier>
                </gmd:identifier>
              </xsl:if>
              <xsl:if test="/Granule/DataGranule/LocalVersionId">
                <gmd:identifier>
                  <gmd:MD_Identifier>
                    <gmd:code>
                      <xsl:call-template name="writeCharacterString">
                        <xsl:with-param name="stringToWrite" select="/Granule/DataGranule/LocalVersionId"/>
                      </xsl:call-template>
                    </gmd:code>
                    <gmd:description>
                      <gco:CharacterString>LocalVersionId</gco:CharacterString>
                    </gmd:description>
                  </gmd:MD_Identifier>
                </gmd:identifier>
              </xsl:if>
              <xsl:for-each select="/*/AssociatedDIFs/DIF/EntryId">
                <gmd:identifier>
                  <gmd:MD_Identifier>
                    <!-- Third identifier is DIF EntryId from gcmd namespace-->
                    <gmd:code>
                      <xsl:call-template name="writeCharacterString">
                        <xsl:with-param name="stringToWrite" select="."/>
                      </xsl:call-template>
                    </gmd:code>
                    <gmd:codeSpace>
                      <xsl:call-template name="writeCharacterString">
                        <xsl:with-param name="stringToWrite" select="'gov.nasa.gcmd'"/>
                      </xsl:call-template>
                    </gmd:codeSpace>
                    <gmd:description>
                      <gco:CharacterString>DIFEntryId</gco:CharacterString>
                    </gmd:description>
                  </gmd:MD_Identifier>
                </gmd:identifier>
              </xsl:for-each>
              <xsl:if test="$citation.identifierCount">
                <!-- additionalAttributes with citation.identifier type -->
                <xsl:for-each select="/*/AdditionalAttributes/AdditionalAttribute">
                  <xsl:if test="key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='citation.identifier'">
                    <xsl:choose>
                      <xsl:when test="Values/Value | Value">
                        <xsl:for-each select="Values/Value | Value">
                          <gmd:identifier>
                            <gmd:MD_Identifier>
                              <gmd:code>
                                <xsl:call-template name="writeCharacterString">
                                  <xsl:with-param name="stringToWrite" select="."/>
                                </xsl:call-template>
                              </gmd:code>
                              <gmd:codeSpace>
                                <xsl:call-template name="writeCharacterString">
                                  <xsl:with-param name="stringToWrite" select="'gov.nasa.echo'"/>
                                </xsl:call-template>
                              </gmd:codeSpace>
                              <gmd:description>
                                <gco:CharacterString>
                                  <xsl:value-of select="../../Name | ../Name"/>
                                  <xsl:if test="../../Description | ../Description">
                                    <xsl:value-of select="concat(' - ',../../Description | ../Description)"/>
                                  </xsl:if>
                                </gco:CharacterString>
                              </gmd:description>
                            </gmd:MD_Identifier>
                          </gmd:identifier>
                        </xsl:for-each>
                      </xsl:when>
                      <xsl:otherwise>
                        <gmd:identifier>
                          <gmd:MD_Identifier>
                            <xsl:choose>
                              <xsl:when test="ParameterRangeBegin | ParameterRangeEnd">
                                <gmd:code>
                                  <gco:CharacterString>
                                    <xsl:value-of select="concat(ParameterRangeBegin,' - ',ParameterRangeEnd)"/>
                                  </gco:CharacterString>
                                </gmd:code>
                              </xsl:when>
                              <xsl:otherwise>
                                <gmd:code gco:nilReason="missing"/>
                              </xsl:otherwise>
                            </xsl:choose>
                            <gmd:codeSpace>
                              <xsl:call-template name="writeCharacterString">
                                <xsl:with-param name="stringToWrite" select="'gov.nasa.echo'"/>
                              </xsl:call-template>
                            </gmd:codeSpace>
                            <gmd:description>
                              <gco:CharacterString>
                                <xsl:value-of select="Name"/>
                                <xsl:if test="Description">
                                  <xsl:value-of select="concat(' - ',Description)"/>
                                </xsl:if>
                              </gco:CharacterString>
                            </gmd:description>
                          </gmd:MD_Identifier>
                        </gmd:identifier>
                      </xsl:otherwise>
                    </xsl:choose>
                  </xsl:if>
                </xsl:for-each>
              </xsl:if>
              <xsl:for-each select="/*/Contacts/Contact[contains(Role,'Data Originator') or contains(Role,'Producer')]">
                <xsl:call-template name="contact2pointOfContact">
                  <xsl:with-param name="roleName" select="'gmd:citedResponsibleParty'"/>
                  <xsl:with-param name="roleCode" select="'originator'"/>
                </xsl:call-template>
              </xsl:for-each>
              <xsl:for-each select="/*/Contacts/Contact[contains(Role,'Investigator') or contains(Role,'INVESTIGATOR')]">
                <xsl:call-template name="contact2pointOfContact">
                  <xsl:with-param name="roleName" select="'gmd:citedResponsibleParty'"/>
                  <xsl:with-param name="roleCode" select="'principalInvestigator'"/>
                </xsl:call-template>
              </xsl:for-each>
              <xsl:for-each select="/*/AdditionalAttributes/AdditionalAttribute">
                <xsl:if test="key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='citation.website'">
                  <xsl:choose>
                    <xsl:when test="Values/Value | Value">
                      <gmd:citedResponsibleParty>
                        <gmd:CI_ResponsibleParty>
                          <gmd:contactInfo>
                            <gmd:CI_Contact>
                              <xsl:for-each select="Values/Value | Value">
                                <gmd:onlineResource>
                                  <gmd:CI_OnlineResource>
                                    <gmd:linkage>
                                      <gmd:URL>
                                        <xsl:value-of select="."/>
                                      </gmd:URL>
                                    </gmd:linkage>
                                    <gmd:function>
                                      <xsl:call-template name="writeCodelist">
                                        <xsl:with-param name="codeListName" select="'CI_OnLineFunctionCode'"/>
                                        <xsl:with-param name="codeListValue" select="'information'"/>
                                      </xsl:call-template>
                                    </gmd:function>
                                  </gmd:CI_OnlineResource>
                                </gmd:onlineResource>
                              </xsl:for-each>
                            </gmd:CI_Contact>
                          </gmd:contactInfo>
                          <gmd:role>
                            <xsl:call-template name="writeCodelist">
                              <xsl:with-param name="codeListName" select="'CI_RoleCode'"/>
                              <xsl:with-param name="codeListValue" select="'pointOfContact'"/>
                            </xsl:call-template>
                          </gmd:role>
                        </gmd:CI_ResponsibleParty>
                      </gmd:citedResponsibleParty>
                    </xsl:when>
                    <xsl:otherwise>
                      <!-- scienceTeamWebsite attribute with no value -->
                    </xsl:otherwise>
                  </xsl:choose>
                </xsl:if>
              </xsl:for-each>
              <gmd:otherCitationDetails>
                <xsl:call-template name="writeCharacterString">
                  <xsl:with-param name="stringToWrite">
                    <xsl:value-of select="/*/CitationForExternalPublication"/>
                  </xsl:with-param>
                </xsl:call-template>
              </gmd:otherCitationDetails>
            </gmd:CI_Citation>
          </gmd:citation>
          <!-- abstract is a required element, will be gco:nilReason in granules -->
          <gmd:abstract>
            <xsl:choose>
              <xsl:when test="/*/Description">
                <gco:CharacterString>
                  <xsl:value-of select="/*/Description"/>
                  <xsl:if test="/*/VersionDescription">
                    <xsl:value-of select="concat(' Version Description: ',/*/VersionDescription)"/>
                  </xsl:if>
                </gco:CharacterString>
              </xsl:when>
              <xsl:otherwise>
                <xsl:attribute name="gco:nilReason" select="'inapplicable'"/>
              </xsl:otherwise>
            </xsl:choose>
          </gmd:abstract>
          <!-- Elements that occur only in series -->
          <gmd:purpose>
            <xsl:call-template name="writeCharacterString">
              <xsl:with-param name="stringToWrite" select="/*/SuggestedUsage"/>
            </xsl:call-template>
          </gmd:purpose>
          <xsl:for-each select="/*/CollectionState">
            <gmd:status>
              <xsl:call-template name="writeCodelist">
                <xsl:with-param name="codeListName" select="'MD_ProgressCode'"/>
                <xsl:with-param name="codeListValue" select="."/>
              </xsl:call-template>
            </gmd:status>
          </xsl:for-each>
          <!-- ArchiveCenter is used as pointOfContact and distributor -->
          <xsl:if test="/*/ArchiveCenter">
            <gmd:pointOfContact>
              <gmd:CI_ResponsibleParty>
                <gmd:organisationName>
                  <xsl:call-template name="writeCharacterString">
                    <xsl:with-param name="stringToWrite" select="/*/ArchiveCenter"/>
                  </xsl:call-template>
                </gmd:organisationName>
                <gmd:role>
                  <xsl:call-template name="writeCodelist">
                    <xsl:with-param name="codeListName" select="'CI_RoleCode'"/>
                    <xsl:with-param name="codeListValue" select="'pointOfContact'"/>
                  </xsl:call-template>
                </gmd:role>
              </gmd:CI_ResponsibleParty>
            </gmd:pointOfContact>
          </xsl:if>
          <xsl:for-each select="/*/Contacts/Contact[contains(Role,'TECHNICAL CONTACT')]">
            <xsl:call-template name="contact2pointOfContact">
              <xsl:with-param name="roleName" select="'gmd:pointOfContact'"/>
              <xsl:with-param name="roleCode" select="'pointOfContact'"/>
            </xsl:call-template>
          </xsl:for-each>
          <xsl:if test="/*/MaintenanceAndUpdateFrequency or /*/DataGranule/ReprocessingPlanned">
            <gmd:resourceMaintenance>
              <gmd:MD_MaintenanceInformation>
                <gmd:maintenanceAndUpdateFrequency>
                  <xsl:choose>
                    <xsl:when test="/*/MaintenanceAndUpdateFrequency">
                      <xsl:call-template name="writeCodelist">
                        <xsl:with-param name="codeListName" select="'MD_MaintenanceFrequencyCode'"/>
                        <xsl:with-param name="codeListValue" select="/*/MaintenanceAndUpdateFrequency"/>
                      </xsl:call-template>
                    </xsl:when>
                    <xsl:otherwise>
                      <xsl:attribute name="gco:nilReason" select="'missing'"/>
                    </xsl:otherwise>
                  </xsl:choose>
                </gmd:maintenanceAndUpdateFrequency>
                <xsl:if test="/*/DataGranule/ReprocessingPlanned">
                  <gmd:maintenanceNote>
                    <gco:CharacterString>
                      <xsl:value-of select="/*/DataGranule/ReprocessingPlanned"/>
                    </gco:CharacterString>
                  </gmd:maintenanceNote>
                </xsl:if>
              </gmd:MD_MaintenanceInformation>
            </gmd:resourceMaintenance>
          </xsl:if>
          <!-- end of series-only elements -->
          <xsl:for-each select="/*/AssociatedBrowseImageUrls/ProviderBrowseUrl">
            <gmd:graphicOverview>
              <gmd:MD_BrowseGraphic>
                <gmd:fileName>
                  <xsl:element name="gmx:FileName">
                    <xsl:attribute name="src" select="URL"/>
                  </xsl:element>
                </gmd:fileName>
                <gmd:fileDescription>
                  <xsl:call-template name="writeCharacterString">
                    <xsl:with-param name="stringToWrite" select="concat(Description,' File Size: ',FileSize)"/>
                  </xsl:call-template>
                </gmd:fileDescription>
                <gmd:fileType>
                  <xsl:call-template name="writeCharacterString">
                    <xsl:with-param name="stringToWrite" select="MimeType"/>
                  </xsl:call-template>
                </gmd:fileType>
              </gmd:MD_BrowseGraphic>
            </gmd:graphicOverview>
          </xsl:for-each>
          <xsl:for-each select="//OnlineResources/OnlineResource[Type='Browse' or Type='Thumbnail'] ">
            <gmd:graphicOverview>
              <gmd:MD_BrowseGraphic>
                <gmd:fileName>
                  <xsl:element name="gmx:FileName">
                    <xsl:attribute name="src" select="URL"/>
                  </xsl:element>
                </gmd:fileName>
                <gmd:fileDescription>
                  <xsl:call-template name="writeCharacterString">
                    <xsl:with-param name="stringToWrite" select="Description"/>
                  </xsl:call-template>
                </gmd:fileDescription>
                <gmd:fileType>
                  <xsl:call-template name="writeCharacterString">
                    <xsl:with-param name="stringToWrite" select="MimeType"/>
                  </xsl:call-template>
                </gmd:fileType>
              </gmd:MD_BrowseGraphic>
            </gmd:graphicOverview>
          </xsl:for-each>
          <xsl:for-each select="/*/DataFormat | /*/CSDTDescriptions/CSDTDescription/Implementation">
            <gmd:resourceFormat>
              <gmd:MD_Format>
                <gmd:name>
                  <xsl:call-template name="writeCharacterString">
                    <xsl:with-param name="stringToWrite" select="."/>
                  </xsl:call-template>
                </gmd:name>
                <xsl:choose>
                  <xsl:when test="contains(.,'4')">
                    <gmd:version>
                      <gco:CharacterString>4</gco:CharacterString>
                    </gmd:version>
                  </xsl:when>
                  <xsl:when test="contains(.,'5')">
                    <gmd:version>
                      <gco:CharacterString>5</gco:CharacterString>
                    </gmd:version>
                  </xsl:when>
                  <xsl:otherwise>
                    <gmd:version gco:nilReason="unknown"/>
                  </xsl:otherwise>
                </xsl:choose>
              </gmd:MD_Format>
            </gmd:resourceFormat>
          </xsl:for-each>
          <xsl:for-each select="/*/AdditionalAttributes/AdditionalAttribute[Name='FileFormat']/Value">
            <!-- Additional Attribute fileFormat is in 2 collections  -->
            <gmd:resourceFormat>
              <gmd:MD_Format>
                <gmd:name>
                  <xsl:call-template name="writeCharacterString">
                    <xsl:with-param name="stringToWrite" select="Value"/>
                  </xsl:call-template>
                </gmd:name>
                <xsl:choose>
                  <xsl:when test="contains(Value,'4')">
                    <gmd:version>
                      <gco:CharacterString>4</gco:CharacterString>
                    </gmd:version>
                  </xsl:when>
                  <xsl:when test="contains(Value,'5')">
                    <gmd:version>
                      <gco:CharacterString>5</gco:CharacterString>
                    </gmd:version>
                  </xsl:when>
                  <xsl:otherwise>
                    <gmd:version gco:nilReason="unknown"/>
                  </xsl:otherwise>
                </xsl:choose>
              </gmd:MD_Format>
            </gmd:resourceFormat>
          </xsl:for-each>
          <!-- Additional Attribute Flags -->
          <xsl:if test="$descriptiveKeywordCount > 0">
            <gmd:descriptiveKeywords>
              <gmd:MD_Keywords>
                <xsl:for-each select="/*/AdditionalAttributes/AdditionalAttribute">
                  <xsl:if test="key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='descriptiveKeyword'">
                    <xsl:choose>
                      <xsl:when test="Values/Value | Value">
                        <xsl:for-each select="Values/Value | Value">
                          <gmd:keyword>
                            <gco:CharacterString>
                              <xsl:value-of select="concat(../../Name | ../Name,': ',.)"/>
                            </gco:CharacterString>
                          </gmd:keyword>
                        </xsl:for-each>
                      </xsl:when>
                      <xsl:otherwise>
                        <gmd:keyword>
                          <gco:CharacterString>
                            <xsl:value-of select="../../Name | ../Name"/>
                          </gco:CharacterString>
                        </gmd:keyword>
                      </xsl:otherwise>
                    </xsl:choose>
                  </xsl:if>
                </xsl:for-each>
                <gmd:thesaurusName>
                  <gmd:CI_Citation>
                    <gmd:title>
                      <gco:CharacterString>NASA ECHO Additional Attributes as descriptiveKeywords</gco:CharacterString>
                    </gmd:title>
                    <gmd:date gco:nilReason="inapplicable"/>
                  </gmd:CI_Citation>
                </gmd:thesaurusName>
              </gmd:MD_Keywords>
            </gmd:descriptiveKeywords>
          </xsl:if>
          <!-- The various kinds of keywords only occur in collection records -->
          <!-- Science Keywords -->
          <xsl:if test="/*/ScienceKeywords/ScienceKeyword">
            <gmd:descriptiveKeywords>
              <gmd:MD_Keywords>
                <xsl:for-each select="/*/ScienceKeywords/ScienceKeyword">
                  <gmd:keyword>
                    <gco:CharacterString>
                      <xsl:choose>
                        <xsl:when test="CategoryKeyword">
                          <xsl:value-of select="CategoryKeyword"/>
                        </xsl:when>
                        <xsl:otherwise>
                          <xsl:value-of select="NONE"/>
                        </xsl:otherwise>
                      </xsl:choose>
                      <xsl:choose>
                        <xsl:when test="TopicKeyword">
                          <xsl:value-of select="concat('&gt;',TopicKeyword)"/>
                        </xsl:when>
                        <xsl:otherwise>
                          <xsl:value-of select="'&gt;NONE'"/>
                        </xsl:otherwise>
                      </xsl:choose>
                      <xsl:choose>
                        <xsl:when test="TermKeyword">
                          <xsl:value-of select="concat('&gt;',TermKeyword)"/>
                        </xsl:when>
                        <xsl:otherwise>
                          <xsl:value-of select="'&gt;NONE'"/>
                        </xsl:otherwise>
                      </xsl:choose>
                      <xsl:choose>
                        <xsl:when test="VariableLevel1Keyword/Value">
                          <xsl:value-of select="concat('&gt;',VariableLevel1Keyword/Value)"/>
                        </xsl:when>
                        <xsl:otherwise>
                          <xsl:value-of select="'&gt;NONE'"/>
                        </xsl:otherwise>
                      </xsl:choose>
                      <xsl:choose>
                        <xsl:when test="VariableLevel1Keyword/VariableLevel2Keyword/Value">
                          <xsl:value-of select="concat('&gt;',VariableLevel1Keyword/VariableLevel2Keyword/Value)"/>
                        </xsl:when>
                        <xsl:otherwise>
                          <xsl:value-of select="'&gt;NONE'"/>
                        </xsl:otherwise>
                      </xsl:choose>
                      <xsl:choose>
                        <xsl:when test="VariableLevel1Keyword/VariableLevel2Keyword/VariableLevel3Keyword">
                          <xsl:value-of select="concat('&gt;',VariableLevel1Keyword/VariableLevel2Keyword/VariableLevel3Keyword)"/>
                        </xsl:when>
                        <xsl:otherwise>
                          <xsl:value-of select="'&gt;NONE'"/>
                        </xsl:otherwise>
                      </xsl:choose>
                      <xsl:choose>
                        <xsl:when test="DetailedVariableKeyword">
                          <xsl:value-of select="concat('&gt;',DetailedVariableKeyword)"/>
                        </xsl:when>
                        <xsl:otherwise>
                          <xsl:value-of select="'&gt;NONE'"/>
                        </xsl:otherwise>
                      </xsl:choose>
                    </gco:CharacterString>
                  </gmd:keyword>
                </xsl:for-each>
                <gmd:type>
                  <xsl:call-template name="writeCodelist">
                    <xsl:with-param name="codeListName" select="'MD_KeywordTypeCode'"/>
                    <xsl:with-param name="codeListValue" select="'theme'"/>
                  </xsl:call-template>
                </gmd:type>
                <gmd:thesaurusName>
                  <gmd:CI_Citation>
                    <gmd:title>
                      <gco:CharacterString>NASA/GCMD Science Keywords</gco:CharacterString>
                    </gmd:title>
                    <gmd:date gco:nilReason="unknown"/>
                    <gmd:citedResponsibleParty>
                      <gmd:CI_ResponsibleParty>
                        <gmd:organisationName>
                          <gco:CharacterString>NASA</gco:CharacterString>
                        </gmd:organisationName>
                        <gmd:positionName>
                          <gco:CharacterString>GCMD User Support Office</gco:CharacterString>
                        </gmd:positionName>
                        <gmd:contactInfo>
                          <gmd:CI_Contact>
                            <gmd:address>
                              <gmd:CI_Address>
                                <gmd:electronicMailAddress>
                                  <gco:CharacterString>gcmduso@gcmd.gsfc.nasa.gov</gco:CharacterString>
                                </gmd:electronicMailAddress>
                              </gmd:CI_Address>
                            </gmd:address>
                            <gmd:onlineResource>
                              <gmd:CI_OnlineResource>
                                <gmd:linkage>
                                  <gmd:URL>http://gcmd.nasa.gov/MailComments/MailComments.jsf?rcpt=gcmduso</gmd:URL>
                                </gmd:linkage>
                                <gmd:name>
                                  <gco:CharacterString>GCMD Feedback Form</gco:CharacterString>
                                </gmd:name>
                                <gmd:description>
                                  <gco:CharacterString>Have a Comment for the GCMD?</gco:CharacterString>
                                </gmd:description>
                                <gmd:function>
                                  <gmd:CI_OnLineFunctionCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode" codeListValue="information">information</gmd:CI_OnLineFunctionCode>
                                </gmd:function>
                              </gmd:CI_OnlineResource>
                            </gmd:onlineResource>
                          </gmd:CI_Contact>
                        </gmd:contactInfo>
                        <gmd:role>
                          <gmd:CI_RoleCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_RoleCode" codeListValue="custodian">custodian</gmd:CI_RoleCode>
                        </gmd:role>
                      </gmd:CI_ResponsibleParty>
                    </gmd:citedResponsibleParty>
                    <gmd:citedResponsibleParty>
                      <gmd:CI_ResponsibleParty>
                        <gmd:organisationName gco:nilReason="inapplicable"/>
                        <gmd:contactInfo>
                          <gmd:CI_Contact>
                            <gmd:onlineResource xlink:title="GCMD Keyword Page">
                              <gmd:CI_OnlineResource>
                                <gmd:linkage>
                                  <gmd:URL>http://gcmd.nasa.gov/Resources/valids/</gmd:URL>
                                </gmd:linkage>
                                <gmd:name>
                                  <gco:CharacterString>GCMD Keywords Page</gco:CharacterString>
                                </gmd:name>
                                <gmd:description>
                                  <gco:CharacterString>This page describes the NASA GCMD Keywords, how to reference those keywords and provides download instructions. </gco:CharacterString>
                                </gmd:description>
                                <gmd:function>
                                  <gmd:CI_OnLineFunctionCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode" codeListValue="information">information</gmd:CI_OnLineFunctionCode>
                                </gmd:function>
                              </gmd:CI_OnlineResource>
                            </gmd:onlineResource>
                          </gmd:CI_Contact>
                        </gmd:contactInfo>
                        <gmd:role>
                          <gmd:CI_RoleCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_RoleCode" codeListValue="custodian">custodian</gmd:CI_RoleCode>
                        </gmd:role>
                      </gmd:CI_ResponsibleParty>
                    </gmd:citedResponsibleParty>
                  </gmd:CI_Citation>
                </gmd:thesaurusName>
              </gmd:MD_Keywords>
            </gmd:descriptiveKeywords>
          </xsl:if>
          <!-- Place Keywords -->
          <xsl:if test="/*/SpatialKeywords/Keyword or /*/Spatial/GranuleLocality/LocalityValue">
            <gmd:descriptiveKeywords>
              <gmd:MD_Keywords>
                <xsl:for-each select="/*/SpatialKeywords/Keyword">
                  <gmd:keyword>
                    <gco:CharacterString>
                      <xsl:value-of select="."/>
                    </gco:CharacterString>
                  </gmd:keyword>
                </xsl:for-each>
                <xsl:for-each select="/*/Spatial/GranuleLocality/LocalityValue">
                  <gmd:keyword>
                    <gco:CharacterString>
                      <xsl:value-of select="."/>
                    </gco:CharacterString>
                  </gmd:keyword>
                </xsl:for-each>
                <gmd:type>
                  <xsl:call-template name="writeCodelist">
                    <xsl:with-param name="codeListName" select="'MD_KeywordTypeCode'"/>
                    <xsl:with-param name="codeListValue" select="'place'"/>
                  </xsl:call-template>
                </gmd:type>
                <xsl:if test="/*/SpatialKeywords/Keyword">
                  <gmd:thesaurusName>
                    <gmd:CI_Citation>
                      <gmd:title>
                        <gco:CharacterString>NASA/Global Change Master Directory (GCMD) Location Keywords</gco:CharacterString>
                      </gmd:title>
                      <gmd:date gco:nilReason="unknown"/>
                      <gmd:citedResponsibleParty>
                        <gmd:CI_ResponsibleParty>
                          <gmd:organisationName>
                            <gco:CharacterString>NASA Global Change Master Directory (GCMD) User Support Office</gco:CharacterString>
                          </gmd:organisationName>
                          <gmd:contactInfo>
                            <gmd:CI_Contact>
                              <gmd:phone gco:nilReason="missing"/>
                              <gmd:address>
                                <gmd:CI_Address>
                                  <gmd:deliveryPoint>
                                    <gco:CharacterString>NASA Global Change Master Directory, Goddard Space Flight Center</gco:CharacterString>
                                  </gmd:deliveryPoint>
                                  <gmd:city>
                                    <gco:CharacterString>Greenbelt</gco:CharacterString>
                                  </gmd:city>
                                  <gmd:administrativeArea>
                                    <gco:CharacterString>MD</gco:CharacterString>
                                  </gmd:administrativeArea>
                                  <gmd:postalCode>
                                    <gco:CharacterString>20771</gco:CharacterString>
                                  </gmd:postalCode>
                                  <gmd:country>
                                    <gco:CharacterString>USA</gco:CharacterString>
                                  </gmd:country>
                                  <gmd:electronicMailAddress>
                                    <gco:CharacterString>gcmduso@gcmd.gsfc.nasa.gov</gco:CharacterString>
                                  </gmd:electronicMailAddress>
                                </gmd:CI_Address>
                              </gmd:address>
                              <gmd:onlineResource>
                                <gmd:CI_OnlineResource>
                                  <gmd:linkage>
                                    <gmd:URL>http://gcmd.nasa.gov/index.html</gmd:URL>
                                  </gmd:linkage>
                                  <gmd:protocol>
                                    <gco:CharacterString>http</gco:CharacterString>
                                  </gmd:protocol>
                                  <gmd:applicationProfile>
                                    <gco:CharacterString>web browser</gco:CharacterString>
                                  </gmd:applicationProfile>
                                  <gmd:name>
                                    <gco:CharacterString>NASA Global Change Master Directory (GCMD)</gco:CharacterString>
                                  </gmd:name>
                                  <gmd:description>
                                    <gco:CharacterString>Home Page</gco:CharacterString>
                                  </gmd:description>
                                  <gmd:function>
                                    <gmd:CI_OnLineFunctionCode codeList="http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode" codeListValue="information">information</gmd:CI_OnLineFunctionCode>
                                  </gmd:function>
                                </gmd:CI_OnlineResource>
                              </gmd:onlineResource>
                              <gmd:contactInstructions>
                                <gco:CharacterString>http://gcmd.nasa.gov/MailComments/MailComments.jsf?rcpt=gcmduso</gco:CharacterString>
                              </gmd:contactInstructions>
                            </gmd:CI_Contact>
                          </gmd:contactInfo>
                          <gmd:role>
                            <gmd:CI_RoleCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_RoleCode" codeListValue="custodian">custodian</gmd:CI_RoleCode>
                          </gmd:role>
                        </gmd:CI_ResponsibleParty>
                      </gmd:citedResponsibleParty>
                      <gmd:citedResponsibleParty>
                        <gmd:CI_ResponsibleParty>
                          <gmd:organisationName>
                            <gco:CharacterString>NASA Global Change Master Directory (GCMD)</gco:CharacterString>
                          </gmd:organisationName>
                          <gmd:contactInfo>
                            <gmd:CI_Contact>
                              <gmd:phone gco:nilReason="missing"/>
                              <gmd:address>
                                <gmd:CI_Address>
                                  <gmd:deliveryPoint>
                                    <gco:CharacterString>NASA Global Change Master Directory, Goddard Space Flight Center</gco:CharacterString>
                                  </gmd:deliveryPoint>
                                  <gmd:city>
                                    <gco:CharacterString>Greenbelt</gco:CharacterString>
                                  </gmd:city>
                                  <gmd:administrativeArea>
                                    <gco:CharacterString>MD</gco:CharacterString>
                                  </gmd:administrativeArea>
                                  <gmd:postalCode>
                                    <gco:CharacterString>20771</gco:CharacterString>
                                  </gmd:postalCode>
                                  <gmd:country>
                                    <gco:CharacterString>USA</gco:CharacterString>
                                  </gmd:country>
                                  <gmd:electronicMailAddress>
                                    <gco:CharacterString>gcmduso@gcmd.gsfc.nasa.gov</gco:CharacterString>
                                  </gmd:electronicMailAddress>
                                </gmd:CI_Address>
                              </gmd:address>
                              <gmd:onlineResource>
                                <gmd:CI_OnlineResource>
                                  <gmd:linkage>
                                    <gmd:URL>http://gcmd.nasa.gov/Resources/valids/</gmd:URL>
                                  </gmd:linkage>
                                  <gmd:protocol>
                                    <gco:CharacterString>http</gco:CharacterString>
                                  </gmd:protocol>
                                  <gmd:applicationProfile>
                                    <gco:CharacterString>web browser</gco:CharacterString>
                                  </gmd:applicationProfile>
                                  <gmd:name>
                                    <gco:CharacterString>NASA Global Change Master Directory (GCMD) Keyword Page</gco:CharacterString>
                                  </gmd:name>
                                  <gmd:description>
                                    <gco:CharacterString>This page describes the NASA GCMD Keywords, how to reference those keywords and provides download instructions.</gco:CharacterString>
                                  </gmd:description>
                                  <gmd:function>
                                    <gmd:CI_OnLineFunctionCode codeList="http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode" codeListValue="download">download</gmd:CI_OnLineFunctionCode>
                                  </gmd:function>
                                </gmd:CI_OnlineResource>
                              </gmd:onlineResource>
                              <gmd:contactInstructions>
                                <gco:CharacterString>http://gcmd.nasa.gov/MailComments/MailComments.jsf?rcpt=gcmduso</gco:CharacterString>
                              </gmd:contactInstructions>
                            </gmd:CI_Contact>
                          </gmd:contactInfo>
                          <gmd:role>
                            <gmd:CI_RoleCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_RoleCode" codeListValue="custodian">custodian</gmd:CI_RoleCode>
                          </gmd:role>
                        </gmd:CI_ResponsibleParty>
                      </gmd:citedResponsibleParty>
                    </gmd:CI_Citation>
                  </gmd:thesaurusName>
                </xsl:if>
              </gmd:MD_Keywords>
            </gmd:descriptiveKeywords>
          </xsl:if>
          <!-- Time Keywords -->
          <xsl:if test="/*/TemporalKeywords/Keyword">
            <gmd:descriptiveKeywords>
              <gmd:MD_Keywords>
                <xsl:for-each select="/*/TemporalKeywords/Keyword">
                  <gmd:keyword>
                    <gco:CharacterString>
                      <xsl:value-of select="."/>
                    </gco:CharacterString>
                  </gmd:keyword>
                </xsl:for-each>
                <gmd:type>
                  <xsl:call-template name="writeCodelist">
                    <xsl:with-param name="codeListName" select="'MD_KeywordTypeCode'"/>
                    <xsl:with-param name="codeListValue" select="'temporal'"/>
                  </xsl:call-template>
                </gmd:type>
                <gmd:thesaurusName gco:nilReason="unknown"/>
              </gmd:MD_Keywords>
            </gmd:descriptiveKeywords>
          </xsl:if>
          <!-- Data Center Keywords -->
          <xsl:if test="//ArchiveCenter">
            <gmd:descriptiveKeywords>
              <gmd:MD_Keywords>
                <xsl:for-each select="//ArchiveCenter">
                  <gmd:keyword>
                    <gco:CharacterString>
                      <xsl:value-of select="."/>
                    </gco:CharacterString>
                  </gmd:keyword>
                </xsl:for-each>
                <gmd:type>
                  <xsl:call-template name="writeCodelist">
                    <xsl:with-param name="codeListName" select="'MD_KeywordTypeCode'"/>
                    <xsl:with-param name="codeListValue" select="'dataCenter'"/>
                  </xsl:call-template>
                </gmd:type>
                <gmd:thesaurusName>
                  <gmd:CI_Citation>
                    <gmd:title>
                      <gco:CharacterString> NASA/Global Change Master Directory (GCMD) Data Center Keywords </gco:CharacterString>
                    </gmd:title>
                    <gmd:date>
                      <gmd:CI_Date>
                        <gmd:date>
                          <gco:Date>2008-02-07</gco:Date>
                        </gmd:date>
                        <gmd:dateType>
                          <gmd:CI_DateTypeCode codeList="http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_DateTypeCode" codeListValue="publication">publication</gmd:CI_DateTypeCode>
                        </gmd:dateType>
                      </gmd:CI_Date>
                    </gmd:date>
                    <gmd:identifier/>
                    <gmd:citedResponsibleParty>
                      <gmd:CI_ResponsibleParty>
                        <gmd:organisationName>
                          <gco:CharacterString>NASA</gco:CharacterString>
                        </gmd:organisationName>
                        <gmd:positionName>
                          <gco:CharacterString>GCMD User Support Office</gco:CharacterString>
                        </gmd:positionName>
                        <gmd:contactInfo>
                          <gmd:CI_Contact>
                            <gmd:address>
                              <gmd:CI_Address>
                                <gmd:electronicMailAddress>
                                  <gco:CharacterString>gcmduso@gcmd.gsfc.nasa.gov</gco:CharacterString>
                                </gmd:electronicMailAddress>
                              </gmd:CI_Address>
                            </gmd:address>
                            <gmd:onlineResource>
                              <gmd:CI_OnlineResource>
                                <gmd:linkage>
                                  <gmd:URL>http://gcmd.nasa.gov/MailComments/MailComments.jsf?rcpt=gcmduso</gmd:URL>
                                </gmd:linkage>
                                <gmd:name>
                                  <gco:CharacterString>GCMD Feedback Form</gco:CharacterString>
                                </gmd:name>
                                <gmd:description>
                                  <gco:CharacterString>Have a Comment for the GCMD?</gco:CharacterString>
                                </gmd:description>
                                <gmd:function>
                                  <gmd:CI_OnLineFunctionCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode" codeListValue="information">information</gmd:CI_OnLineFunctionCode>
                                </gmd:function>
                              </gmd:CI_OnlineResource>
                            </gmd:onlineResource>
                          </gmd:CI_Contact>
                        </gmd:contactInfo>
                        <gmd:role>
                          <gmd:CI_RoleCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_RoleCode" codeListValue="custodian">custodian</gmd:CI_RoleCode>
                        </gmd:role>
                      </gmd:CI_ResponsibleParty>
                    </gmd:citedResponsibleParty>
                    <gmd:citedResponsibleParty>
                      <gmd:CI_ResponsibleParty>
                        <gmd:organisationName gco:nilReason="inapplicable"/>
                        <gmd:contactInfo>
                          <gmd:CI_Contact>
                            <gmd:onlineResource xlink:title="GCMD Keyword Page">
                              <gmd:CI_OnlineResource>
                                <gmd:linkage>
                                  <gmd:URL>http://gcmd.nasa.gov/Resources/valids/</gmd:URL>
                                </gmd:linkage>
                                <gmd:name>
                                  <gco:CharacterString>GCMD Keywords Page</gco:CharacterString>
                                </gmd:name>
                                <gmd:description>
                                  <gco:CharacterString>This page describes the NASA GCMD Keywords, how to reference those keywords and provides download instructions. </gco:CharacterString>
                                </gmd:description>
                                <gmd:function>
                                  <gmd:CI_OnLineFunctionCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode" codeListValue="information">information</gmd:CI_OnLineFunctionCode>
                                </gmd:function>
                              </gmd:CI_OnlineResource>
                            </gmd:onlineResource>
                          </gmd:CI_Contact>
                        </gmd:contactInfo>
                        <gmd:role>
                          <gmd:CI_RoleCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_RoleCode" codeListValue="custodian">custodian</gmd:CI_RoleCode>
                        </gmd:role>
                      </gmd:CI_ResponsibleParty>
                    </gmd:citedResponsibleParty>
                  </gmd:CI_Citation>
                </gmd:thesaurusName>
              </gmd:MD_Keywords>
            </gmd:descriptiveKeywords>
          </xsl:if>
          <!-- Project Keywords -->
          <xsl:if test="/*/Campaigns/Campaign/ShortName | /*/Campaigns/Campaign/LongName">
            <gmd:descriptiveKeywords>
              <gmd:MD_Keywords>
                <xsl:for-each select="/*/Campaigns/Campaign">
                  <gmd:keyword>
                    <gco:CharacterString>
                      <xsl:value-of select="ShortName"/>
                      <xsl:if test="LongName">
                        <xsl:value-of select="concat(' &gt; ',LongName)"/>
                      </xsl:if>
                    </gco:CharacterString>
                  </gmd:keyword>
                </xsl:for-each>
                <gmd:type>
                  <xsl:call-template name="writeCodelist">
                    <xsl:with-param name="codeListName" select="'MD_KeywordTypeCode'"/>
                    <xsl:with-param name="codeListValue" select="'project'"/>
                  </xsl:call-template>
                </gmd:type>
                <gmd:thesaurusName>
                  <gmd:CI_Citation>
                    <gmd:title>
                      <gco:CharacterString>NASA/GCMD Project Keywords</gco:CharacterString>
                    </gmd:title>
                    <gmd:date gco:nilReason="unknown"/>
                    <gmd:citedResponsibleParty>
                      <gmd:CI_ResponsibleParty>
                        <gmd:organisationName>
                          <gco:CharacterString>NASA</gco:CharacterString>
                        </gmd:organisationName>
                        <gmd:positionName>
                          <gco:CharacterString>GCMD User Support Office</gco:CharacterString>
                        </gmd:positionName>
                        <gmd:contactInfo>
                          <gmd:CI_Contact>
                            <gmd:address>
                              <gmd:CI_Address>
                                <gmd:electronicMailAddress>
                                  <gco:CharacterString>gcmduso@gcmd.gsfc.nasa.gov</gco:CharacterString>
                                </gmd:electronicMailAddress>
                              </gmd:CI_Address>
                            </gmd:address>
                            <gmd:onlineResource>
                              <gmd:CI_OnlineResource>
                                <gmd:linkage>
                                  <gmd:URL>http://gcmd.nasa.gov/MailComments/MailComments.jsf?rcpt=gcmduso</gmd:URL>
                                </gmd:linkage>
                                <gmd:name>
                                  <gco:CharacterString>GCMD Feedback Form</gco:CharacterString>
                                </gmd:name>
                                <gmd:description>
                                  <gco:CharacterString>Have a Comment for the GCMD?</gco:CharacterString>
                                </gmd:description>
                                <gmd:function>
                                  <gmd:CI_OnLineFunctionCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode" codeListValue="information">information</gmd:CI_OnLineFunctionCode>
                                </gmd:function>
                              </gmd:CI_OnlineResource>
                            </gmd:onlineResource>
                          </gmd:CI_Contact>
                        </gmd:contactInfo>
                        <gmd:role>
                          <gmd:CI_RoleCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_RoleCode" codeListValue="custodian">custodian</gmd:CI_RoleCode>
                        </gmd:role>
                      </gmd:CI_ResponsibleParty>
                    </gmd:citedResponsibleParty>
                    <gmd:citedResponsibleParty>
                      <gmd:CI_ResponsibleParty>
                        <gmd:organisationName gco:nilReason="inapplicable"/>
                        <gmd:contactInfo>
                          <gmd:CI_Contact>
                            <gmd:onlineResource>
                              <gmd:CI_OnlineResource>
                                <gmd:linkage>
                                  <gmd:URL>http://gcmd.nasa.gov/Resources/valids/</gmd:URL>
                                </gmd:linkage>
                                <gmd:name>
                                  <gco:CharacterString>GCMD Keywords Page</gco:CharacterString>
                                </gmd:name>
                                <gmd:description>
                                  <gco:CharacterString>This page describes the NASA GCMD Keywords, how to reference those keywords and provides download instructions. </gco:CharacterString>
                                </gmd:description>
                                <gmd:function>
                                  <gmd:CI_OnLineFunctionCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode" codeListValue="information">information</gmd:CI_OnLineFunctionCode>
                                </gmd:function>
                              </gmd:CI_OnlineResource>
                            </gmd:onlineResource>
                          </gmd:CI_Contact>
                        </gmd:contactInfo>
                        <gmd:role>
                          <gmd:CI_RoleCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_RoleCode" codeListValue="custodian">custodian</gmd:CI_RoleCode>
                        </gmd:role>
                      </gmd:CI_ResponsibleParty>
                    </gmd:citedResponsibleParty>
                  </gmd:CI_Citation>
                </gmd:thesaurusName>
              </gmd:MD_Keywords>
            </gmd:descriptiveKeywords>
          </xsl:if>
          <!-- Platform Keywords -->
          <xsl:if test="/*/Platforms/Platform/ShortName | /*/Platforms/Platform/ShortName">
            <gmd:descriptiveKeywords>
              <gmd:MD_Keywords>
                <xsl:for-each select="/*/Platforms/Platform">
                  <gmd:keyword>
                    <gco:CharacterString>
                      <xsl:value-of select="ShortName"/>
                      <xsl:if test="LongName">
                        <xsl:value-of select="concat(' &gt; ',LongName)"/>
                      </xsl:if>
                    </gco:CharacterString>
                  </gmd:keyword>
                </xsl:for-each>
                <gmd:type>
                  <xsl:call-template name="writeCodelist">
                    <xsl:with-param name="codeListName" select="'MD_KeywordTypeCode'"/>
                    <xsl:with-param name="codeListValue" select="'platform'"/>
                  </xsl:call-template>
                </gmd:type>
                <gmd:thesaurusName>
                  <gmd:CI_Citation>
                    <gmd:title>
                      <gco:CharacterString>NASA/GCMD Platform Keywords</gco:CharacterString>
                    </gmd:title>
                    <gmd:date gco:nilReason="unknown"/>
                    <gmd:citedResponsibleParty>
                      <gmd:CI_ResponsibleParty>
                        <gmd:organisationName>
                          <gco:CharacterString>NASA</gco:CharacterString>
                        </gmd:organisationName>
                        <gmd:positionName>
                          <gco:CharacterString>GCMD User Support Office</gco:CharacterString>
                        </gmd:positionName>
                        <gmd:contactInfo>
                          <gmd:CI_Contact>
                            <gmd:address>
                              <gmd:CI_Address>
                                <gmd:electronicMailAddress>
                                  <gco:CharacterString>gcmduso@gcmd.gsfc.nasa.gov</gco:CharacterString>
                                </gmd:electronicMailAddress>
                              </gmd:CI_Address>
                            </gmd:address>
                            <gmd:onlineResource>
                              <gmd:CI_OnlineResource>
                                <gmd:linkage>
                                  <gmd:URL>http://gcmd.nasa.gov/MailComments/MailComments.jsf?rcpt=gcmduso</gmd:URL>
                                </gmd:linkage>
                                <gmd:name>
                                  <gco:CharacterString>GCMD Feedback Form</gco:CharacterString>
                                </gmd:name>
                                <gmd:description>
                                  <gco:CharacterString>Have a Comment for the GCMD?</gco:CharacterString>
                                </gmd:description>
                                <gmd:function>
                                  <gmd:CI_OnLineFunctionCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode" codeListValue="information">information</gmd:CI_OnLineFunctionCode>
                                </gmd:function>
                              </gmd:CI_OnlineResource>
                            </gmd:onlineResource>
                          </gmd:CI_Contact>
                        </gmd:contactInfo>
                        <gmd:role>
                          <gmd:CI_RoleCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_RoleCode" codeListValue="custodian">custodian</gmd:CI_RoleCode>
                        </gmd:role>
                      </gmd:CI_ResponsibleParty>
                    </gmd:citedResponsibleParty>
                    <gmd:citedResponsibleParty>
                      <gmd:CI_ResponsibleParty>
                        <gmd:organisationName gco:nilReason="inapplicable"/>
                        <gmd:contactInfo>
                          <gmd:CI_Contact>
                            <gmd:onlineResource xlink:title="GCMD Keyword Page">
                              <gmd:CI_OnlineResource>
                                <gmd:linkage>
                                  <gmd:URL>http://gcmd.nasa.gov/Resources/valids/</gmd:URL>
                                </gmd:linkage>
                                <gmd:name>
                                  <gco:CharacterString>GCMD Keywords Page</gco:CharacterString>
                                </gmd:name>
                                <gmd:description>
                                  <gco:CharacterString>This page describes the NASA GCMD Keywords, how to reference those keywords and provides download instructions. </gco:CharacterString>
                                </gmd:description>
                                <gmd:function>
                                  <gmd:CI_OnLineFunctionCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode" codeListValue="information">information</gmd:CI_OnLineFunctionCode>
                                </gmd:function>
                              </gmd:CI_OnlineResource>
                            </gmd:onlineResource>
                          </gmd:CI_Contact>
                        </gmd:contactInfo>
                        <gmd:role>
                          <gmd:CI_RoleCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_RoleCode" codeListValue="custodian">custodian</gmd:CI_RoleCode>
                        </gmd:role>
                      </gmd:CI_ResponsibleParty>
                    </gmd:citedResponsibleParty>
                  </gmd:CI_Citation>
                </gmd:thesaurusName>
              </gmd:MD_Keywords>
            </gmd:descriptiveKeywords>
          </xsl:if>
          <!-- Instrument Keywords -->
          <xsl:if test="//Instruments/Instrument/ShortName | //Instruments/Instrument/LongName">
            <gmd:descriptiveKeywords>
              <gmd:MD_Keywords>
                <xsl:for-each select="/*/Platforms/Platform/Instruments/Instrument">
                  <gmd:keyword>
                    <gco:CharacterString>
                      <xsl:value-of select="ShortName"/>
                      <xsl:if test="LongName">
                        <xsl:value-of select="concat(' &gt; ',LongName)"/>
                      </xsl:if>
                    </gco:CharacterString>
                  </gmd:keyword>
                </xsl:for-each>
                <gmd:type>
                  <xsl:call-template name="writeCodelist">
                    <xsl:with-param name="codeListName" select="'MD_KeywordTypeCode'"/>
                    <xsl:with-param name="codeListValue" select="'instrument'"/>
                  </xsl:call-template>
                </gmd:type>
                <gmd:thesaurusName>
                  <gmd:CI_Citation>
                    <gmd:title>
                      <gco:CharacterString>NASA/GCMD Instrument Keywords</gco:CharacterString>
                    </gmd:title>
                    <gmd:date gco:nilReason="unknown"/>
                    <gmd:citedResponsibleParty>
                      <gmd:CI_ResponsibleParty>
                        <gmd:organisationName>
                          <gco:CharacterString>NASA</gco:CharacterString>
                        </gmd:organisationName>
                        <gmd:positionName>
                          <gco:CharacterString>GCMD User Support Office</gco:CharacterString>
                        </gmd:positionName>
                        <gmd:contactInfo>
                          <gmd:CI_Contact>
                            <gmd:address>
                              <gmd:CI_Address>
                                <gmd:electronicMailAddress>
                                  <gco:CharacterString>gcmduso@gcmd.gsfc.nasa.gov</gco:CharacterString>
                                </gmd:electronicMailAddress>
                              </gmd:CI_Address>
                            </gmd:address>
                            <gmd:onlineResource>
                              <gmd:CI_OnlineResource>
                                <gmd:linkage>
                                  <gmd:URL>http://gcmd.nasa.gov/MailComments/MailComments.jsf?rcpt=gcmduso</gmd:URL>
                                </gmd:linkage>
                                <gmd:name>
                                  <gco:CharacterString>GCMD Feedback Form</gco:CharacterString>
                                </gmd:name>
                                <gmd:description>
                                  <gco:CharacterString>Have a Comment for the GCMD?</gco:CharacterString>
                                </gmd:description>
                                <gmd:function>
                                  <gmd:CI_OnLineFunctionCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode" codeListValue="information">information</gmd:CI_OnLineFunctionCode>
                                </gmd:function>
                              </gmd:CI_OnlineResource>
                            </gmd:onlineResource>
                          </gmd:CI_Contact>
                        </gmd:contactInfo>
                        <gmd:role>
                          <gmd:CI_RoleCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_RoleCode" codeListValue="custodian">custodian</gmd:CI_RoleCode>
                        </gmd:role>
                      </gmd:CI_ResponsibleParty>
                    </gmd:citedResponsibleParty>
                    <gmd:citedResponsibleParty>
                      <gmd:CI_ResponsibleParty>
                        <gmd:organisationName gco:nilReason="inapplicable"/>
                        <gmd:contactInfo>
                          <gmd:CI_Contact>
                            <gmd:onlineResource>
                              <gmd:CI_OnlineResource>
                                <gmd:linkage>
                                  <gmd:URL>http://gcmd.nasa.gov/Resources/valids/</gmd:URL>
                                </gmd:linkage>
                                <gmd:name>
                                  <gco:CharacterString>GCMD Keywords Page</gco:CharacterString>
                                </gmd:name>
                                <gmd:description>
                                  <gco:CharacterString>This page describes the NASA GCMD Keywords, how to reference those keywords and provides download instructions. </gco:CharacterString>
                                </gmd:description>
                                <gmd:function>
                                  <gmd:CI_OnLineFunctionCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode" codeListValue="information">information</gmd:CI_OnLineFunctionCode>
                                </gmd:function>
                              </gmd:CI_OnlineResource>
                            </gmd:onlineResource>
                          </gmd:CI_Contact>
                        </gmd:contactInfo>
                        <gmd:role>
                          <gmd:CI_RoleCode codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_RoleCode" codeListValue="custodian">custodian</gmd:CI_RoleCode>
                        </gmd:role>
                      </gmd:CI_ResponsibleParty>
                    </gmd:citedResponsibleParty>
                  </gmd:CI_Citation>
                </gmd:thesaurusName>
              </gmd:MD_Keywords>
            </gmd:descriptiveKeywords>
          </xsl:if>
          <gmd:resourceConstraints>
            <gmd:MD_LegalConstraints>
              <gmd:useLimitation>
                <xsl:call-template name="writeCharacterString">
                  <xsl:with-param name="stringToWrite" select="concat('Restriction Comment: ',/*/RestrictionComment)"/>
                </xsl:call-template>
              </gmd:useLimitation>
              <gmd:otherConstraints>
                <xsl:call-template name="writeCharacterString">
                  <xsl:with-param name="stringToWrite" select="concat('Restriction Flag:',/*/RestrictionFlag)"/>
                </xsl:call-template>
              </gmd:otherConstraints>
            </gmd:MD_LegalConstraints>
          </gmd:resourceConstraints>
          <!-- Associated collections are treated differently in collection and granule records -->
          <!-- Collection Associations are used in collection records -->
          <!-- Collections associated with type = "Input" are listed as sources. Others described here -->
          <xsl:for-each select="/*/CollectionAssociations/CollectionAssociation[CollectionType!='Input']">
            <gmd:aggregationInfo>
              <gmd:MD_AggregateInformation>
                <gmd:aggregateDataSetName>
                  <gmd:CI_Citation>
                    <gmd:title>
                      <xsl:call-template name="writeCharacterString">
                        <xsl:with-param name="stringToWrite" select="ShortName"/>
                      </xsl:call-template>
                    </gmd:title>
                    <gmd:date gco:nilReason="unknown"/>
                    <gmd:edition>
                      <xsl:call-template name="writeCharacterString">
                        <xsl:with-param name="stringToWrite" select="VersionId"/>
                      </xsl:call-template>
                    </gmd:edition>
                    <gmd:otherCitationDetails>
                      <gco:CharacterString>
                        <xsl:value-of select="CollectionUse"/>
                      </gco:CharacterString>
                    </gmd:otherCitationDetails>
                  </gmd:CI_Citation>
                </gmd:aggregateDataSetName>
                <gmd:aggregateDataSetIdentifier>
                  <gmd:MD_Identifier>
                    <gmd:code>
                      <xsl:call-template name="writeCharacterString">
                        <xsl:with-param name="stringToWrite" select="ShortName"/>
                      </xsl:call-template>
                    </gmd:code>
                  </gmd:MD_Identifier>
                </gmd:aggregateDataSetIdentifier>
                <gmd:associationType>
                  <xsl:call-template name="writeCodelist">
                    <xsl:with-param name="codeListName" select="'DS_AssociationTypeCode'"/>
                    <xsl:with-param name="codeListValue" select="CollectionType"/>
                  </xsl:call-template>
                </gmd:associationType>
              </gmd:MD_AggregateInformation>
            </gmd:aggregationInfo>
          </xsl:for-each>
          <!-- Related collections are described in granule records using CollectionRefs -->
          <xsl:for-each select="/*/Collection">
            <gmd:aggregationInfo>
              <gmd:MD_AggregateInformation>
                <gmd:aggregateDataSetName>
                  <gmd:CI_Citation>
                    <gmd:title>
                      <xsl:call-template name="writeCharacterString">
                        <xsl:with-param name="stringToWrite" select="ShortName | DataSetId"/>
                      </xsl:call-template>
                    </gmd:title>
                    <gmd:date gco:nilReason="unknown"/>
                    <xsl:if test="VersionId">
                      <gmd:edition>
                        <xsl:call-template name="writeCharacterString">
                          <xsl:with-param name="stringToWrite" select="VersionId"/>
                        </xsl:call-template>
                      </gmd:edition>
                    </xsl:if>
                  </gmd:CI_Citation>
                </gmd:aggregateDataSetName>
                <gmd:aggregateDataSetIdentifier>
                  <gmd:MD_Identifier>
                    <gmd:code>
                      <!-- The identifier is either the ShortName or the DataSetID. Both can not exist.  -->
                      <xsl:call-template name="writeCharacterString">
                        <xsl:with-param name="stringToWrite" select="ShortName | DataSetId"/>
                      </xsl:call-template>
                    </gmd:code>
                  </gmd:MD_Identifier>
                </gmd:aggregateDataSetIdentifier>
                <!-- In granule records these are all LargeWorkCitations (no CollectionType is included) -->
                <gmd:associationType>
                  <xsl:call-template name="writeCodelist">
                    <xsl:with-param name="codeListName" select="'DS_AssociationTypeCode'"/>
                    <xsl:with-param name="codeListValue" select="'LargerWorkCitation'"/>
                  </xsl:call-template>
                </gmd:associationType>
              </gmd:MD_AggregateInformation>
            </gmd:aggregationInfo>
          </xsl:for-each>
          <gmd:language>
            <gco:CharacterString>eng</gco:CharacterString>
          </gmd:language>
          <gmd:characterSet>
            <xsl:call-template name="writeCodelist">
              <xsl:with-param name="codeListName" select="'MD_CharacterSetCode'"/>
              <xsl:with-param name="codeListValue" select="'utf8'"/>
            </xsl:call-template>
          </gmd:characterSet>
          <xsl:call-template name="ECHOExtentToISO"/>
          <gmd:supplementalInformation>
            <xsl:for-each select="/*/CSDTDescriptions/CSDTDescription">
              <xsl:call-template name="writeCharacterString">
                <xsl:with-param name="stringToWrite" select="concat('CSDT',position(),': ',PrimaryCSDT,
                ' Implementation: ',Implementation,
                ' Comments: ',CSDTComments,
                ' Indirect Reference: ',IndirectReference,' ')"/>
              </xsl:call-template>
            </xsl:for-each>
          </gmd:supplementalInformation>
          <!-- This is a field added to 19115-1 to accomodate NASA/NOAA requirements -->
          <gmd:processingLevel>
            <gmd:MD_Identifier>
              <gmd:code>
                <gco:CharacterString>
                  <xsl:value-of select="/*/ProcessingLevelId"/>
                </gco:CharacterString>
              </gmd:code>
              <gmd:description>
                <gco:CharacterString>
                  <xsl:value-of select="/*/ProcessingLevelDescription"/>
                </gco:CharacterString>
              </gmd:description>
            </gmd:MD_Identifier>
          </gmd:processingLevel>
        </gmd:MD_DataIdentification>
      </gmd:identificationInfo>
      <xsl:if test="$contentInformationCount > 0">
        <!-- This contentInfo section exists to accomodate AdditionalAttributes with type = contentInformation.
        These AdditionalAttributes provide information about the parameters in the dataset. There is no way to
        associate these AdditionalAttributes with a particular parameter so they are in a separate MD_Band object
        without a sequenceIdentifier. -->
        <gmd:contentInfo>
          <gmd:MD_CoverageDescription>
            <gmd:attributeDescription gco:nilReason="missing"/>
            <gmd:contentType>
              <xsl:call-template name="writeCodelist">
                <xsl:with-param name="codeListName" select="'MD_CoverageContentTypeCode'"/>
                <xsl:with-param name="codeListValue" select="'physicalMeasurement'"/>
              </xsl:call-template>
            </gmd:contentType>
            <gmd:dimension>
              <gmd:MD_Band>
                <gmd:otherPropertyType>
                  <gco:RecordType xlink:href="http://earthdata.nasa.gov/metadata/schema/eos/1.0/eos.xsd#xpointer(//element[@name='AdditionalAttributes'])">Echo Additional Attributes</gco:RecordType>
                </gmd:otherPropertyType>
                <gmd:otherProperty>
                  <gco:Record>
                    <eos:AdditionalAttributes>
                      <xsl:for-each select="//AdditionalAttribute">
                        <xsl:if test="key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='contentInformation'">
                          <xsl:call-template name="writeEOS_PSA">
                            <xsl:with-param name="additionalAttributeType" select="'contentInformation'"/>
                          </xsl:call-template>
                        </xsl:if>
                      </xsl:for-each>
                      <xsl:if test="/Granule/DataGranule/DayNightFlag">
                        <eos:AdditionalAttribute>
                          <eos:reference>
                            <eos:EOS_AdditionalAttributeDescription>
                              <eos:type>
                                <xsl:element name="eos:EOS_AdditionalAttributeTypeCode">
                                  <xsl:attribute name="codeList" select="'http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeTypeCode'"/>
                                  <xsl:attribute name="codeListValue" select="'contentInformation'"/>
                                  <xsl:value-of select="'contentInformation'"/>
                                </xsl:element>
                              </eos:type>
                              <eos:name>
                                <gco:CharacterString>DayNightFlag</gco:CharacterString>
                              </eos:name>
                            </eos:EOS_AdditionalAttributeDescription>
                          </eos:reference>
                          <xsl:element name="eos:value">
                            <gco:CharacterString>
                              <xsl:value-of select="/Granule/DataGranule/DayNightFlag"/>
                            </gco:CharacterString>
                          </xsl:element>
                        </eos:AdditionalAttribute>
                      </xsl:if>
                    </eos:AdditionalAttributes>
                  </gco:Record>
                </gmd:otherProperty>
              </gmd:MD_Band>
            </gmd:dimension>
          </gmd:MD_CoverageDescription>
        </gmd:contentInfo>
      </xsl:if>
      <xsl:if test="/*/MeasuredParameters/MeasuredParameter">
        <!-- MeasuredParameters are included in granule records. They are listed along with several quality statistics and flags -->
        <gmd:contentInfo>
          <gmd:MD_CoverageDescription>
            <gmd:attributeDescription/>
            <gmd:contentType>
              <xsl:call-template name="writeCodelist">
                <xsl:with-param name="codeListName" select="'MD_CoverageContentTypeCode'"/>
                <xsl:with-param name="codeListValue" select="'physicalMeasurement'"/>
              </xsl:call-template>
            </gmd:contentType>
            <xsl:for-each select="/*/MeasuredParameters/MeasuredParameter">
              <gmd:dimension>
                <gmd:MD_Band>
                  <gmd:sequenceIdentifier>
                    <gco:MemberName>
                      <gco:aName>
                        <gco:CharacterString>
                          <xsl:value-of select="ParameterName"/>
                        </gco:CharacterString>
                      </gco:aName>
                      <gco:attributeType gco:nilReason="unknown"/>
                    </gco:MemberName>
                  </gmd:sequenceIdentifier>
                  <!-- Include QAStats and QAFlags with each MeasuredParameter. Note AdditionalAttributeType = qualityInformation instead of contentInformation. DataType assumed to be FLOAT. -->
                  <gmd:otherPropertyType>
                    <gco:RecordType xlink:href="http://earthdata.nasa.gov/metadata/schema/eos/1.0/eos.xsd#xpointer(//element[@name='AdditionalAttributes'])">Echo Additional Attributes</gco:RecordType>
                  </gmd:otherPropertyType>
                  <gmd:otherProperty>
                    <gco:Record>
                      <eos:AdditionalAttributes>
                        <xsl:for-each select="QAStats/*">
                          <!-- The QAStats have only a value -->
                          <eos:AdditionalAttribute>
                            <eos:reference>
                              <eos:EOS_AdditionalAttributeDescription>
                                <eos:type>
                                  <xsl:element name="eos:EOS_AdditionalAttributeTypeCode">
                                    <xsl:attribute name="codeList" select="'http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeTypeCode'"/>
                                    <xsl:attribute name="codeListValue" select="'qualityInformation'"/>
                                    <xsl:value-of select="'qualityInformation'"/>
                                  </xsl:element>
                                </eos:type>
                                <xsl:element name="eos:name">
                                  <xsl:call-template name="writeCharacterString">
                                    <xsl:with-param name="stringToWrite" select="local-name()"/>
                                  </xsl:call-template>
                                </xsl:element>
                                <eos:dataType>
                                  <eos:EOS_AdditionalAttributeDataTypeCode codeList="http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeDataTypeCode" codeListValue="FLOAT">FLOAT</eos:EOS_AdditionalAttributeDataTypeCode>
                                </eos:dataType>
                              </eos:EOS_AdditionalAttributeDescription>
                            </eos:reference>
                            <xsl:element name="eos:value">
                              <gco:CharacterString>
                                <xsl:value-of select="."/>
                              </gco:CharacterString>
                            </xsl:element>
                          </eos:AdditionalAttribute>
                        </xsl:for-each>
                        <xsl:for-each select="QAFlags/*[not(contains(local-name(),'Explanation'))]">
                          <!-- The QAFlags include a value and an explanation (translated to description) -->
                          <eos:AdditionalAttribute>
                            <eos:reference>
                              <eos:EOS_AdditionalAttributeDescription>
                                <eos:type>
                                  <xsl:element name="eos:EOS_AdditionalAttributeTypeCode">
                                    <xsl:attribute name="codeList" select="'http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeTypeCode'"/>
                                    <xsl:attribute name="codeListValue" select="'qualityInformation'"/>
                                    <xsl:value-of select="'qualityInformation'"/>
                                  </xsl:element>
                                </eos:type>
                                <xsl:element name="eos:name">
                                  <xsl:call-template name="writeCharacterString">
                                    <xsl:with-param name="stringToWrite" select="local-name()"/>
                                  </xsl:call-template>
                                </xsl:element>
                                <xsl:element name="eos:description">
                                  <xsl:variable name="explanationName" select="concat(local-name(),'Explanation')"/>
                                  <gco:CharacterString>
                                    <xsl:value-of select="../*[local-name()=$explanationName]"/>
                                  </gco:CharacterString>
                                </xsl:element>
                                <eos:dataType>
                                  <eos:EOS_AdditionalAttributeDataTypeCode codeList="http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeDataTypeCode" codeListValue="FLOAT">FLOAT</eos:EOS_AdditionalAttributeDataTypeCode>
                                </eos:dataType>
                              </eos:EOS_AdditionalAttributeDescription>
                            </eos:reference>
                            <xsl:element name="eos:value">
                              <gco:CharacterString>
                                <xsl:value-of select="."/>
                              </gco:CharacterString>
                            </xsl:element>
                          </eos:AdditionalAttribute>
                        </xsl:for-each>
                      </eos:AdditionalAttributes>
                    </gco:Record>
                  </gmd:otherProperty>
                </gmd:MD_Band>
              </gmd:dimension>
            </xsl:for-each>
          </gmd:MD_CoverageDescription>
        </gmd:contentInfo>
      </xsl:if>
      <xsl:if test="/*/ProcessingLevelId or /*/CloudCover">
        <!-- ProcessingLevelId and CloudCover are included in the MD_ImageDescription object. This is a special contentInformation object. -->
        <gmd:contentInfo>
          <gmd:MD_ImageDescription>
            <gmd:attributeDescription/>
            <gmd:contentType/>
            <xsl:if test="/*/ProcessingLevelId">
              <gmd:processingLevelCode>
                <gmd:MD_Identifier>
                  <gmd:code>
                    <gco:CharacterString>
                      <xsl:value-of select="/*/ProcessingLevelId"/>
                    </gco:CharacterString>
                  </gmd:code>
                  <gmd:description>
                    <gco:CharacterString>
                      <xsl:value-of select="/*/ProcessingLevelDescription"/>
                    </gco:CharacterString>
                  </gmd:description>
                </gmd:MD_Identifier>
              </gmd:processingLevelCode>
            </xsl:if>
            <xsl:if test="/*/CloudCover">
              <gmd:cloudCoverPercentage>
                <gco:Real>
                  <xsl:value-of select="/*/CloudCover"/>
                </gco:Real>
              </gmd:cloudCoverPercentage>
            </xsl:if>
          </gmd:MD_ImageDescription>
        </gmd:contentInfo>
      </xsl:if>
      <gmd:distributionInfo>
        <gmd:MD_Distribution>
          <!-- There is a known problem with multiple distributorContacts. The standard allows only one which means that, even when you have different roles at a single distributor,
            you need an entire distributor section for each contact.
            This is fixed in 19115-1  -->
          <xsl:variable name="distributorContactCount" select="count(/*/Contacts/Contact[contains(Role,'Archive')
            or contains(Role,'DATA CENTER CONTACT')
            or contains(Role,'Distributor')
            or contains(Role,'User Services')
            or contains(Role,'GHRC USER SERVICES')
            or contains(Role,'ORNL DAAC User Services')])"/>
          <gmd:distributor>
            <gmd:MD_Distributor>
              <!-- Use Archive Center only if no more complete contact information exists -->
              <xsl:if test="$distributorContactCount = 0">
                <xsl:choose>
                  <xsl:when test="/*/ArchiveCenter">
                    <gmd:distributorContact>
                      <gmd:CI_ResponsibleParty>
                        <gmd:organisationName>
                          <xsl:call-template name="writeCharacterString">
                            <xsl:with-param name="stringToWrite" select="/*/ArchiveCenter"/>
                          </xsl:call-template>
                        </gmd:organisationName>
                        <gmd:role>
                          <xsl:call-template name="writeCodelist">
                            <xsl:with-param name="codeListName" select="'CI_RoleCode'"/>
                            <xsl:with-param name="codeListValue" select="'distributor'"/>
                          </xsl:call-template>
                        </gmd:role>
                      </gmd:CI_ResponsibleParty>
                    </gmd:distributorContact>
                  </xsl:when>
                  <xsl:otherwise>
                    <gmd:distributorContact gco:nilReason="missing"/>
                  </xsl:otherwise>
                </xsl:choose>
              </xsl:if>
              <xsl:for-each select="/*/Contacts/Contact[contains(Role,'Archive') or contains(Role,'DATA CENTER CONTACT')  or contains(Role,'Distributor') or contains(Role,'User Services') or contains(Role,'GHRC USER SERVICES') or contains(Role,'ORNL DAAC User Services') ]">
                <xsl:if test="position()=1">
                  <xsl:call-template name="contact2pointOfContact">
                    <xsl:with-param name="roleName" select="'gmd:distributorContact'"/>
                    <xsl:with-param name="roleCode" select="'distributor'"/>
                  </xsl:call-template>
                </xsl:if>
              </xsl:for-each>
              <gmd:distributionOrderProcess>
                <gmd:MD_StandardOrderProcess>
                  <gmd:fees>
                    <xsl:call-template name="writeCharacterString">
                      <xsl:with-param name="stringToWrite" select="/*/Price"/>
                    </xsl:call-template>
                  </gmd:fees>
                </gmd:MD_StandardOrderProcess>
              </gmd:distributionOrderProcess>
              <xsl:for-each select="/*/DataFormat | /*/CSDTDescriptions/CSDTDescription/Implementation">
                <gmd:distributorFormat>
                  <gmd:MD_Format>
                    <gmd:name>
                      <xsl:call-template name="writeCharacterString">
                        <xsl:with-param name="stringToWrite" select="."/>
                      </xsl:call-template>
                    </gmd:name>
                    <xsl:choose>
                      <xsl:when test="contains(.,'4')">
                        <gmd:version>
                          <gco:CharacterString>4</gco:CharacterString>
                        </gmd:version>
                      </xsl:when>
                      <xsl:when test="contains(.,'5')">
                        <gmd:version>
                          <gco:CharacterString>5</gco:CharacterString>
                        </gmd:version>
                      </xsl:when>
                      <xsl:otherwise>
                        <gmd:version gco:nilReason="unknown"/>
                      </xsl:otherwise>
                    </xsl:choose>
                  </gmd:MD_Format>
                </gmd:distributorFormat>
              </xsl:for-each>
              <gmd:distributorTransferOptions>
                <gmd:MD_DigitalTransferOptions>
                  <xsl:if test="/*/DataGranule/SizeMBDataGranule">
                    <gmd:transferSize>
                      <gco:Real>
                        <xsl:value-of select="/*/DataGranule/SizeMBDataGranule"/>
                      </gco:Real>
                    </gmd:transferSize>
                  </xsl:if>
                  <xsl:for-each select="/*/OnlineAccessURLs/OnlineAccessURL">
                    <gmd:onLine>
                      <gmd:CI_OnlineResource>
                        <gmd:linkage>
                          <gmd:URL>
                            <xsl:value-of select="URL"/>
                          </gmd:URL>
                        </gmd:linkage>
                        <gmd:applicationProfile>
                          <xsl:call-template name="writeCharacterString">
                            <xsl:with-param name="stringToWrite" select="MimeType"/>
                          </xsl:call-template>
                        </gmd:applicationProfile>
                        <gmd:description>
                          <xsl:call-template name="writeCharacterString">
                            <xsl:with-param name="stringToWrite" select="URLDescription"/>
                          </xsl:call-template>
                        </gmd:description>
                        <gmd:function>
                          <xsl:call-template name="writeCodelist">
                            <xsl:with-param name="codeListName" select="'CI_OnLineFunctionCode'"/>
                            <xsl:with-param name="codeListValue" select="'download'"/>
                          </xsl:call-template>
                        </gmd:function>
                      </gmd:CI_OnlineResource>
                    </gmd:onLine>
                  </xsl:for-each>
                  <xsl:for-each select="/*/OnlineResources/OnlineResource">
                    <gmd:onLine>
                      <gmd:CI_OnlineResource>
                        <gmd:linkage>
                          <gmd:URL>
                            <xsl:value-of select="URL"/>
                          </gmd:URL>
                        </gmd:linkage>
                        <gmd:applicationProfile>
                          <xsl:call-template name="writeCharacterString">
                            <xsl:with-param name="stringToWrite" select="MimeType"/>
                          </xsl:call-template>
                        </gmd:applicationProfile>
                        <gmd:name>
                          <xsl:call-template name="writeCharacterString">
                            <xsl:with-param name="stringToWrite" select="Type"/>
                          </xsl:call-template>
                        </gmd:name>
                        <gmd:description>
                          <xsl:call-template name="writeCharacterString">
                            <xsl:with-param name="stringToWrite" select="Description"/>
                          </xsl:call-template>
                        </gmd:description>
                        <gmd:function>
                          <!-- lookup the CI_OnlineFunctionCode for the ECHO /*/OnlineResources/OnlineResource/Type  -->
                          <xsl:variable name="onlineResourceType" select="key('onlineResourceTypeLookup',Type,doc('onlineResourceType.xml'))/@type"/>
                          <xsl:call-template name="writeCodelist">
                            <xsl:with-param name="codeListName" select="'CI_OnLineFunctionCode'"/>
                            <!-- This is the line for hard coding this and avoiding the lookup... -->
                            <!--<xsl:with-param name="codeListValue" select="'information'"/>-->
                            <xsl:with-param name="codeListValue" select="$onlineResourceType"/>
                          </xsl:call-template>
                        </gmd:function>
                      </gmd:CI_OnlineResource>
                    </gmd:onLine>
                  </xsl:for-each>
                  <!-- This section writes AdditionalAttributes identified as having type = distribution.url -->
                  <xsl:for-each select="/*/AdditionalAttributes/AdditionalAttribute">
                    <xsl:if test="key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='distribution.url'">
                      <xsl:for-each select="Values/Value | Value">
                        <gmd:onLine>
                          <gmd:CI_OnlineResource>
                            <gmd:linkage>
                              <gmd:URL>
                                <xsl:value-of select="."/>
                              </gmd:URL>
                            </gmd:linkage>
                            <gmd:description>
                              <xsl:call-template name="writeCharacterString">
                                <xsl:with-param name="stringToWrite" select="ancestor::AdditionalAttribute/Name"/>
                              </xsl:call-template>
                            </gmd:description>
                            <gmd:function>
                              <xsl:call-template name="writeCodelist">
                                <xsl:with-param name="codeListName" select="'CI_OnLineFunctionCode'"/>
                                <xsl:with-param name="codeListValue" select="'download'"/>
                              </xsl:call-template>
                            </gmd:function>
                          </gmd:CI_OnlineResource>
                        </gmd:onLine>
                      </xsl:for-each>
                    </xsl:if>
                  </xsl:for-each>
                </gmd:MD_DigitalTransferOptions>
              </gmd:distributorTransferOptions>
            </gmd:MD_Distributor>
          </gmd:distributor>
          <xsl:for-each select="/*/Contacts/Contact[contains(Role,'Archive') or contains(Role,'DATA CENTER CONTACT')  or contains(Role,'Distributor') or contains(Role,'User Services') or contains(Role,'GHRC USER SERVICES') or contains(Role,'ORNL DAAC User Services') ]">
            <xsl:if test="position()>1">
              <gmd:distributor>
                <gmd:MD_Distributor>
                  <xsl:call-template name="contact2pointOfContact">
                    <xsl:with-param name="roleName" select="'gmd:distributorContact'"/>
                    <xsl:with-param name="roleCode" select="'distributor'"/>
                  </xsl:call-template>
                </gmd:MD_Distributor>
              </gmd:distributor>
            </xsl:if>
          </xsl:for-each>
          <xsl:for-each select="/*/Contacts/Contact[contains(Role,'Data Manager')]">
            <gmd:distributor>
              <gmd:MD_Distributor>
                <xsl:call-template name="contact2pointOfContact">
                  <xsl:with-param name="roleName" select="'gmd:distributorContact'"/>
                  <xsl:with-param name="roleCode" select="'custodian'"/>
                </xsl:call-template>
              </gmd:MD_Distributor>
            </gmd:distributor>
          </xsl:for-each>
        </gmd:MD_Distribution>
      </gmd:distributionInfo>
      <gmd:dataQualityInfo>
        <gmd:DQ_DataQuality>
          <gmd:scope>
            <gmd:DQ_Scope>
              <gmd:level>
                <xsl:call-template name="writeCodelist">
                  <xsl:with-param name="codeListName" select="'MD_ScopeCode'"/>
                  <xsl:with-param name="codeListValue" select="$metadataScope"/>
                </xsl:call-template>
              </gmd:level>
            </gmd:DQ_Scope>
          </gmd:scope>
          <!-- This section writes AdditionalAttributes identified as having type = quaityInformation -->
          <xsl:for-each select="/*/AdditionalAttributes/AdditionalAttribute">
            <xsl:if test="key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='qualityInformation'">
              <gmd:report>
                <gmd:DQ_QuantitativeAttributeAccuracy>
                  <gmd:nameOfMeasure>
                    <gco:CharacterString>
                      <xsl:value-of select="Name"/>
                    </gco:CharacterString>
                  </gmd:nameOfMeasure>
                  <gmd:result>
                    <gmd:DQ_QuantitativeResult>
                      <gmd:valueType>
                        <gco:RecordType xlink:href="http://earthdata.nasa.gov/metadata/schema/eos/1.0/eos.xsd#xpointer(//element[@name='AdditionalAttributes'])">Echo Additional Attributes</gco:RecordType>
                      </gmd:valueType>
                      <gmd:valueUnit gco:nilReason="missing"/>
                      <gmd:value>
                        <gco:Record>
                          <eos:AdditionalAttributes>
                            <xsl:call-template name="writeEOS_PSA">
                              <xsl:with-param name="additionalAttributeType" select="'qualityInformation'"/>
                            </xsl:call-template>
                          </eos:AdditionalAttributes>
                        </gco:Record>
                      </gmd:value>
                    </gmd:DQ_QuantitativeResult>
                  </gmd:result>
                </gmd:DQ_QuantitativeAttributeAccuracy>
              </gmd:report>
            </xsl:if>
          </xsl:for-each>
          <xsl:if test="/*/Temporal/PrecisionOfSeconds">
            <gmd:report>
              <gmd:DQ_AccuracyOfATimeMeasurement>
                <gmd:measureIdentification>
                  <gmd:MD_Identifier>
                    <gmd:code>
                      <xsl:call-template name="writeCharacterString">
                        <xsl:with-param name="stringToWrite" select="'PrecisionOfSeconds'"/>
                      </xsl:call-template>
                    </gmd:code>
                  </gmd:MD_Identifier>
                </gmd:measureIdentification>
                <gmd:result>
                  <gmd:DQ_QuantitativeResult>
                    <gmd:valueUnit/>
                    <gmd:value>
                      <gco:Record xsi:type="gco:Real_PropertyType">
                        <gco:Real>
                          <xsl:value-of select="/*/Temporal/PrecisionOfSeconds"/>
                        </gco:Real>
                      </gco:Record>
                    </gmd:value>
                  </gmd:DQ_QuantitativeResult>
                </gmd:result>
              </gmd:DQ_AccuracyOfATimeMeasurement>
            </gmd:report>
          </xsl:if>
          <gmd:lineage>
            <gmd:LI_Lineage>
              <gmd:processStep>
                <gmi:LE_ProcessStep>
                  <xsl:choose>
                    <xsl:when test="/*/DataGranule/ReprocessingActual">
                      <gmd:description>
                        <gco:CharacterString>
                          <xsl:value-of select="/*/DataGranule/ReprocessingActual"/>
                        </gco:CharacterString>
                      </gmd:description>
                    </xsl:when>
                    <xsl:otherwise>
                      <gmd:description gco:nilReason="unknown"/>
                    </xsl:otherwise>
                  </xsl:choose>
                  <xsl:if test="/*/DataGranule/ProductionDateTime">
                    <gmd:dateTime>
                      <gco:DateTime>
                        <xsl:value-of select="/*/DataGranule/ProductionDateTime"/>
                      </gco:DateTime>
                    </gmd:dateTime>
                  </xsl:if>
                  <xsl:if test="/*/ProcessingCenter">
                    <gmd:processor>
                      <gmd:CI_ResponsibleParty>
                        <gmd:organisationName>
                          <xsl:call-template name="writeCharacterString">
                            <xsl:with-param name="stringToWrite" select="/*/ProcessingCenter"/>
                          </xsl:call-template>
                        </gmd:organisationName>
                        <gmd:role>
                          <xsl:call-template name="writeCodelist">
                            <xsl:with-param name="codeListName" select="'CI_RoleCode'"/>
                            <xsl:with-param name="codeListValue" select="'processor'"/>
                          </xsl:call-template>
                        </gmd:role>
                      </gmd:CI_ResponsibleParty>
                    </gmd:processor>
                  </xsl:if>
                  <xsl:if test="/*/AlgorithmPackages/AlgorithmPackage or $processingInformationCount > 0 or /*/PGEVersionClass">
                    <gmi:processingInformation>
                      <eos:EOS_Processing>
                        <xsl:for-each select="/*/PGEVersionClass">
                          <gmi:identifier>
                            <gmd:MD_Identifier>
                              <gmd:code>
                                <xsl:call-template name="writeCharacterString">
                                  <xsl:with-param name="stringToWrite" select="PGEVersion"/>
                                </xsl:call-template>
                              </gmd:code>
                              <gmd:description>
                                <xsl:call-template name="writeCharacterString">
                                  <xsl:with-param name="stringToWrite" select="PGEName"/>
                                </xsl:call-template>
                              </gmd:description>
                            </gmd:MD_Identifier>
                          </gmi:identifier>
                          <gmi:softwareReference>
                            <gmd:CI_Citation>
                              <gmd:title>
                                <xsl:call-template name="writeCharacterString">
                                  <xsl:with-param name="stringToWrite" select="PGEName"/>
                                </xsl:call-template>
                              </gmd:title>
                              <gmd:date gco:nilReason="unknown"/>
                              <gmd:edition>
                                <xsl:call-template name="writeCharacterString">
                                  <xsl:with-param name="stringToWrite" select="PGEVersion"/>
                                </xsl:call-template>
                              </gmd:edition>
                            </gmd:CI_Citation>
                          </gmi:softwareReference>
                        </xsl:for-each>
                        <xsl:for-each select="/*/AlgorithmPackages/AlgorithmPackage">
                          <gmi:algorithm>
                            <gmi:LE_Algorithm>
                              <gmi:citation>
                                <gmd:CI_Citation>
                                  <gmd:title>
                                    <xsl:call-template name="writeCharacterString">
                                      <xsl:with-param name="stringToWrite" select="Name"/>
                                    </xsl:call-template>
                                  </gmd:title>
                                  <gmd:date gco:nilReason="unknown"/>
                                  <gmd:edition>
                                    <xsl:call-template name="writeCharacterString">
                                      <xsl:with-param name="stringToWrite" select="Version"/>
                                    </xsl:call-template>
                                  </gmd:edition>
                                </gmd:CI_Citation>
                              </gmi:citation>
                              <gmi:description>
                                <gco:CharacterString>
                                  <xsl:value-of select="Description"/>
                                </gco:CharacterString>
                              </gmi:description>
                            </gmi:LE_Algorithm>
                          </gmi:algorithm>
                        </xsl:for-each>
                        <!-- NASA Specific Extensions -->
                        <xsl:if test="$processingInformationCount > 0">
                          <xsl:if test="not(/*/PGEVersionClass/PGEVersion)">
                            <gmi:identifier gco:nilReason="unknown"/>
                          </xsl:if>
                          <eos:otherPropertyType>
                            <gco:RecordType xlink:href="http://earthdata.nasa.gov/metadata/schema/eos/1.0/eos.xsd#xpointer(//element[@name='AdditionalAttributes'])">Echo Additional Attributes</gco:RecordType>
                          </eos:otherPropertyType>
                          <eos:otherProperty>
                            <gco:Record>
                              <eos:AdditionalAttributes>
                                <xsl:for-each select="/*/AdditionalAttributes/AdditionalAttribute">
                                  <xsl:if test="key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='processingInformation'">
                                    <xsl:call-template name="writeEOS_PSA">
                                      <xsl:with-param name="additionalAttributeType" select="'processingInformation'"/>
                                    </xsl:call-template>
                                  </xsl:if>
                                </xsl:for-each>
                              </eos:AdditionalAttributes>
                            </gco:Record>
                          </eos:otherProperty>
                        </xsl:if>
                      </eos:EOS_Processing>
                    </gmi:processingInformation>
                  </xsl:if>
                </gmi:LE_ProcessStep>
              </gmd:processStep>
              <xsl:for-each select="/*/CollectionAssociations/CollectionAssociation[CollectionType='Input']">
                <gmd:source>
                  <gmi:LE_Source>
                    <gmd:description>
                      <xsl:call-template name="writeCharacterString">
                        <xsl:with-param name="stringToWrite" select="CollectionUse"/>
                      </xsl:call-template>
                    </gmd:description>
                    <gmd:sourceCitation>
                      <gmd:CI_Citation>
                        <gmd:title>
                          <xsl:call-template name="writeCharacterString">
                            <xsl:with-param name="stringToWrite" select="ShortName"/>
                          </xsl:call-template>
                        </gmd:title>
                        <gmd:date gco:nilReason="unknown"/>
                        <gmd:edition>
                          <xsl:call-template name="writeCharacterString">
                            <xsl:with-param name="stringToWrite" select="VersionId"/>
                          </xsl:call-template>
                        </gmd:edition>
                      </gmd:CI_Citation>
                    </gmd:sourceCitation>
                  </gmi:LE_Source>
                </gmd:source>
              </xsl:for-each>
              <xsl:for-each select="/*/InputGranules/InputGranule">
                <gmd:source>
                  <gmi:LE_Source id="{generate-id()}">
                    <gmd:sourceCitation>
                      <gmd:CI_Citation>
                        <gmd:title>
                          <gmx:FileName>
                            <xsl:attribute name="src">
                              <xsl:value-of select="."/>
                            </xsl:attribute>
                          </gmx:FileName>
                        </gmd:title>
                        <gmd:date gco:nilReason="unknown"/>
                      </gmd:CI_Citation>
                    </gmd:sourceCitation>
                  </gmi:LE_Source>
                </gmd:source>
              </xsl:for-each>
            </gmd:LI_Lineage>
          </gmd:lineage>
        </gmd:DQ_DataQuality>
      </gmd:dataQualityInfo>
      <xsl:for-each select="/*/MeasuredParameters/MeasuredParameter">
        <gmd:dataQualityInfo>
          <gmd:DQ_DataQuality>
            <gmd:scope>
              <gmd:DQ_Scope>
                <gmd:level>
                  <xsl:call-template name="writeCodelist">
                    <xsl:with-param name="codeListName" select="'MD_ScopeCode'"/>
                    <xsl:with-param name="codeListValue" select="'attribute'"/>
                  </xsl:call-template>
                </gmd:level>
                <gmd:levelDescription>
                  <gmd:MD_ScopeDescription>
                    <gmd:attributes>
                      <gco:CharacterString>
                        <xsl:value-of select="ParameterName"/>
                      </gco:CharacterString>
                    </gmd:attributes>
                  </gmd:MD_ScopeDescription>
                </gmd:levelDescription>
              </gmd:DQ_Scope>
            </gmd:scope>
            <xsl:for-each select="QAStats/*">
              <gmd:report>
                <gmd:DQ_QuantitativeAttributeAccuracy>
                  <gmd:nameOfMeasure>
                    <xsl:call-template name="writeCharacterString">
                      <xsl:with-param name="stringToWrite" select="name(.)"/>
                    </xsl:call-template>
                  </gmd:nameOfMeasure>
                  <gmd:result>
                    <gmd:DQ_QuantitativeResult>
                      <gmd:valueUnit/>
                      <gmd:value>
                        <gco:Record xsi:type="gco:Real_PropertyType">
                          <gco:Real>
                            <xsl:value-of select="."/>
                          </gco:Real>
                        </gco:Record>
                      </gmd:value>
                    </gmd:DQ_QuantitativeResult>
                  </gmd:result>
                </gmd:DQ_QuantitativeAttributeAccuracy>
              </gmd:report>
            </xsl:for-each>
            <xsl:if test="QAFlags/*[starts-with(name(.),'Automatic')]">
              <gmd:report>
                <gmd:DQ_NonQuantitativeAttributeAccuracy>
                  <gmd:nameOfMeasure>
                    <xsl:call-template name="writeCharacterString">
                      <xsl:with-param name="stringToWrite" select="'AutomaticQualityFlag'"/>
                    </xsl:call-template>
                  </gmd:nameOfMeasure>
                  <gmd:measureDescription>
                    <xsl:call-template name="writeCharacterString">
                      <xsl:with-param name="stringToWrite" select="QAFlags/AutomaticQualityFlagExplanation"/>
                    </xsl:call-template>
                  </gmd:measureDescription>
                  <gmd:result>
                    <gmd:DQ_QuantitativeResult>
                      <gmd:valueUnit/>
                      <gmd:value>
                        <gco:Record xsi:type="gco:CharacterString_PropertyType">
                          <xsl:call-template name="writeCharacterString">
                            <xsl:with-param name="stringToWrite" select="QAFlags/AutomaticQualityFlag"/>
                          </xsl:call-template>
                        </gco:Record>
                      </gmd:value>
                    </gmd:DQ_QuantitativeResult>
                  </gmd:result>
                </gmd:DQ_NonQuantitativeAttributeAccuracy>
              </gmd:report>
            </xsl:if>
            <xsl:if test="QAFlags/*[starts-with(name(.),'Operational')]">
              <gmd:report>
                <gmd:DQ_NonQuantitativeAttributeAccuracy>
                  <gmd:nameOfMeasure>
                    <xsl:call-template name="writeCharacterString">
                      <xsl:with-param name="stringToWrite" select="'OperationalQualityFlag'"/>
                    </xsl:call-template>
                  </gmd:nameOfMeasure>
                  <gmd:measureDescription>
                    <xsl:call-template name="writeCharacterString">
                      <xsl:with-param name="stringToWrite" select="QAFlags/OperationalQualityFlagExplanation"/>
                    </xsl:call-template>
                  </gmd:measureDescription>
                  <gmd:result>
                    <gmd:DQ_QuantitativeResult>
                      <gmd:valueUnit/>
                      <gmd:value>
                        <gco:Record xsi:type="gco:CharacterString_PropertyType">
                          <xsl:call-template name="writeCharacterString">
                            <xsl:with-param name="stringToWrite" select="QAFlags/OperationalQualityFlag"/>
                          </xsl:call-template>
                        </gco:Record>
                      </gmd:value>
                    </gmd:DQ_QuantitativeResult>
                  </gmd:result>
                </gmd:DQ_NonQuantitativeAttributeAccuracy>
              </gmd:report>
            </xsl:if>
            <xsl:if test="QAFlags/*[starts-with(name(.),'Science')]">
              <gmd:report>
                <gmd:DQ_NonQuantitativeAttributeAccuracy>
                  <gmd:nameOfMeasure>
                    <xsl:call-template name="writeCharacterString">
                      <xsl:with-param name="stringToWrite" select="'ScienceQualityFlag'"/>
                    </xsl:call-template>
                  </gmd:nameOfMeasure>
                  <gmd:measureDescription>
                    <xsl:call-template name="writeCharacterString">
                      <xsl:with-param name="stringToWrite" select="QAFlags/ScienceQualityFlagExplanation"/>
                    </xsl:call-template>
                  </gmd:measureDescription>
                  <gmd:result>
                    <gmd:DQ_QuantitativeResult>
                      <gmd:valueUnit/>
                      <gmd:value>
                        <gco:Record xsi:type="gco:CharacterString_PropertyType">
                          <xsl:call-template name="writeCharacterString">
                            <xsl:with-param name="stringToWrite" select="QAFlags/ScienceQualityFlag"/>
                          </xsl:call-template>
                        </gco:Record>
                      </gmd:value>
                    </gmd:DQ_QuantitativeResult>
                  </gmd:result>
                </gmd:DQ_NonQuantitativeAttributeAccuracy>
              </gmd:report>
            </xsl:if>
          </gmd:DQ_DataQuality>
        </gmd:dataQualityInfo>
      </xsl:for-each>
      <gmd:metadataMaintenance>
        <gmd:MD_MaintenanceInformation>
          <gmd:maintenanceAndUpdateFrequency>
            <xsl:call-template name="writeCodelist">
              <xsl:with-param name="codeListName" select="'MD_MaintenanceFrequencyCode'"/>
              <xsl:with-param name="codeListValue" select="'irregular'"/>
            </xsl:call-template>
          </gmd:maintenanceAndUpdateFrequency>
          <gmd:maintenanceNote>
            <gco:CharacterString>
              <xsl:value-of select="concat('Translated from ECHO using ',$translationName,' Version: ',$translationVersion)"/>
            </gco:CharacterString>
          </gmd:maintenanceNote>
        </gmd:MD_MaintenanceInformation>
      </gmd:metadataMaintenance>
      <gmi:acquisitionInformation>
        <!-- The gmi:MI_AcquisitionInformation element can hold all extended types -->
        <xsl:element name="gmi:MI_AcquisitionInformation">
          <xsl:call-template name="writeInstrument"/>
          <xsl:call-template name="writeOperation"/>
          <xsl:call-template name="writePlatform"/>
          <xsl:if test="count(//Platforms/Platform) = 0 and (//OrbitCalculatedSpatialDomains or //Spatial/OrbitParameters or //Spatial/HorizontalSpatialDomain/Orbit)">
            <gmi:platform>
              <eos:EOS_Platform>
                <gmi:identifier gco:nilReason="missing"/>
                <gmi:description gco:nilReason="missing"/>
                <gmi:instrument gco:nilReason="missing"/>
                <xsl:call-template name="writePlatformAdditionalAttributes"/>
              </eos:EOS_Platform>
            </gmi:platform>
          </xsl:if>
        </xsl:element>
      </gmi:acquisitionInformation>
      <!--</xsl:if>-->
    </gmi:MI_Metadata>
  </xsl:template>
  <!-- suppress default with this template -->
  <xsl:template match="text()"/>
  <xsl:template name="contact2pointOfContact">
    <xsl:param name="roleName"/>
    <xsl:param name="roleCode"/>
    <xsl:choose>
      <xsl:when test="count(ContactPersons/ContactPerson)">
        <xsl:for-each select="ContactPersons/ContactPerson">
          <!-- Each CI_ResponsibleParty can have just one person -->
          <xsl:element name="{$roleName}">
            <gmd:CI_ResponsibleParty>
              <gmd:individualName>
                <xsl:call-template name="writeCharacterString">
                  <xsl:with-param name="stringToWrite" select="concat(./FirstName,' ',./MiddleName,' ',./LastName)"/>
                </xsl:call-template>
              </gmd:individualName>
              <gmd:organisationName>
                <xsl:call-template name="writeCharacterString">
                  <xsl:with-param name="stringToWrite" select="../../OrganizationName"/>
                </xsl:call-template>
              </gmd:organisationName>
              <gmd:positionName>
                <xsl:call-template name="writeCharacterString">
                  <xsl:with-param name="stringToWrite" select="./JobPosition"/>
                </xsl:call-template>
              </gmd:positionName>
              <gmd:contactInfo>
                <gmd:CI_Contact>
                  <gmd:phone>
                    <gmd:CI_Telephone>
                      <xsl:for-each select="../../OrganizationPhones/Phone[Type != 'fax']">
                        <gmd:voice>
                          <xsl:call-template name="writeCharacterString">
                            <xsl:with-param name="stringToWrite" select="Number"/>
                          </xsl:call-template>
                        </gmd:voice>
                      </xsl:for-each>
                      <xsl:for-each select="../../OrganizationPhones/Phone[Type = 'fax']">
                        <gmd:facsimile>
                          <xsl:call-template name="writeCharacterString">
                            <xsl:with-param name="stringToWrite" select="Number"/>
                          </xsl:call-template>
                        </gmd:facsimile>
                      </xsl:for-each>
                    </gmd:CI_Telephone>
                  </gmd:phone>
                  <gmd:address>
                    <gmd:CI_Address>
                      <gmd:deliveryPoint>
                        <xsl:call-template name="writeCharacterString">
                          <xsl:with-param name="stringToWrite" select="../../OrganizationAddresses/Address[1]/StreetAddress"/>
                        </xsl:call-template>
                      </gmd:deliveryPoint>
                      <gmd:city>
                        <xsl:call-template name="writeCharacterString">
                          <xsl:with-param name="stringToWrite" select="../../OrganizationAddresses/Address[1]/City"/>
                        </xsl:call-template>
                      </gmd:city>
                      <gmd:administrativeArea>
                        <xsl:call-template name="writeCharacterString">
                          <xsl:with-param name="stringToWrite" select="../../OrganizationAddresses/Address[1]/StateProvince"/>
                        </xsl:call-template>
                      </gmd:administrativeArea>
                      <gmd:postalCode>
                        <xsl:call-template name="writeCharacterString">
                          <xsl:with-param name="stringToWrite" select="../../OrganizationAddresses/Address[1]/PostalCode"/>
                        </xsl:call-template>
                      </gmd:postalCode>
                      <gmd:country>
                        <xsl:call-template name="writeCharacterString">
                          <xsl:with-param name="stringToWrite" select="../../OrganizationAddresses/Address[1]/Country"/>
                        </xsl:call-template>
                      </gmd:country>
                      <gmd:electronicMailAddress>
                        <xsl:call-template name="writeCharacterString">
                          <xsl:with-param name="stringToWrite" select="../..//OrganizationEmails/Email[1]"/>
                        </xsl:call-template>
                      </gmd:electronicMailAddress>
                    </gmd:CI_Address>
                  </gmd:address>
                  <gmd:hoursOfService>
                    <xsl:call-template name="writeCharacterString">
                      <xsl:with-param name="stringToWrite" select="../../HoursOfService"/>
                    </xsl:call-template>
                  </gmd:hoursOfService>
                  <gmd:contactInstructions>
                    <xsl:call-template name="writeCharacterString">
                      <xsl:with-param name="stringToWrite" select="../../Instructions"/>
                    </xsl:call-template>
                  </gmd:contactInstructions>
                </gmd:CI_Contact>
              </gmd:contactInfo>
              <gmd:role>
                <xsl:call-template name="writeCodelist">
                  <xsl:with-param name="codeListName" select="'CI_RoleCode'"/>
                  <xsl:with-param name="codeListValue" select="$roleCode"/>
                </xsl:call-template>
              </gmd:role>
            </gmd:CI_ResponsibleParty>
          </xsl:element>
        </xsl:for-each>
      </xsl:when>
      <xsl:otherwise>
        <!-- Just organization -->
        <xsl:element name="{$roleName}">
          <gmd:CI_ResponsibleParty>
            <gmd:organisationName>
              <xsl:call-template name="writeCharacterString">
                <xsl:with-param name="stringToWrite" select="OrganizationName"/>
              </xsl:call-template>
            </gmd:organisationName>
            <gmd:positionName>
              <xsl:call-template name="writeCharacterString">
                <xsl:with-param name="stringToWrite" select="JobPosition"/>
              </xsl:call-template>
            </gmd:positionName>
            <gmd:contactInfo>
              <gmd:CI_Contact>
                <gmd:phone>
                  <gmd:CI_Telephone>
                    <xsl:for-each select="OrganizationPhones/Phone[Type != 'fax']">
                      <gmd:voice>
                        <xsl:call-template name="writeCharacterString">
                          <xsl:with-param name="stringToWrite" select="Number"/>
                        </xsl:call-template>
                      </gmd:voice>
                    </xsl:for-each>
                    <xsl:for-each select="OrganizationPhones/Phone[Type = 'fax']">
                      <gmd:facsimile>
                        <xsl:call-template name="writeCharacterString">
                          <xsl:with-param name="stringToWrite" select="Number"/>
                        </xsl:call-template>
                      </gmd:facsimile>
                    </xsl:for-each>
                  </gmd:CI_Telephone>
                </gmd:phone>
                <gmd:address>
                  <gmd:CI_Address>
                    <gmd:deliveryPoint>
                      <xsl:call-template name="writeCharacterString">
                        <xsl:with-param name="stringToWrite" select="OrganizationAddresses/Address[1]/StreetAddress"/>
                      </xsl:call-template>
                    </gmd:deliveryPoint>
                    <gmd:city>
                      <xsl:call-template name="writeCharacterString">
                        <xsl:with-param name="stringToWrite" select="OrganizationAddresses/Address[1]/City"/>
                      </xsl:call-template>
                    </gmd:city>
                    <gmd:administrativeArea>
                      <xsl:call-template name="writeCharacterString">
                        <xsl:with-param name="stringToWrite" select="OrganizationAddresses/Address[1]/StateProvince"/>
                      </xsl:call-template>
                    </gmd:administrativeArea>
                    <gmd:postalCode>
                      <xsl:call-template name="writeCharacterString">
                        <xsl:with-param name="stringToWrite" select="OrganizationAddresses/Address[1]/PostalCode"/>
                      </xsl:call-template>
                    </gmd:postalCode>
                    <gmd:country>
                      <xsl:call-template name="writeCharacterString">
                        <xsl:with-param name="stringToWrite" select="OrganizationAddresses/Address[1]/Country"/>
                      </xsl:call-template>
                    </gmd:country>
                    <gmd:electronicMailAddress>
                      <xsl:call-template name="writeCharacterString">
                        <xsl:with-param name="stringToWrite" select="OrganizationEmails/Email[1]"/>
                      </xsl:call-template>
                    </gmd:electronicMailAddress>
                  </gmd:CI_Address>
                </gmd:address>
                <gmd:hoursOfService>
                  <xsl:call-template name="writeCharacterString">
                    <xsl:with-param name="stringToWrite" select="HoursOfService"/>
                  </xsl:call-template>
                </gmd:hoursOfService>
                <gmd:contactInstructions>
                  <xsl:call-template name="writeCharacterString">
                    <xsl:with-param name="stringToWrite" select="Instructions"/>
                  </xsl:call-template>
                </gmd:contactInstructions>
              </gmd:CI_Contact>
            </gmd:contactInfo>
            <gmd:role>
              <xsl:call-template name="writeCodelist">
                <xsl:with-param name="codeListName" select="'CI_RoleCode'"/>
                <xsl:with-param name="codeListValue" select="$roleCode"/>
              </xsl:call-template>
            </gmd:role>
          </gmd:CI_ResponsibleParty>
        </xsl:element>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template name="writeCharacterString">
    <xsl:param name="stringToWrite"/>
    <xsl:choose>
      <xsl:when test="$stringToWrite">
        <gco:CharacterString>
          <xsl:value-of select="normalize-space($stringToWrite)"/>
        </gco:CharacterString>
      </xsl:when>
      <xsl:otherwise>
        <xsl:attribute name="gco:nilReason">
          <xsl:value-of select="'missing'"/>
        </xsl:attribute>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template name="writeCodelist">
    <xsl:param name="codeListName"/>
    <xsl:param name="codeListValue"/>
    <xsl:variable name="codeListLocation" select="'http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml'"/>
    <xsl:element name="{concat('gmd:',$codeListName)}">
      <xsl:attribute name="codeList">
        <xsl:value-of select="$codeListLocation"/>
        <xsl:value-of select="'#'"/>
        <xsl:value-of select="$codeListName"/>
      </xsl:attribute>
      <xsl:attribute name="codeListValue">
        <xsl:value-of select="$codeListValue"/>
      </xsl:attribute>
      <xsl:value-of select="$codeListValue"/>
    </xsl:element>
  </xsl:template>
  <xsl:template name="writeCharacteristicList">
    <xsl:choose>
      <xsl:when test="$recordType='SWEDataArray'">
        <xsl:call-template name="writeCharacteristicListAsSWEDataArray"/>
      </xsl:when>
      <xsl:when test="$recordType='NcML'">
        <xsl:call-template name="writeCharacteristicListAsNcML"/>
      </xsl:when>
      <xsl:when test="$recordType='JSON'">
        <xsl:call-template name="writeCharacteristicListAsJSON"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:call-template name="writeCharacteristicListAsECHO_PSA"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template name="writeCharacteristicListAsSWEDataArray">
    <eos:otherPropertyType>
      <gco:RecordType xlink:href="http://schemas.opengis.net/sweCommon/2.0/block_components.xsd#xpointer(//element[@name='DataArray'])">SWECommon Data Array</gco:RecordType>
    </eos:otherPropertyType>
    <eos:otherProperty>
      <gco:Record>
        <swe:DataArray>
          <swe:elementCount>
            <swe:Count>
              <swe:value>
                <xsl:value-of select="count(Characteristics/Characteristic)"/>
              </swe:value>
            </swe:Count>
          </swe:elementCount>
          <swe:DataRecord>
            <swe:field name="Name"/>
            <swe:field name="Description"/>
            <swe:field name="DataType"/>
            <swe:field name="Unit"/>
            <swe:field name="Value"/>
          </swe:DataRecord>
          <swe:encoding>
            <swe:TextBlock decimalSeparator="." tokenSeparator="," blockSeparator="&#10;"/>
          </swe:encoding>
          <swe:values>
            <xsl:for-each select="Characteristics/Characteristic">
              <xsl:text>"</xsl:text>
              <xsl:value-of select="Name"/>
              <xsl:text>"</xsl:text>
              <xsl:text>,</xsl:text>
              <xsl:text>"</xsl:text>
              <xsl:value-of select="Description"/>
              <xsl:text>"</xsl:text>
              <xsl:text>,</xsl:text>
              <xsl:text>"</xsl:text>
              <xsl:value-of select="DataType"/>
              <xsl:text>"</xsl:text>
              <xsl:text>,</xsl:text>
              <xsl:text>"</xsl:text>
              <xsl:value-of select="Unit"/>
              <xsl:text>"</xsl:text>
              <xsl:text>,</xsl:text>
              <xsl:text>"</xsl:text>
              <xsl:value-of select="Value"/>
              <xsl:text>"</xsl:text>
              <xsl:text>&#10;</xsl:text>
            </xsl:for-each>
          </swe:values>
        </swe:DataArray>
      </gco:Record>
    </eos:otherProperty>
  </xsl:template>
  <xsl:template name="writeCharacteristicListAsECHO_PSA">
    <eos:otherPropertyType>
      <gco:RecordType xlink:href="http://earthdata.nasa.gov/metadata/schema/eos/1.0/eos.xsd#xpointer(//element[@name='AdditionalAttributes'])">Echo Additional Attributes</gco:RecordType>
    </eos:otherPropertyType>
    <eos:otherProperty>
      <gco:Record>
        <eos:AdditionalAttributes>
          <xsl:for-each select="Characteristics/Characteristic">
            <xsl:call-template name="writeEOS_PSA">
              <xsl:with-param name="additionalAttributeType" select="'sensorInformation'"/>
            </xsl:call-template>
          </xsl:for-each>
        </eos:AdditionalAttributes>
      </gco:Record>
    </eos:otherProperty>
  </xsl:template>
  <xsl:template name="writeEOS_PSA">
    <xsl:param name="additionalAttributeType"/>
    <eos:AdditionalAttribute>
      <eos:reference>
        <eos:EOS_AdditionalAttributeDescription>
          <eos:type>
            <xsl:element name="eos:EOS_AdditionalAttributeTypeCode">
              <xsl:attribute name="codeList" select="'http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeTypeCode'"/>
              <xsl:attribute name="codeListValue" select="$additionalAttributeType"/>
              <xsl:value-of select="$additionalAttributeType"/>
            </xsl:element>
          </eos:type>
          <xsl:element name="eos:name">
            <xsl:call-template name="writeCharacterString">
              <xsl:with-param name="stringToWrite" select="Name"/>
            </xsl:call-template>
          </xsl:element>
          <xsl:if test="Description">
            <eos:description>
              <gco:CharacterString>
                <xsl:value-of select="Description"/>
              </gco:CharacterString>
            </eos:description>
          </xsl:if>
          <xsl:if test="DataType">
            <eos:dataType>
              <xsl:element name="eos:EOS_AdditionalAttributeDataTypeCode">
                <xsl:attribute name="codeList" select="'http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeDataTypeCode'"/>
                <xsl:attribute name="codeListValue" select="DataType"/>
                <xsl:value-of select="DataType"/>
              </xsl:element>
            </eos:dataType>
          </xsl:if>
          <xsl:if test="MeasurementResolution">
            <eos:measurementResolution>
              <gco:CharacterString>
                <xsl:value-of select="MeasurementResolution"/>
              </gco:CharacterString>
            </eos:measurementResolution>
          </xsl:if>
          <xsl:if test="ParameterRangeBegin">
            <eos:parameterRangeBegin>
              <gco:CharacterString>
                <xsl:value-of select="ParameterRangeBegin"/>
              </gco:CharacterString>
            </eos:parameterRangeBegin>
          </xsl:if>
          <xsl:if test="ParameterRangeEnd">
            <eos:parameterRangeEnd>
              <gco:CharacterString>
                <xsl:value-of select="ParameterRangeEnd"/>
              </gco:CharacterString>
            </eos:parameterRangeEnd>
          </xsl:if>
          <xsl:if test="ParameterUnitsOfMeasure | Unit">
            <eos:parameterUnitsOfMeasure>
              <gco:CharacterString>
                <xsl:value-of select="ParameterUnitsOfMeasure | Unit"/>
              </gco:CharacterString>
            </eos:parameterUnitsOfMeasure>
          </xsl:if>
          <xsl:if test="ParameterValueAccuracy">
            <eos:parameterValueAccuracy>
              <gco:CharacterString>
                <xsl:value-of select="ParameterValueAccuracy"/>
              </gco:CharacterString>
            </eos:parameterValueAccuracy>
          </xsl:if>
          <xsl:if test="ValueAccuracyExplanation">
            <eos:valueAccuracyExplanation>
              <gco:CharacterString>
                <xsl:value-of select="ValueAccuracyExplanation"/>
              </gco:CharacterString>
            </eos:valueAccuracyExplanation>
          </xsl:if>
        </eos:EOS_AdditionalAttributeDescription>
      </eos:reference>
      <xsl:for-each select="Value | Values/Value">
        <xsl:element name="eos:value">
          <gco:CharacterString>
            <xsl:value-of select="."/>
          </gco:CharacterString>
        </xsl:element>
      </xsl:for-each>
    </eos:AdditionalAttribute>
  </xsl:template>
  <xsl:template name="writeCharacteristicListAsJSON">
    <eos:otherPropertyType>
      <gco:RecordType xlink:href="http://json.org">JSON Description</gco:RecordType>
    </eos:otherPropertyType>
    <eos:otherProperty>
      <gco:Record>
        <gco:CharacterString>
          <xsl:text>{"characteristics":[&#10;</xsl:text>
          <xsl:for-each select="Characteristics/Characteristic">
            <xsl:if test="position() != 1">
              <xsl:text>,&#10;</xsl:text>
            </xsl:if>
            <xsl:text>{</xsl:text>
            <xsl:call-template name="writeJsonItem">
              <xsl:with-param name="id" select="'Name'"/>
              <xsl:with-param name="value" select="Name"/>
            </xsl:call-template>
            <xsl:text>,&#10;</xsl:text>
            <xsl:call-template name="writeJsonItem">
              <xsl:with-param name="id" select="'Description'"/>
              <xsl:with-param name="value" select="Description"/>
            </xsl:call-template>
            <xsl:text>,&#10;</xsl:text>
            <xsl:call-template name="writeJsonItem">
              <xsl:with-param name="id" select="'DataType'"/>
              <xsl:with-param name="value" select="DataType"/>
            </xsl:call-template>
            <xsl:text>,&#10;</xsl:text>
            <xsl:call-template name="writeJsonItem">
              <xsl:with-param name="id" select="'Unit'"/>
              <xsl:with-param name="value" select="Unit"/>
            </xsl:call-template>
            <xsl:text>,&#10;</xsl:text>
            <xsl:call-template name="writeJsonItem">
              <xsl:with-param name="id" select="'Value'"/>
              <xsl:with-param name="value" select="Value"/>
            </xsl:call-template>
            <xsl:text>}&#10;</xsl:text>
          </xsl:for-each>
          <xsl:text>]&#10;}</xsl:text>
        </gco:CharacterString>
      </gco:Record>
    </eos:otherProperty>
  </xsl:template>
  <xsl:template name="writeJsonItem">
    <xsl:param name="id"/>
    <xsl:param name="value"/>
    <xsl:value-of select="concat('&quot;',$id,'&quot;:&quot;',$value,'&quot;')"/>
  </xsl:template>
  <xsl:template name="writeCharacteristicListAsNcML">
    <eos:otherPropertyType>
      <gco:RecordType xlink:href="http://www.unidata.ucar.edu/schemas/netcdf/ncml-2.2.xsd#xpointer(//element[@name='group'])">netCDF Group Type</gco:RecordType>
    </eos:otherPropertyType>
    <eos:otherProperty>
      <gco:Record xmlns:nc="http://www.unidata.ucar.edu/schemas/netcdf">
        <xsl:element name="nc:group">
          <xsl:attribute name="name">additional_attributes</xsl:attribute>
          <xsl:for-each select="Characteristics/Characteristic">
            <xsl:element name="nc:variable">
              <xsl:attribute name="name">
                <xsl:value-of select="Name"/>
              </xsl:attribute>
              <xsl:call-template name="writeNcMLItem">
                <xsl:with-param name="id" select="'Description'"/>
                <xsl:with-param name="value" select="Description"/>
              </xsl:call-template>
              <xsl:call-template name="writeNcMLItem">
                <xsl:with-param name="id" select="'DataType'"/>
                <xsl:with-param name="value" select="DataType"/>
              </xsl:call-template>
              <xsl:call-template name="writeNcMLItem">
                <xsl:with-param name="id" select="'Unit'"/>
                <xsl:with-param name="value" select="Unit"/>
              </xsl:call-template>
              <xsl:call-template name="writeNcMLItem">
                <xsl:with-param name="id" select="'Value'"/>
                <xsl:with-param name="value" select="Value"/>
              </xsl:call-template>
            </xsl:element>
          </xsl:for-each>
        </xsl:element>
      </gco:Record>
    </eos:otherProperty>
  </xsl:template>
  <xsl:template name="writeNcMLItem">
    <xsl:param name="id"/>
    <xsl:param name="value"/>
    <xsl:element name="nc:attribute" namespace="www">
      <xsl:attribute name="name">
        <xsl:value-of select="$id"/>
      </xsl:attribute>
      <xsl:attribute name="value">
        <xsl:value-of select="$value"/>
      </xsl:attribute>
    </xsl:element>
  </xsl:template>
  <xsl:template name="ECHOExtentToISO">
    <gmd:extent>
      <xsl:choose>
        <xsl:when test="contains(/*/Spatial,'NO_SPATIAL') and count(/*/Temporal) = 0">
          <xsl:attribute name="gco:nilReason" select="'missing'"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:element name="gmd:EX_Extent">
            <xsl:attribute name="id" select="'boundingExtent'"/>
            <xsl:choose>
              <!-- No spatial contant -->
              <xsl:when test="contains(/*/Spatial,'NO_SPATIAL')"/>
              <xsl:otherwise>
                <!-- spatial content exists -->
                <xsl:call-template name="writeExtentDescription"/>
                <xsl:call-template name="writeExtentBoundingBox"/>
                <xsl:choose>
                  <xsl:when test="//VerticalSpatialDomain[number(Value) != number(Value)]">
                    <gmd:geographicElement>
                      <gmd:EX_GeographicDescription>
                        <gmd:geographicIdentifier>
                          <gmd:MD_Identifier>
                            <gmd:code>
                              <gco:CharacterString>Atmosphere Layer</gco:CharacterString>
                            </gmd:code>
                            <gmd:description>
                              <gco:CharacterString>
                                <xsl:value-of select="concat(//VerticalSpatialDomain[Type='Minimum Altitude']/Value,' to ',//VerticalSpatialDomain[Type='Maximum Altitude']/Value)"/>
                              </gco:CharacterString>
                            </gmd:description>
                          </gmd:MD_Identifier>
                        </gmd:geographicIdentifier>
                      </gmd:EX_GeographicDescription>
                    </gmd:geographicElement>
                  </xsl:when>
                  <xsl:when test="/*/Spatial/SpatialCoverageType = 'Orbit' or /*/Spatial/GranuleSpatialRepresentation = 'ORBIT'">
                    <gmd:geographicElement>
                      <gmd:EX_GeographicDescription>
                        <gmd:geographicIdentifier>
                          <gmd:MD_Identifier>
                            <gmd:code>
                              <gco:CharacterString>Orbit</gco:CharacterString>
                            </gmd:code>
                            <gmd:description>
                              <gco:CharacterString>
                                <xsl:for-each select="/*/Spatial/OrbitParameters/*">
                                  <xsl:if test="position() > 1">
                                    <xsl:text> </xsl:text>
                                  </xsl:if>
                                  <xsl:value-of select="concat(local-name(.),': ',.)"/>
                                </xsl:for-each>
                              </gco:CharacterString>
                            </gmd:description>
                          </gmd:MD_Identifier>
                        </gmd:geographicIdentifier>
                      </gmd:EX_GeographicDescription>
                    </gmd:geographicElement>
                  </xsl:when>
                </xsl:choose>
              </xsl:otherwise>
            </xsl:choose>
            <!-- Check for center points -->
            <xsl:for-each select="/*/Spatial/HorizontalSpatialDomain/Geometry/*/CenterPoint">
              <gmd:geographicElement>
                <xsl:comment select="'CenterPoint'"/>
                <xsl:variable name="centerPointId" select="concat('centerPoint_',position())"/>
                <gmd:EX_BoundingPolygon>
                  <xsl:attribute name="id" select="$centerPointId"/>
                  <gmd:polygon>
                    <gml:Point>
                      <xsl:attribute name="gml:id" select="generate-id()"/>
                      <gml:pos>
                        <xsl:attribute name="srsName" select="'http://www.opengis.net/def/crs/EPSG/4326'"/>
                        <xsl:attribute name="srsDimension" select="'2'"/>
                        <xsl:value-of select="concat(PointLatitude,' ',PointLongitude)"/>
                      </gml:pos>
                    </gml:Point>
                  </gmd:polygon>
                </gmd:EX_BoundingPolygon>
              </gmd:geographicElement>
            </xsl:for-each>
            <!-- Check for spatial information in AdditionalAttributes -->
            <xsl:call-template name="writeGeographicIdentifiers"/>
            <xsl:call-template name="writeExtentTemporalInformation"/>
            <xsl:if test="count(//VerticalSpatialDomain[number(Value) = number(Value)]) > 0 or
              /*/SpatialInfo/VerticalCoordinateSystem/AltitudeSystemDefinition/DatumName">
              <!-- If minimum and maximum altitudes are numeric, write verticalElement -->
              <gmd:verticalElement>
                <gmd:EX_VerticalExtent>
                  <gmd:minimumValue>
                    <gco:Real>
                      <xsl:value-of select="//VerticalSpatialDomain[Type='Minimum Altitude']/Value"/>
                    </gco:Real>
                  </gmd:minimumValue>
                  <gmd:maximumValue>
                    <gco:Real>
                      <xsl:value-of select="//VerticalSpatialDomain[Type='Maximum Altitude']/Value"/>
                    </gco:Real>
                  </gmd:maximumValue>
                  <xsl:if test="/*/SpatialInfo/VerticalCoordinateSystem/AltitudeSystemDefinition/DatumName">
                    <xsl:choose>
                      <xsl:when test="/*/SpatialInfo/VerticalCoordinateSystem/AltitudeSystemDefinition/DatumName='Not Applicable'">
                        <gmd:verticalCRS gco:nilReason="inapplicable"/>
                      </xsl:when>
                      <xsl:otherwise>
                        <gmd:verticalCRS>
                          <gml:ML_VerticalCRS>
                            <xsl:attribute name="gml:id" select="generate-id()"/>
                            <gml:identifier codeSpace="gov.nasa.esdis">missing</gml:identifier>
                            <gml:scope/>
                            <gml:verticalCS/>
                            <gml:verticalDatum>
                              <gml:VerticalDatum>
                                <xsl:attribute name="gml:id" select="generate-id()"/>
                                <gml:identifier codeSpace="gov.nasa.esdis">
                                  <xsl:value-of select="/*/SpatialInfo/VerticalCoordinateSystem/AltitudeSystemDefinition/DatumName"/>
                                </gml:identifier>
                                <gml:scope/>
                              </gml:VerticalDatum>
                            </gml:verticalDatum>
                            <gml:alternativeExpression/>
                          </gml:ML_VerticalCRS>
                        </gmd:verticalCRS>
                      </xsl:otherwise>
                    </xsl:choose>
                  </xsl:if>
                </gmd:EX_VerticalExtent>
              </gmd:verticalElement>
            </xsl:if>
          </xsl:element>
        </xsl:otherwise>
      </xsl:choose>
    </gmd:extent>
  </xsl:template>
  <xsl:template name="writeExtentDescription">
    <gmd:description>
      <xsl:choose>
        <!-- The extent description is a catch-all for a variety of textual spatial elements -->
        <xsl:when test="/*/Spatial/SpatialCoverageType | /*/SpatialInfo/SpatialCoverageType
          | /*/Spatial/GranuleSpatialRepresentation | /*/Temporal/TemporalRangeType
          | /*/Temporal/TimeType | /*/Spatial/VerticalSpatialDomains/VerticalSpatialDomain">
          <xsl:variable name="extentDescription" as="xs:string+">
            <xsl:for-each select="/*/Spatial/SpatialCoverageType">
              <xsl:sequence select="concat('SpatialCoverageType=',.)"/>
            </xsl:for-each>
            <xsl:for-each select="/*/SpatialInfo/SpatialCoverageType">
              <xsl:sequence select="concat('SpatialInfoCoverageType=',.)"/>
            </xsl:for-each>
            <xsl:for-each select="/*/Spatial/GranuleSpatialRepresentation">
              <xsl:sequence select="concat('SpatialGranuleSpatialRepresentation=',.)"/>
            </xsl:for-each>
            <xsl:for-each select="/*/Temporal/TemporalRangeType">
              <xsl:sequence select="concat('Temporal Range Type=',.)"/>
            </xsl:for-each>
            <xsl:for-each select="/*/Temporal/TimeType">
              <xsl:sequence select="concat('Time Type=',.)"/>
            </xsl:for-each>
            <xsl:for-each select="/*/Spatial/VerticalSpatialDomains/VerticalSpatialDomain">
              <xsl:sequence select="concat('VerticalSpatialDomainType=',Type)"/>
              <xsl:sequence select="concat('VerticalSpatialDomainValue=',Value)"/>
            </xsl:for-each>
          </xsl:variable>
          <gco:CharacterString>
            <xsl:value-of select="$extentDescription" separator=", "/>
          </gco:CharacterString>
        </xsl:when>
        <xsl:otherwise>
          <xsl:attribute name="gco:nilReason" select="'unknown'"/>
        </xsl:otherwise>
      </xsl:choose>
    </gmd:description>
  </xsl:template>
  <xsl:template name="writeExtentBoundingBox">
    <xsl:for-each select="/*/Spatial/HorizontalSpatialDomain/Geometry/Point">
      <gmd:geographicElement>
        <xsl:comment select="'Point Bounds'"/>
        <gmd:EX_GeographicBoundingBox>
          <gmd:westBoundLongitude>
            <gco:Decimal>
              <xsl:value-of select="PointLongitude"/>
            </gco:Decimal>
          </gmd:westBoundLongitude>
          <gmd:eastBoundLongitude>
            <gco:Decimal>
              <xsl:value-of select="PointLongitude"/>
            </gco:Decimal>
          </gmd:eastBoundLongitude>
          <gmd:southBoundLatitude>
            <gco:Decimal>
              <xsl:value-of select="PointLatitude"/>
            </gco:Decimal>
          </gmd:southBoundLatitude>
          <gmd:northBoundLatitude>
            <gco:Decimal>
              <xsl:value-of select="PointLatitude"/>
            </gco:Decimal>
          </gmd:northBoundLatitude>
        </gmd:EX_GeographicBoundingBox>
      </gmd:geographicElement>
      <gmd:geographicElement>
        <xsl:comment select="'Point'"/>
        <gmd:EX_BoundingPolygon>
          <gmd:polygon>
            <gml:Point>
              <xsl:attribute name="gml:id" select="generate-id()"/>
              <gml:pos>
                <xsl:attribute name="srsName" select="'http://www.opengis.net/def/crs/EPSG/4326'"/>
                <xsl:attribute name="srsDimension" select="'2'"/>
                <xsl:value-of select="concat(PointLatitude,' ',PointLongitude)"/>
              </gml:pos>
            </gml:Point>
          </gmd:polygon>
        </gmd:EX_BoundingPolygon>
      </gmd:geographicElement>
    </xsl:for-each>
    <xsl:for-each select="/*/Spatial/HorizontalSpatialDomain/Geometry/Line">
      <gmd:geographicElement>
        <xsl:comment>Line Bounds</xsl:comment>
        <gmd:EX_GeographicBoundingBox>
          <gmd:westBoundLongitude>
            <gco:Decimal>
              <xsl:value-of select="min(Point/PointLongitude)"/>
            </gco:Decimal>
          </gmd:westBoundLongitude>
          <gmd:eastBoundLongitude>
            <gco:Decimal>
              <xsl:value-of select="max(Point/PointLongitude)"/>
            </gco:Decimal>
          </gmd:eastBoundLongitude>
          <gmd:southBoundLatitude>
            <gco:Decimal>
              <xsl:value-of select="min(Point/PointLatitude)"/>
            </gco:Decimal>
          </gmd:southBoundLatitude>
          <gmd:northBoundLatitude>
            <gco:Decimal>
              <xsl:value-of select="max(Point/PointLatitude)"/>
            </gco:Decimal>
          </gmd:northBoundLatitude>
        </gmd:EX_GeographicBoundingBox>
      </gmd:geographicElement>
      <gmd:geographicElement>
        <xsl:comment>Line</xsl:comment>
        <gmd:EX_BoundingPolygon>
          <gmd:polygon>
            <gml:LineString>
              <xsl:attribute name="gml:id" select="generate-id()"/>
              <gml:posList>
                <xsl:attribute name="srsName" select="'http://www.opengis.net/def/crs/EPSG/4326'"/>
                <xsl:attribute name="srsDimension" select="'2'"/>
                <xsl:for-each select="Point">
                  <xsl:value-of select="concat(PointLatitude,' ',PointLongitude)"/>
                  <xsl:if test="position()!=last()">
                    <xsl:text> </xsl:text>
                  </xsl:if>
                </xsl:for-each>
              </gml:posList>
            </gml:LineString>
          </gmd:polygon>
        </gmd:EX_BoundingPolygon>
      </gmd:geographicElement>
    </xsl:for-each>
    <xsl:for-each select="/*/Spatial/HorizontalSpatialDomain/Geometry/GPolygon">
      <xsl:for-each select="Boundary">
        <gmd:geographicElement>
          <xsl:comment select="'Polygon Bounds'"/>
          <gmd:EX_GeographicBoundingBox>
            <gmd:westBoundLongitude>
              <gco:Decimal>
                <xsl:value-of select="min(Point/PointLongitude)"/>
              </gco:Decimal>
            </gmd:westBoundLongitude>
            <gmd:eastBoundLongitude>
              <gco:Decimal>
                <xsl:value-of select="max(Point/PointLongitude)"/>
              </gco:Decimal>
            </gmd:eastBoundLongitude>
            <gmd:southBoundLatitude>
              <gco:Decimal>
                <xsl:value-of select="min(Point/PointLatitude)"/>
              </gco:Decimal>
            </gmd:southBoundLatitude>
            <gmd:northBoundLatitude>
              <gco:Decimal>
                <xsl:value-of select="max(Point/PointLatitude)"/>
              </gco:Decimal>
            </gmd:northBoundLatitude>
          </gmd:EX_GeographicBoundingBox>
        </gmd:geographicElement>
      </xsl:for-each>
      <gmd:geographicElement>
        <xsl:comment select="'Polygon'"/>
        <gmd:EX_BoundingPolygon>
          <gmd:polygon>
            <gml:Polygon>
              <xsl:attribute name="gml:id" select="generate-id()"/>
              <xsl:for-each select="Boundary">
                <xsl:element name="gml:exterior">
                  <gml:LinearRing>
                    <gml:posList>
                      <xsl:attribute name="srsName" select="'http://www.opengis.net/def/crs/EPSG/4326'"/>
                      <xsl:attribute name="srsDimension" select="'2'"/>
                      <xsl:for-each select="Point | Boundary/Point">
                        <xsl:value-of select="concat(PointLatitude,' ',PointLongitude)"/>
                        <xsl:if test="position()!=last()">
                          <xsl:text> </xsl:text>
                        </xsl:if>
                      </xsl:for-each>
                    </gml:posList>
                  </gml:LinearRing>
                </xsl:element>
              </xsl:for-each>
              <xsl:for-each select="ExclusiveZone">
                <xsl:element name="gml:interior">
                  <gml:LinearRing>
                    <gml:posList>
                      <xsl:attribute name="srsName" select="'http://www.opengis.net/def/crs/EPSG/4326'"/>
                      <xsl:attribute name="srsDimension" select="'2'"/>
                      <xsl:for-each select="Point | Boundary/Point">
                        <xsl:value-of select="concat(PointLatitude,' ',PointLongitude)"/>
                        <xsl:if test="position()!=last()">
                          <xsl:text> </xsl:text>
                        </xsl:if>
                      </xsl:for-each>
                    </gml:posList>
                  </gml:LinearRing>
                </xsl:element>
              </xsl:for-each>
            </gml:Polygon>
          </gmd:polygon>
        </gmd:EX_BoundingPolygon>
      </gmd:geographicElement>
    </xsl:for-each>
    <xsl:for-each select="/*/Spatial/HorizontalSpatialDomain/Geometry/BoundingRectangle">
      <gmd:geographicElement>
        <xsl:comment select="'Bounding Rectangle'"/>
        <gmd:EX_GeographicBoundingBox>
          <gmd:westBoundLongitude>
            <gco:Decimal>
              <xsl:value-of select="WestBoundingCoordinate"/>
            </gco:Decimal>
          </gmd:westBoundLongitude>
          <gmd:eastBoundLongitude>
            <gco:Decimal>
              <xsl:value-of select="EastBoundingCoordinate"/>
            </gco:Decimal>
          </gmd:eastBoundLongitude>
          <gmd:southBoundLatitude>
            <gco:Decimal>
              <xsl:value-of select="SouthBoundingCoordinate"/>
            </gco:Decimal>
          </gmd:southBoundLatitude>
          <gmd:northBoundLatitude>
            <gco:Decimal>
              <xsl:value-of select="NorthBoundingCoordinate"/>
            </gco:Decimal>
          </gmd:northBoundLatitude>
        </gmd:EX_GeographicBoundingBox>
      </gmd:geographicElement>
    </xsl:for-each>
  </xsl:template>
  <xsl:template name="writeGeographicIdentifiers">
    <xsl:if test="$geographicIdentifierCount > 0">
      <xsl:for-each select="/*/AdditionalAttributes/AdditionalAttribute">
        <xsl:if test="key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='geographicIdentifier'">
          <xsl:choose>
            <xsl:when test="Values/Value | Value">
              <xsl:for-each select="Values/Value | Value">
                <gmd:geographicElement>
                  <xsl:comment select="'Additional Attribute Identifier'"/>
                  <gmd:EX_GeographicDescription>
                    <gmd:geographicIdentifier>
                      <gmd:MD_Identifier>
                        <gmd:code>
                          <gco:CharacterString>
                            <xsl:value-of select="."/>
                          </gco:CharacterString>
                        </gmd:code>
                        <gmd:codeSpace>
                          <xsl:call-template name="writeCharacterString">
                            <xsl:with-param name="stringToWrite" select="'gov.nasa.echo'"/>
                          </xsl:call-template>
                        </gmd:codeSpace>
                        <gmd:description>
                          <gco:CharacterString>
                            <xsl:value-of select="../../Name | ../Name"/>
                            <xsl:if test="../../Description | ../Description">
                              <xsl:value-of select="concat(' - ',../../Description | ../Description)"/>
                            </xsl:if>
                          </gco:CharacterString>
                        </gmd:description>
                      </gmd:MD_Identifier>
                    </gmd:geographicIdentifier>
                  </gmd:EX_GeographicDescription>
                </gmd:geographicElement>
              </xsl:for-each>
            </xsl:when>
            <xsl:otherwise>
              <gmd:geographicElement>
                <gmd:EX_GeographicDescription>
                  <gmd:geographicIdentifier>
                    <gmd:MD_Identifier>
                      <xsl:choose>
                        <xsl:when test="count(ParameterRangeBegin | ParameterRangeEnd) = 2">
                          <gmd:code>
                            <gco:CharacterString>
                              <xsl:value-of select="concat(ParameterRangeBegin,' - ',ParameterRangeEnd)"/>
                            </gco:CharacterString>
                          </gmd:code>
                          <gmd:codeSpace>
                            <xsl:call-template name="writeCharacterString">
                              <xsl:with-param name="stringToWrite" select="'gov.nasa.echo'"/>
                            </xsl:call-template>
                          </gmd:codeSpace>
                        </xsl:when>
                        <xsl:otherwise>
                          <gmd:code gco:nilReason="missing"/>
                        </xsl:otherwise>
                      </xsl:choose>
                      <gmd:description>
                        <gco:CharacterString>
                          <xsl:value-of select="concat(Name,' - ',Description)"/>
                        </gco:CharacterString>
                      </gmd:description>
                    </gmd:MD_Identifier>
                  </gmd:geographicIdentifier>
                </gmd:EX_GeographicDescription>
              </gmd:geographicElement>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
    <xsl:if test="/*/Spatial/HorizontalSpatialDomain/ZoneIdentifier">
      <gmd:geographicElement>
        <gmd:EX_GeographicDescription>
          <gmd:geographicIdentifier>
            <gmd:MD_Identifier>
              <gmd:code>
                <gco:CharacterString>
                  <xsl:value-of select="/*/Spatial/HorizontalSpatialDomain/ZoneIdentifier"/>
                </gco:CharacterString>
              </gmd:code>
              <gmd:description>
                <gco:CharacterString>ZoneIdentifier</gco:CharacterString>
              </gmd:description>
            </gmd:MD_Identifier>
          </gmd:geographicIdentifier>
        </gmd:EX_GeographicDescription>
      </gmd:geographicElement>
    </xsl:if>
    <xsl:for-each select="//TwoDCoordinateSystem">
      <xsl:call-template name="writeTilesAsIdentifier"/>
    </xsl:for-each>
  </xsl:template>
  <xsl:template name="writeTilesAsIdentifier">
    <xsl:choose>
      <xsl:when test="TwoDCoordinateSystemName='CALIPSO'">
        <gmd:geographicElement>
          <gmd:EX_GeographicDescription>
            <gmd:geographicIdentifier>
              <gmd:MD_Identifier>
                <gmd:code>
                  <gco:CharacterString>
                    <xsl:value-of select="concat('o',StartCoordinate1 | Coordinate1/MinimumValue)"/>
                    <xsl:if test="EndCoordinate1  | Coordinate1/MaximumValue">
                      <xsl:value-of select="concat(',',EndCoordinate1 | Coordinate1/MaximumValue)"/>
                    </xsl:if>
                    <xsl:value-of select="concat('p',StartCoordinate2 | Coordinate2/MinimumValue)"/>
                    <xsl:if test="EndCoordinate2 | Coordinate2/MaximumValue">
                      <xsl:value-of select="concat(',',EndCoordinate2 | Coordinate2/MaximumValue)"/>
                    </xsl:if>
                  </gco:CharacterString>
                </gmd:code>
                <gmd:description>
                  <gco:CharacterString>CALIPSO</gco:CharacterString>
                </gmd:description>
              </gmd:MD_Identifier>
            </gmd:geographicIdentifier>
          </gmd:EX_GeographicDescription>
        </gmd:geographicElement>
      </xsl:when>
      <xsl:when test="TwoDCoordinateSystemName='MISR'">
        <gmd:geographicElement>
          <gmd:EX_GeographicDescription>
            <gmd:geographicIdentifier>
              <gmd:MD_Identifier>
                <gmd:code>
                  <gco:CharacterString>
                    <xsl:value-of select="concat('p',StartCoordinate1 | Coordinate1/MinimumValue)"/>
                    <xsl:if test="EndCoordinate1 | Coordinate1/MaximumValue">
                      <xsl:value-of select="concat('-',EndCoordinate1 | Coordinate1/MaximumValue)"/>
                    </xsl:if>
                    <xsl:value-of select="concat('b',StartCoordinate2 | Coordinate2/MinimumValue)"/>
                    <xsl:if test="EndCoordinate2 | Coordinate2/MaximumValue">
                      <xsl:value-of select="concat('-',EndCoordinate2 | Coordinate2/MaximumValue)"/>
                    </xsl:if>
                  </gco:CharacterString>
                </gmd:code>
                <gmd:description>
                  <gco:CharacterString>MISR</gco:CharacterString>
                </gmd:description>
              </gmd:MD_Identifier>
            </gmd:geographicIdentifier>
          </gmd:EX_GeographicDescription>
        </gmd:geographicElement>
      </xsl:when>
      <xsl:when test="contains(TwoDCoordinateSystemName,'MODIS')">
        <gmd:geographicElement>
          <gmd:EX_GeographicDescription>
            <gmd:geographicIdentifier>
              <gmd:MD_Identifier>
                <gmd:code>
                  <gco:CharacterString>
                    <xsl:value-of select="concat('h',StartCoordinate1 | Coordinate1/MinimumValue)"/>
                    <xsl:if test="EndCoordinate1 | Coordinate1/MaximumValue">
                      <xsl:value-of select="concat('-',EndCoordinate1 | Coordinate1/MaximumValue)"/>
                    </xsl:if>
                    <xsl:value-of select="concat('v',StartCoordinate2 | Coordinate2/MinimumValue)"/>
                    <xsl:if test="EndCoordinate2 | Coordinate2/MaximumValue">
                      <xsl:value-of select="concat('-',EndCoordinate2 | Coordinate2/MaximumValue)"/>
                    </xsl:if>
                  </gco:CharacterString>
                </gmd:code>
                <gmd:description>
                  <gco:CharacterString>
                    <xsl:value-of select="TwoDCoordinateSystemName"/>
                  </gco:CharacterString>
                </gmd:description>
              </gmd:MD_Identifier>
            </gmd:geographicIdentifier>
          </gmd:EX_GeographicDescription>
        </gmd:geographicElement>
      </xsl:when>
      <xsl:when test="contains(TwoDCoordinateSystemName,'WRS')">
        <gmd:geographicElement>
          <gmd:EX_GeographicDescription>
            <gmd:geographicIdentifier>
              <gmd:MD_Identifier>
                <gmd:code>
                  <gco:CharacterString>
                    <xsl:value-of select="concat('p',StartCoordinate1 | Coordinate1/MinimumValue)"/>
                    <xsl:if test="EndCoordinate1 | Coordinate1/MaximumValue">
                      <xsl:value-of select="concat('-',EndCoordinate1 | Coordinate1/MaximumValue)"/>
                    </xsl:if>
                    <xsl:value-of select="concat('r',StartCoordinate2 | Coordinate2/MinimumValue)"/>
                    <xsl:if test="EndCoordinate2 | Coordinate2/MaximumValue">
                      <xsl:value-of select="concat('-',EndCoordinate2 | Coordinate2/MaximumValue)"/>
                    </xsl:if>
                  </gco:CharacterString>
                </gmd:code>
                <gmd:description>
                  <gco:CharacterString>
                    <xsl:value-of select="TwoDCoordinateSystemName"/>
                  </gco:CharacterString>
                </gmd:description>
              </gmd:MD_Identifier>
            </gmd:geographicIdentifier>
          </gmd:EX_GeographicDescription>
        </gmd:geographicElement>
      </xsl:when>
      <xsl:otherwise>
        <gmd:geographicElement>
          <gmd:EX_GeographicDescription>
            <gmd:geographicIdentifier>
              <gmd:MD_Identifier>
                <gmd:code>
                  <gco:CharacterString>
                    <xsl:value-of select="concat('x',StartCoordinate1 | Coordinate1/MinimumValue)"/>
                    <xsl:if test="EndCoordinate1 | Coordinate1/MaximumValue">
                      <xsl:value-of select="concat('-',EndCoordinate1 | Coordinate1/MaximumValue)"/>
                    </xsl:if>
                    <xsl:value-of select="concat('y',StartCoordinate2 | Coordinate2/MinimumValue)"/>
                    <xsl:if test="EndCoordinate2 | Coordinate2/MaximumValue">
                      <xsl:value-of select="concat('-',EndCoordinate2 | Coordinate2/MaximumValue)"/>
                    </xsl:if>
                  </gco:CharacterString>
                </gmd:code>
                <gmd:description>
                  <gco:CharacterString>
                    <xsl:value-of select="'Unknown Tiling System'"/>
                  </gco:CharacterString>
                </gmd:description>
              </gmd:MD_Identifier>
            </gmd:geographicIdentifier>
          </gmd:EX_GeographicDescription>
        </gmd:geographicElement>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template name="writeExtentTemporalInformation">
    <xsl:for-each select="/*/Temporal/SingleDateTime">
      <gmd:temporalElement>
        <xsl:comment>SingleDateTime</xsl:comment>
        <gmd:EX_TemporalExtent>
          <!-- In cases with multiple SingleDateTimes this boundingTemporalExtent id is not correct -->
          <xsl:if test="position()=1">
            <xsl:attribute name="id" select="'boundingTemporalExtent'"/>
          </xsl:if>
          <gmd:extent>
            <gml:TimeInstant>
              <xsl:attribute name="gml:id" select="generate-id()"/>
              <gml:timePosition>
                <xsl:if test="/*/Temporal/DateType">
                  <xsl:attribute name="frame" select="/*/Temporal/DateType"/>
                </xsl:if>
                <xsl:value-of select="."/>
              </gml:timePosition>
            </gml:TimeInstant>
          </gmd:extent>
        </gmd:EX_TemporalExtent>
      </gmd:temporalElement>
    </xsl:for-each>
    <xsl:for-each select="/*/Temporal/RangeDateTime">
      <gmd:temporalElement>
        <xsl:comment>RangeDateTime</xsl:comment>
        <gmd:EX_TemporalExtent>
          <xsl:if test="position()=1">
            <!-- In cases with multiple RangeDateTimes this boundingTemporalExtent id is not correct -->
            <xsl:attribute name="id" select="'boundingTemporalExtent'"/>
          </xsl:if>
          <gmd:extent>
            <gml:TimePeriod>
              <xsl:attribute name="gml:id" select="generate-id()"/>
              <gml:beginPosition>
                <xsl:if test="/*/Temporal/DateType">
                  <xsl:attribute name="frame" select="/*/Temporal/DateType"/>
                </xsl:if>
                <xsl:value-of select="BeginningDateTime"/>
              </gml:beginPosition>
              <gml:endPosition>
                <xsl:if test="/*/Temporal/DateType">
                  <xsl:attribute name="frame" select="/*/Temporal/DateType"/>
                </xsl:if>
                <xsl:choose>
                  <xsl:when test="/*/Temporal/EndsAtPresentFlag='true'">
                    <xsl:attribute name="indeterminatePosition">now</xsl:attribute>
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:value-of select="EndingDateTime"/>
                  </xsl:otherwise>
                </xsl:choose>
              </gml:endPosition>
            </gml:TimePeriod>
          </gmd:extent>
        </gmd:EX_TemporalExtent>
      </gmd:temporalElement>
    </xsl:for-each>
  </xsl:template>
  <xsl:template name="writeOperation">
    <xsl:for-each select="/*/Campaigns/Campaign">
      <gmi:operation>
        <gmi:MI_Operation>
          <gmi:description>
            <xsl:call-template name="writeCharacterString">
              <xsl:with-param name="stringToWrite">
                <xsl:value-of select="ShortName"/>
                <xsl:if test="LongName">
                  <xsl:value-of select="concat(' &gt; ',LongName)"/>
                </xsl:if>
                <xsl:if test="StartDate">
                  <xsl:value-of select="concat(' ',StartDate)"/>
                </xsl:if>
                <xsl:if test="EndDate">
                  <xsl:value-of select="concat(' to ',EndDate)"/>
                </xsl:if>
              </xsl:with-param>
            </xsl:call-template>
          </gmi:description>
          <gmi:identifier>
            <gmd:MD_Identifier>
              <gmd:code>
                <xsl:call-template name="writeCharacterString">
                  <xsl:with-param name="stringToWrite" select="ShortName"/>
                </xsl:call-template>
              </gmd:code>
            </gmd:MD_Identifier>
          </gmi:identifier>
          <gmi:status/>
          <gmi:parentOperation gco:nilReason="inapplicable"/>
        </gmi:MI_Operation>
      </gmi:operation>
    </xsl:for-each>
  </xsl:template>
  <xsl:template name="writePlatform">
    <xsl:for-each select="/*/Platforms/Platform[ShortName!='N/A']">
      <xsl:element name="gmi:platform">
        <xsl:variable name="platformObjectName">
          <xsl:choose>
            <!-- eos:EOS_Platform must be used when any of the extension criteria exist for any instrument -->
            <xsl:when test="$platformCharacteristicCount+$platformInformationCount+
                            $instrumentCharacteristicCount+$instrumentInformationCount+
                            $sensorCharacteristicCount+$sensorInformationCount+
                            count(//Sensors/Sensor)+
                            count(//Instrument/OperationModes/OperationMode)">
              <xsl:value-of select="'eos:EOS_Platform'"/>
            </xsl:when>
            <xsl:otherwise>
              <xsl:value-of select="'gmi:MI_Platform'"/>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:variable>
        <xsl:element name="{$platformObjectName}">
          <xsl:attribute name="id" select="generate-id()"/>
          <gmi:identifier>
            <gmd:MD_Identifier>
              <gmd:code>
                <xsl:call-template name="writeCharacterString">
                  <xsl:with-param name="stringToWrite" select="ShortName"/>
                </xsl:call-template>
              </gmd:code>
              <gmd:description>
                <xsl:call-template name="writeCharacterString">
                  <xsl:with-param name="stringToWrite" select="LongName"/>
                </xsl:call-template>
              </gmd:description>
            </gmd:MD_Identifier>
          </gmi:identifier>
          <gmi:description>
            <xsl:call-template name="writeCharacterString">
              <xsl:with-param name="stringToWrite" select="Type"/>
            </xsl:call-template>
          </gmi:description>
          <xsl:choose>
            <xsl:when test="count(Instruments/Instrument) > 0">
              <xsl:for-each select="Instruments/Instrument">
                <xsl:element name="gmi:instrument">
                  <xsl:attribute name="xlink:href" select="concat('#',generate-id())"/>
                </xsl:element>
              </xsl:for-each>
            </xsl:when>
            <xsl:otherwise>
              <xsl:element name="gmi:instrument">
                <xsl:attribute name="gco:nilReason" select="'inapplicable'"/>
              </xsl:element>
            </xsl:otherwise>
          </xsl:choose>
          <!-- Need to take care of unnamed platforms with instruments -->
          <xsl:for-each select="../Platform[ShortName='N/A']/Instruments/Instrument">
            <gmi:instrument>
              <xsl:attribute name="xlink:href" select="concat('#',generate-id())"/>
            </gmi:instrument>
          </xsl:for-each>
          <xsl:if test="Characteristics/Characteristic or $platformInformationCount > 0 or ../../OrbitCalculatedSpatialDomains or ../../Spatial/OrbitParameters or ../../Spatial/HorizontalSpatialDomain/Orbit">
            <xsl:call-template name="writePlatformAdditionalAttributes"/>
          </xsl:if>
        </xsl:element>
      </xsl:element>
    </xsl:for-each>
  </xsl:template>
  <xsl:template name="writeInstrument">
    <xsl:for-each select="//Instruments/Instrument">
      <xsl:element name="gmi:instrument">
        <xsl:variable name="instrumentObjectName">
          <xsl:choose>
            <!-- Check extension requirements -->
            <xsl:when test="$instrumentCharacteristicCount +
                            $instrumentInformationCount +
                            $sensorCharacteristicCount +
                            $sensorInformationCount +
                            count(OperationModes/OperationMode) +
                            count(Sensors/Sensor)">
              <xsl:value-of select="'eos:EOS_Instrument'"/>
            </xsl:when>
            <xsl:otherwise>
              <xsl:value-of select="'gmi:MI_Instrument'"/>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:variable>
        <xsl:element name="{$instrumentObjectName}">
          <xsl:attribute name="id" select="generate-id()"/>
          <gmi:citation>
            <gmd:CI_Citation>
              <gmd:title>
                <!-- Concat Instrument and Short and Long Names -->
                <xsl:call-template name="writeCharacterString">
                  <xsl:with-param name="stringToWrite">
                    <xsl:value-of select="ShortName"/>
                    <xsl:if test="LongName">
                      <xsl:value-of select="concat(' &gt; ',LongName)"/>
                    </xsl:if>
                  </xsl:with-param>
                  <!--<xsl:with-param name="stringToWrite" select="concat(ShortName,'>',LongName)"/>-->
                </xsl:call-template>
              </gmd:title>
              <gmd:date gco:nilReason="unknown"/>
            </gmd:CI_Citation>
          </gmi:citation>
          <gmi:identifier>
            <gmd:MD_Identifier>
              <gmd:code>
                <xsl:call-template name="writeCharacterString">
                  <xsl:with-param name="stringToWrite" select="ShortName"/>
                </xsl:call-template>
              </gmd:code>
              <gmd:description>
                <xsl:call-template name="writeCharacterString">
                  <xsl:with-param name="stringToWrite" select="LongName"/>
                </xsl:call-template>
              </gmd:description>
            </gmd:MD_Identifier>
          </gmi:identifier>
          <gmi:type>
            <gco:CharacterString>
              <xsl:value-of select="Technique"/>
            </gco:CharacterString>
          </gmi:type>
          <gmi:description gco:nilReason="missing"/>
          <xsl:for-each select="ancestor::Platform">
            <xsl:element name="gmi:mountedOn">
              <xsl:attribute name="xlink:href" select="concat('#',generate-id())"/>
            </xsl:element>
          </xsl:for-each>
          <xsl:if test="Characteristics/Characteristic or $instrumentInformationCount > 0 or OperationModes/OperationMode">
            <eos:otherPropertyType>
              <gco:RecordType xlink:href="http://earthdata.nasa.gov/metadata/schema/eos/1.0/eos.xsd#xpointer(//element[@name='AdditionalAttributes'])">Echo Additional Attributes</gco:RecordType>
            </eos:otherPropertyType>
            <eos:otherProperty>
              <gco:Record>
                <eos:AdditionalAttributes>
                  <!-- Write Instrument Characteristics -->
                  <xsl:for-each select="Characteristics/Characteristic">
                    <xsl:comment select="'Instrument Characteristic'"/>
                    <xsl:call-template name="writeEOS_PSA">
                      <xsl:with-param name="additionalAttributeType" select="'instrumentInformation'"/>
                    </xsl:call-template>
                  </xsl:for-each>
                  <!-- Write instrumentInformation AdditionalAttributes -->
                  <xsl:for-each select="/*/AdditionalAttributes/AdditionalAttribute">
                    <xsl:if test="key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='instrumentInformation'">
                      <xsl:comment select="'Instrument Information Additional Attribute'"/>
                      <xsl:call-template name="writeEOS_PSA">
                        <xsl:with-param name="additionalAttributeType" select="'instrumentInformation'"/>
                      </xsl:call-template>
                    </xsl:if>
                  </xsl:for-each>
                  <!-- Write instrument OperationMode -->
                  <xsl:for-each select="OperationModes/OperationMode">
                    <eos:AdditionalAttribute>
                      <eos:reference>
                        <eos:EOS_AdditionalAttributeDescription>
                          <eos:type>
                            <xsl:element name="eos:EOS_AdditionalAttributeTypeCode">
                              <xsl:attribute name="codeList" select="'http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeTypeCode'"/>
                              <xsl:attribute name="codeListValue" select="'instrumentInformation'"/>
                              <xsl:value-of select="'instrumentInformation'"/>
                            </xsl:element>
                          </eos:type>
                          <eos:name>
                            <gco:CharacterString>OperationMode</gco:CharacterString>
                          </eos:name>
                        </eos:EOS_AdditionalAttributeDescription>
                      </eos:reference>
                      <eos:value>
                        <gco:CharacterString>
                          <xsl:value-of select="."/>
                        </gco:CharacterString>
                      </eos:value>
                    </eos:AdditionalAttribute>
                  </xsl:for-each>
                </eos:AdditionalAttributes>
              </gco:Record>
            </eos:otherProperty>
          </xsl:if>
          <xsl:call-template name="writeSensor"/>
        </xsl:element>
      </xsl:element>
    </xsl:for-each>
  </xsl:template>
  <xsl:template name="writeSensor">
    <xsl:for-each select="Sensors/Sensor">
      <xsl:element name="eos:sensor">
        <xsl:element name="eos:EOS_Sensor">
          <xsl:attribute name="id" select="generate-id()"/>
          <eos:citation>
            <gmd:CI_Citation>
              <gmd:title>
                <xsl:call-template name="writeCharacterString">
                  <!-- Concat Sensor and Short and Long Names -->
                  <xsl:with-param name="stringToWrite" select="concat(ShortName,'>',LongName)"/>
                </xsl:call-template>
              </gmd:title>
              <gmd:date gco:nilReason="unknown"/>
            </gmd:CI_Citation>
          </eos:citation>
          <eos:identifier>
            <gmd:MD_Identifier>
              <gmd:code>
                <xsl:call-template name="writeCharacterString">
                  <xsl:with-param name="stringToWrite">
                    <xsl:value-of select="ShortName"/>
                  </xsl:with-param>
                </xsl:call-template>
              </gmd:code>
              <gmd:description>
                <xsl:call-template name="writeCharacterString">
                  <xsl:with-param name="stringToWrite">
                    <xsl:value-of select="LongName"/>
                  </xsl:with-param>
                </xsl:call-template>
              </gmd:description>
            </gmd:MD_Identifier>
          </eos:identifier>
          <eos:type>
            <xsl:call-template name="writeCharacterString">
              <xsl:with-param name="stringToWrite" select="Technique"/>
            </xsl:call-template>
          </eos:type>
          <xsl:for-each select="ancestor::Instrument">
            <eos:mountedOn>
              <xsl:attribute name="xlink:href" select="concat('#',generate-id())"/>
            </eos:mountedOn>
          </xsl:for-each>
          <xsl:if test="Characteristics/Characteristic or $sensorInformationCount > 0">
            <eos:otherPropertyType>
              <gco:RecordType xlink:href="http://earthdata.nasa.gov/metadata/schema/eos/1.0/eos.xsd#xpointer(//element[@name='AdditionalAttributes'])">Echo Additional Attributes</gco:RecordType>
            </eos:otherPropertyType>
            <eos:otherProperty>
              <gco:Record>
                <eos:AdditionalAttributes>
                  <!-- Write Sensor Characteristics -->
                  <xsl:for-each select="Characteristics/Characteristic">
                    <xsl:call-template name="writeEOS_PSA">
                      <xsl:with-param name="additionalAttributeType" select="'sensorInformation'"/>
                    </xsl:call-template>
                  </xsl:for-each>
                  <!-- Write sensorInformation AdditionalAttributes -->
                  <xsl:for-each select="/*/AdditionalAttributes/AdditionalAttribute">
                    <xsl:if test="key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='sensorInformation'">
                      <xsl:call-template name="writeEOS_PSA">
                        <xsl:with-param name="additionalAttributeType" select="'sensorInformation'"/>
                      </xsl:call-template>
                    </xsl:if>
                  </xsl:for-each>
                </eos:AdditionalAttributes>
              </gco:Record>
            </eos:otherProperty>
          </xsl:if>
        </xsl:element>
      </xsl:element>
    </xsl:for-each>
  </xsl:template>
  <xsl:template name="writePlatformAdditionalAttributes">
    <eos:otherPropertyType>
      <gco:RecordType xlink:href="http://earthdata.nasa.gov/metadata/schema/eos/1.0/eos.xsd#xpointer(//element[@name='AdditionalAttributes'])">Echo Additional Attributes</gco:RecordType>
    </eos:otherPropertyType>
    <eos:otherProperty>
      <gco:Record>
        <eos:AdditionalAttributes>
          <!-- Write Platform Characteristics -->
          <xsl:for-each select="Characteristics/Characteristic">
            <xsl:comment select="'Platform Characteristic'"/>
            <xsl:call-template name="writeEOS_PSA">
              <xsl:with-param name="additionalAttributeType" select="'platformInformation'"/>
            </xsl:call-template>
          </xsl:for-each>
          <!-- Write platformInformation AdditionalAttributes -->
          <xsl:for-each select="//AdditionalAttributes/AdditionalAttribute">
            <xsl:if test="key('additionalAttributeLookup',Name,doc('additionalAttributeType.xml'))/@type='platformInformation'">
              <xsl:comment select="'Platform Additional Attributes (platformInformation)'"/>
              <xsl:call-template name="writeEOS_PSA">
                <xsl:with-param name="additionalAttributeType" select="'platformInformation'"/>
              </xsl:call-template>
            </xsl:if>
          </xsl:for-each>
          <!-- Write OrbitCalculatedSpatialDomains -->
          <xsl:for-each select="//OrbitCalculatedSpatialDomains/OrbitCalculatedSpatialDomain">
            <xsl:comment select="'OrbitCalculatedSpatialDomains'"/>
            <xsl:for-each select="*">
              <eos:AdditionalAttribute>
                <eos:reference>
                  <eos:EOS_AdditionalAttributeDescription>
                    <eos:type>
                      <eos:EOS_AdditionalAttributeTypeCode codeList="http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeTypeCode" codeListValue="platformInformation">platformInformation</eos:EOS_AdditionalAttributeTypeCode>
                    </eos:type>
                    <eos:name>
                      <gco:CharacterString>
                        <xsl:value-of select="local-name()"/>
                      </gco:CharacterString>
                    </eos:name>
                  </eos:EOS_AdditionalAttributeDescription>
                </eos:reference>
                <eos:value>
                  <gco:CharacterString>
                    <xsl:value-of select="."/>
                  </gco:CharacterString>
                </eos:value>
              </eos:AdditionalAttribute>
            </xsl:for-each>
          </xsl:for-each>
          <!-- Write HorizontalSpatialDomain/Orbit Information -->
          <xsl:for-each select="//Spatial/HorizontalSpatialDomain/Orbit">
            <xsl:comment select="'HorizontalSpatialDomain/Orbit'"/>
            <xsl:for-each select="*">
              <eos:AdditionalAttribute>
                <eos:reference>
                  <eos:EOS_AdditionalAttributeDescription>
                    <eos:type>
                      <eos:EOS_AdditionalAttributeTypeCode codeList="http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeTypeCode" codeListValue="platformInformation">platformInformation</eos:EOS_AdditionalAttributeTypeCode>
                    </eos:type>
                    <eos:name>
                      <gco:CharacterString>
                        <xsl:value-of select="local-name()"/>
                      </gco:CharacterString>
                    </eos:name>
                  </eos:EOS_AdditionalAttributeDescription>
                </eos:reference>
                <eos:value>
                  <gco:CharacterString>
                    <xsl:value-of select="."/>
                  </gco:CharacterString>
                </eos:value>
              </eos:AdditionalAttribute>
            </xsl:for-each>
          </xsl:for-each>
          <!-- Write Spatial Orbit Parameters -->
          <xsl:for-each select="//Spatial/OrbitParameters">
            <xsl:comment select="'Spatial/OrbitParameters'"/>
            <xsl:for-each select="*">
              <eos:AdditionalAttribute>
                <eos:reference>
                  <eos:EOS_AdditionalAttributeDescription>
                    <eos:type>
                      <eos:EOS_AdditionalAttributeTypeCode codeList="http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeTypeCode" codeListValue="platformInformation">platformInformation</eos:EOS_AdditionalAttributeTypeCode>
                    </eos:type>
                    <eos:name>
                      <gco:CharacterString>
                        <xsl:value-of select="local-name()"/>
                      </gco:CharacterString>
                    </eos:name>
                  </eos:EOS_AdditionalAttributeDescription>
                </eos:reference>
                <eos:value>
                  <gco:CharacterString>
                    <xsl:value-of select="."/>
                  </gco:CharacterString>
                </eos:value>
              </eos:AdditionalAttribute>
            </xsl:for-each>
          </xsl:for-each>
        </eos:AdditionalAttributes>
      </gco:Record>
    </eos:otherProperty>
  </xsl:template>
</xsl:stylesheet>
