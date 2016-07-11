(ns cmr.search.test.xslt
  "Tests to verify the xsl for ECHO10 to ISO19115"
  (:require [clojure.test :refer :all]
            [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [cmr.common.util :refer [are3]]
            [cmr.common.xml :as xml]
            [cmr.common.xml.xslt :as xslt]))

(def echo10-collection-no-use-constraints
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
  <Collection>
      <ShortName>MINIMAL</ShortName>
      <VersionId>1</VersionId>
      <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
      <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
      <LongName>A minimal valid collection</LongName>
      <DataSetId>A minimal valid collection V 1</DataSetId>
      <Description>A minimal valid collection</Description>
      <Orderable>true</Orderable>
      <Visible>true</Visible>
    </Collection>")

(def echo10-collection-with-use-constraints
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
  <Collection>
      <ShortName>MINIMAL</ShortName>
      <VersionId>1</VersionId>
      <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
      <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
      <LongName>A minimal valid collection</LongName>
      <DataSetId>A minimal valid collection V 1</DataSetId>
      <Description>A minimal valid collection</Description>
      <Orderable>true</Orderable>
      <RestrictionFlag>0</RestrictionFlag>
      <RestrictionComment>Dummy Comment</RestrictionComment>
      <Visible>true</Visible>
    </Collection>")

(def iso-no-use-constraints "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<gmi:MI_Metadata xmlns:gmi=\"http://www.isotc211.org/2005/gmi\" xmlns:eos=\"http://earthdata.nasa.gov/schema/eos\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gml=\"http://www.opengis.net/gml/3.2\" xmlns:gmx=\"http://www.isotc211.org/2005/gmx\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:srv=\"http://www.isotc211.org/2005/srv\" xmlns:swe=\"http://schemas.opengis.net/sweCommon/2.0/\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">
   <!--Other Properties, all:0, coi:0,ii:0,si:0,pli:0,pri:0,qi:0,gi:0,ci:0,dk:0,pcc:0,icc:0,scc:0-->
   <gmd:fileIdentifier>
      <gco:CharacterString>gov.nasa.echo:A minimal valid collection V 1</gco:CharacterString>
   </gmd:fileIdentifier>
   <gmd:language>
      <gco:CharacterString>eng</gco:CharacterString>
   </gmd:language>
   <gmd:characterSet>
      <gmd:MD_CharacterSetCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_CharacterSetCode\" codeListValue=\"utf8\">utf8</gmd:MD_CharacterSetCode>
   </gmd:characterSet>
   <gmd:hierarchyLevel>
      <gmd:MD_ScopeCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ScopeCode\" codeListValue=\"series\">series</gmd:MD_ScopeCode>
   </gmd:hierarchyLevel>
   <gmd:contact gco:nilReason=\"missing\" />
   <gmd:dateStamp>
      <gco:DateTime>2016-07-06T18:15:49.058-04:00</gco:DateTime>
   </gmd:dateStamp>
   <gmd:metadataStandardName>
      <gco:CharacterString>ISO 19115-2 Geographic Information - Metadata Part 2 Extensions for imagery and gridded data</gco:CharacterString>
   </gmd:metadataStandardName>
   <gmd:metadataStandardVersion>
      <gco:CharacterString>ISO 19115-2:2009(E)</gco:CharacterString>
   </gmd:metadataStandardVersion>
   <gmd:identificationInfo>
      <gmd:MD_DataIdentification>
         <gmd:citation>
            <gmd:CI_Citation>
               <gmd:title>
                  <gco:CharacterString>MINIMAL &gt; A minimal valid collection</gco:CharacterString>
               </gmd:title>
               <gmd:date>
                  <gmd:CI_Date>
                     <gmd:date>
                        <gco:DateTime>1999-12-31T19:00:00-05:00</gco:DateTime>
                     </gmd:date>
                     <gmd:dateType>
                        <gmd:CI_DateTypeCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_DateTypeCode\" codeListValue=\"revision\">revision</gmd:CI_DateTypeCode>
                     </gmd:dateType>
                  </gmd:CI_Date>
               </gmd:date>
               <gmd:date>
                  <gmd:CI_Date>
                     <gmd:date>
                        <gco:DateTime>1999-12-31T19:00:00-05:00</gco:DateTime>
                     </gmd:date>
                     <gmd:dateType>
                        <gmd:CI_DateTypeCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_DateTypeCode\" codeListValue=\"creation\">creation</gmd:CI_DateTypeCode>
                     </gmd:dateType>
                  </gmd:CI_Date>
               </gmd:date>
               <gmd:edition>
                  <gco:CharacterString>1</gco:CharacterString>
               </gmd:edition>
               <gmd:identifier>
                  <gmd:MD_Identifier>
                     <gmd:code>
                        <gco:CharacterString>MINIMAL</gco:CharacterString>
                     </gmd:code>
                     <gmd:description>
                        <gco:CharacterString>A minimal valid collection</gco:CharacterString>
                     </gmd:description>
                  </gmd:MD_Identifier>
               </gmd:identifier>
               <gmd:otherCitationDetails>
                  <gco:CharacterString />
               </gmd:otherCitationDetails>
            </gmd:CI_Citation>
         </gmd:citation>
         <gmd:abstract>
            <gco:CharacterString>A minimal valid collection</gco:CharacterString>
         </gmd:abstract>
         <gmd:purpose gco:nilReason=\"missing\" />
         <gmd:language>
            <gco:CharacterString>eng</gco:CharacterString>
         </gmd:language>
         <gmd:characterSet>
            <gmd:MD_CharacterSetCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_CharacterSetCode\" codeListValue=\"utf8\">utf8</gmd:MD_CharacterSetCode>
         </gmd:characterSet>
         <gmd:extent>
            <gmd:EX_Extent id=\"boundingExtent\">
               <gmd:description gco:nilReason=\"unknown\" />
            </gmd:EX_Extent>
         </gmd:extent>
         <gmd:supplementalInformation />
         <gmd:processingLevel>
            <gmd:MD_Identifier>
               <gmd:code>
                  <gco:CharacterString />
               </gmd:code>
               <gmd:description>
                  <gco:CharacterString />
               </gmd:description>
            </gmd:MD_Identifier>
         </gmd:processingLevel>
      </gmd:MD_DataIdentification>
   </gmd:identificationInfo>
   <gmd:distributionInfo>
      <gmd:MD_Distribution>
         <gmd:distributor>
            <gmd:MD_Distributor>
               <gmd:distributorContact gco:nilReason=\"missing\" />
               <gmd:distributionOrderProcess>
                  <gmd:MD_StandardOrderProcess>
                     <gmd:fees gco:nilReason=\"missing\" />
                  </gmd:MD_StandardOrderProcess>
               </gmd:distributionOrderProcess>
               <gmd:distributorTransferOptions>
                  <gmd:MD_DigitalTransferOptions />
               </gmd:distributorTransferOptions>
            </gmd:MD_Distributor>
         </gmd:distributor>
      </gmd:MD_Distribution>
   </gmd:distributionInfo>
   <gmd:dataQualityInfo>
      <gmd:DQ_DataQuality>
         <gmd:scope>
            <gmd:DQ_Scope>
               <gmd:level>
                  <gmd:MD_ScopeCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ScopeCode\" codeListValue=\"series\">series</gmd:MD_ScopeCode>
               </gmd:level>
            </gmd:DQ_Scope>
         </gmd:scope>
         <gmd:lineage>
            <gmd:LI_Lineage>
               <gmd:processStep>
                  <gmi:LE_ProcessStep>
                     <gmd:description gco:nilReason=\"unknown\" />
                  </gmi:LE_ProcessStep>
               </gmd:processStep>
            </gmd:LI_Lineage>
         </gmd:lineage>
      </gmd:DQ_DataQuality>
   </gmd:dataQualityInfo>
   <gmd:metadataMaintenance>
      <gmd:MD_MaintenanceInformation>
         <gmd:maintenanceAndUpdateFrequency>
            <gmd:MD_MaintenanceFrequencyCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_MaintenanceFrequencyCode\" codeListValue=\"irregular\">irregular</gmd:MD_MaintenanceFrequencyCode>
         </gmd:maintenanceAndUpdateFrequency>
         <gmd:maintenanceNote>
            <gco:CharacterString>Translated from ECHO using ECHOToISO.xsl Version: 1.32 (Dec. 9, 2015)</gco:CharacterString>
         </gmd:maintenanceNote>
      </gmd:MD_MaintenanceInformation>
   </gmd:metadataMaintenance>
   <gmi:acquisitionInformation>
      <gmi:MI_AcquisitionInformation />
   </gmi:acquisitionInformation>
</gmi:MI_Metadata>")

(def iso-with-use-constraints
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
  <gmi:MI_Metadata xmlns:gmi=\"http://www.isotc211.org/2005/gmi\" xmlns:eos=\"http://earthdata.nasa.gov/schema/eos\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gml=\"http://www.opengis.net/gml/3.2\" xmlns:gmx=\"http://www.isotc211.org/2005/gmx\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:srv=\"http://www.isotc211.org/2005/srv\" xmlns:swe=\"http://schemas.opengis.net/sweCommon/2.0/\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">
     <!--Other Properties, all:0, coi:0,ii:0,si:0,pli:0,pri:0,qi:0,gi:0,ci:0,dk:0,pcc:0,icc:0,scc:0-->
     <gmd:fileIdentifier>
        <gco:CharacterString>gov.nasa.echo:A minimal valid collection V 1</gco:CharacterString>
     </gmd:fileIdentifier>
     <gmd:language>
        <gco:CharacterString>eng</gco:CharacterString>
     </gmd:language>
     <gmd:characterSet>
        <gmd:MD_CharacterSetCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_CharacterSetCode\" codeListValue=\"utf8\">utf8</gmd:MD_CharacterSetCode>
     </gmd:characterSet>
     <gmd:hierarchyLevel>
        <gmd:MD_ScopeCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ScopeCode\" codeListValue=\"series\">series</gmd:MD_ScopeCode>
     </gmd:hierarchyLevel>
     <gmd:contact gco:nilReason=\"missing\" />
     <gmd:dateStamp>
        <gco:DateTime>2016-07-06T18:15:49.058-04:00</gco:DateTime>
     </gmd:dateStamp>
     <gmd:metadataStandardName>
        <gco:CharacterString>ISO 19115-2 Geographic Information - Metadata Part 2 Extensions for imagery and gridded data</gco:CharacterString>
     </gmd:metadataStandardName>
     <gmd:metadataStandardVersion>
        <gco:CharacterString>ISO 19115-2:2009(E)</gco:CharacterString>
     </gmd:metadataStandardVersion>
     <gmd:identificationInfo>
        <gmd:MD_DataIdentification>
           <gmd:citation>
              <gmd:CI_Citation>
                 <gmd:title>
                    <gco:CharacterString>MINIMAL &gt; A minimal valid collection</gco:CharacterString>
                 </gmd:title>
                 <gmd:date>
                    <gmd:CI_Date>
                       <gmd:date>
                          <gco:DateTime>1999-12-31T19:00:00-05:00</gco:DateTime>
                       </gmd:date>
                       <gmd:dateType>
                          <gmd:CI_DateTypeCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_DateTypeCode\" codeListValue=\"revision\">revision</gmd:CI_DateTypeCode>
                       </gmd:dateType>
                    </gmd:CI_Date>
                 </gmd:date>
                 <gmd:date>
                    <gmd:CI_Date>
                       <gmd:date>
                          <gco:DateTime>1999-12-31T19:00:00-05:00</gco:DateTime>
                       </gmd:date>
                       <gmd:dateType>
                          <gmd:CI_DateTypeCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_DateTypeCode\" codeListValue=\"creation\">creation</gmd:CI_DateTypeCode>
                       </gmd:dateType>
                    </gmd:CI_Date>
                 </gmd:date>
                 <gmd:edition>
                    <gco:CharacterString>1</gco:CharacterString>
                 </gmd:edition>
                 <gmd:identifier>
                    <gmd:MD_Identifier>
                       <gmd:code>
                          <gco:CharacterString>MINIMAL</gco:CharacterString>
                       </gmd:code>
                       <gmd:description>
                          <gco:CharacterString>A minimal valid collection</gco:CharacterString>
                       </gmd:description>
                    </gmd:MD_Identifier>
                 </gmd:identifier>
                 <gmd:otherCitationDetails>
                    <gco:CharacterString />
                 </gmd:otherCitationDetails>
              </gmd:CI_Citation>
           </gmd:citation>
           <gmd:abstract>
              <gco:CharacterString>A minimal valid collection</gco:CharacterString>
           </gmd:abstract>
           <gmd:purpose gco:nilReason=\"missing\" />
           <gmd:resourceConstraints>
              <gmd:MD_LegalConstraints>
                 <gmd:useLimitation>
                    <gco:CharacterString>Restriction Comment: Dummy Comment</gco:CharacterString>
                 </gmd:useLimitation>
                 <gmd:otherConstraints>
                    <gco:CharacterString>Restriction Flag: 0</gco:CharacterString>
                 </gmd:otherConstraints>
              </gmd:MD_LegalConstraints>
           </gmd:resourceConstraints>
           <gmd:language>
              <gco:CharacterString>eng</gco:CharacterString>
           </gmd:language>
           <gmd:characterSet>
              <gmd:MD_CharacterSetCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_CharacterSetCode\" codeListValue=\"utf8\">utf8</gmd:MD_CharacterSetCode>
           </gmd:characterSet>
           <gmd:extent>
              <gmd:EX_Extent id=\"boundingExtent\">
                 <gmd:description gco:nilReason=\"unknown\" />
              </gmd:EX_Extent>
           </gmd:extent>
           <gmd:supplementalInformation />
           <gmd:processingLevel>
              <gmd:MD_Identifier>
                 <gmd:code>
                    <gco:CharacterString />
                 </gmd:code>
                 <gmd:description>
                    <gco:CharacterString />
                 </gmd:description>
              </gmd:MD_Identifier>
           </gmd:processingLevel>
        </gmd:MD_DataIdentification>
     </gmd:identificationInfo>
     <gmd:distributionInfo>
        <gmd:MD_Distribution>
           <gmd:distributor>
              <gmd:MD_Distributor>
                 <gmd:distributorContact gco:nilReason=\"missing\" />
                 <gmd:distributionOrderProcess>
                    <gmd:MD_StandardOrderProcess>
                       <gmd:fees gco:nilReason=\"missing\" />
                    </gmd:MD_StandardOrderProcess>
                 </gmd:distributionOrderProcess>
                 <gmd:distributorTransferOptions>
                    <gmd:MD_DigitalTransferOptions />
                 </gmd:distributorTransferOptions>
              </gmd:MD_Distributor>
           </gmd:distributor>
        </gmd:MD_Distribution>
     </gmd:distributionInfo>
     <gmd:dataQualityInfo>
        <gmd:DQ_DataQuality>
           <gmd:scope>
              <gmd:DQ_Scope>
                 <gmd:level>
                    <gmd:MD_ScopeCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ScopeCode\" codeListValue=\"series\">series</gmd:MD_ScopeCode>
                 </gmd:level>
              </gmd:DQ_Scope>
           </gmd:scope>
           <gmd:lineage>
              <gmd:LI_Lineage>
                 <gmd:processStep>
                    <gmi:LE_ProcessStep>
                       <gmd:description gco:nilReason=\"unknown\" />
                    </gmi:LE_ProcessStep>
                 </gmd:processStep>
              </gmd:LI_Lineage>
           </gmd:lineage>
        </gmd:DQ_DataQuality>
     </gmd:dataQualityInfo>
     <gmd:metadataMaintenance>
        <gmd:MD_MaintenanceInformation>
           <gmd:maintenanceAndUpdateFrequency>
              <gmd:MD_MaintenanceFrequencyCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_MaintenanceFrequencyCode\" codeListValue=\"irregular\">irregular</gmd:MD_MaintenanceFrequencyCode>
           </gmd:maintenanceAndUpdateFrequency>
           <gmd:maintenanceNote>
              <gco:CharacterString>Translated from ECHO using ECHOToISO.xsl Version: 1.32 (Dec. 9, 2015)</gco:CharacterString>
           </gmd:maintenanceNote>
        </gmd:MD_MaintenanceInformation>
     </gmd:metadataMaintenance>
     <gmi:acquisitionInformation>
        <gmi:MI_AcquisitionInformation />
     </gmi:acquisitionInformation>
  </gmi:MI_Metadata>")

(def echo10-granule-browse
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
				<Granule>
				    <GranuleUR>GLDAS_NOAH10_3H.2.0:GLDAS_NOAH10_3H.A19480101.0300.020.nc4</GranuleUR>
				    <InsertTime>2012-01-11T10:00:00.000Z</InsertTime>
				    <LastUpdate>2012-01-19T18:00:00.000Z</LastUpdate>
				    <Collection>
				        <DataSetId>ASTER Level 1 precision terrain corrected registered at-sensor radiance V003</DataSetId>
				    </Collection>
        		<OnlineResources>
                <OnlineResource>
                  <URL>http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5/GLDAS_NOAH10_3H.2.0:GLDAS_NOAH10_3H.A19480101.0300.020.jpg</URL>
                  <Description>Some image</Description>
                  <Type>Browse</Type>
        		    <MimeType>image/jpeg</MimeType>
                </OnlineResource>
      		</OnlineResources>
				  <Orderable>true</Orderable>
				</Granule>")

(def echo10-granule-browse-uppercase (str/replace echo10-granule-browse "Browse" "BROWSE"))

(def echo10-granule-browse-lowercase (str/replace echo10-granule-browse "Browse" "browse"))

(def echo10-granule-browse-mixed-case (str/replace echo10-granule-browse "Browse" "BrOwSe"))

(def echo10-granule-no-browse
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
				<Granule>
				    <GranuleUR>GLDAS_NOAH10_3H.2.0:GLDAS_NOAH10_3H.A19480101.0300.020.nc4</GranuleUR>
				    <InsertTime>2012-01-11T10:00:00.000Z</InsertTime>
				    <LastUpdate>2012-01-19T18:00:00.000Z</LastUpdate>
				    <Collection>
				        <DataSetId>ASTER Level 1 precision terrain corrected registered at-sensor radiance V003</DataSetId>
				    </Collection>
              		<OnlineResources>
                      <OnlineResource>
                        <URL>http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5/GLDAS_NOAH10_3H.2.0:GLDAS_NOAH10_3H.A19480101.0300.020.nc4</URL>
                        <Description>OpenDAP URL</Description>
                        <Type>OpenDAP</Type>
              		    <MimeType>application/x-netcdf</MimeType>
                      </OnlineResource>
            		</OnlineResources>
				    <Orderable>true</Orderable>
				</Granule>")

(def iso-granule-browse
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
  <gmi:MI_Metadata xmlns:gmi=\"http://www.isotc211.org/2005/gmi\"
                   xmlns:gco=\"http://www.isotc211.org/2005/gco\"
                   xmlns:gmd=\"http://www.isotc211.org/2005/gmd\"
                   xmlns:gmx=\"http://www.isotc211.org/2005/gmx\"
                   xmlns:gsr=\"http://www.isotc211.org/2005/gsr\"
                   xmlns:gss=\"http://www.isotc211.org/2005/gss\"
                   xmlns:gts=\"http://www.isotc211.org/2005/gts\"
                   xmlns:srv=\"http://www.isotc211.org/2005/srv\"
                   xmlns:gml=\"http://www.opengis.net/gml/3.2\"
                   xmlns:xlink=\"http://www.w3.org/1999/xlink\"
                   xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
                   xmlns:swe=\"http://schemas.opengis.net/sweCommon/2.0/\"
                   xmlns:eos=\"http://earthdata.nasa.gov/schema/eos\"
                   xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><!--Other Properties, all:0, coi:0,ii:0,si:0,pli:0,pri:0,qi:0,gi:0,ci:0,dk:0,pcc:0,icc:0,scc:0-->
     <gmd:fileIdentifier>
        <gco:CharacterString>gov.nasa.echo:GLDAS_NOAH10_3H.2.0:GLDAS_NOAH10_3H.A19480101.0300.020.nc4</gco:CharacterString>
     </gmd:fileIdentifier>
     <gmd:language>
        <gco:CharacterString>eng</gco:CharacterString>
     </gmd:language>
     <gmd:characterSet>
        <gmd:MD_CharacterSetCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_CharacterSetCode\"
                                 codeListValue=\"utf8\">utf8</gmd:MD_CharacterSetCode>
     </gmd:characterSet>
     <gmd:hierarchyLevel>
        <gmd:MD_ScopeCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ScopeCode\"
                          codeListValue=\"dataset\">dataset</gmd:MD_ScopeCode>
     </gmd:hierarchyLevel>
     <gmd:contact gco:nilReason=\"missing\"/>
     <gmd:dateStamp>
        <gco:DateTime>2016-07-08T11:17:34.508-04:00</gco:DateTime>
     </gmd:dateStamp>
     <gmd:metadataStandardName>
        <gco:CharacterString>ISO 19115-2 Geographic Information - Metadata Part 2 Extensions for imagery and gridded data</gco:CharacterString>
     </gmd:metadataStandardName>
     <gmd:metadataStandardVersion>
        <gco:CharacterString>ISO 19115-2:2009(E)</gco:CharacterString>
     </gmd:metadataStandardVersion>
     <gmd:identificationInfo>
        <gmd:MD_DataIdentification>
           <gmd:citation>
              <gmd:CI_Citation>
                 <gmd:title>
                    <gco:CharacterString>ASTER Level 1 precision terrain corrected registered at-sensor radiance V003</gco:CharacterString>
                 </gmd:title>
                 <gmd:date>
                    <gmd:CI_Date>
                       <gmd:date>
                          <gco:DateTime>2012-01-19T18:00:00.000Z</gco:DateTime>
                       </gmd:date>
                       <gmd:dateType>
                          <gmd:CI_DateTypeCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_DateTypeCode\"
                                               codeListValue=\"revision\">revision</gmd:CI_DateTypeCode>
                       </gmd:dateType>
                    </gmd:CI_Date>
                 </gmd:date>
                 <gmd:date>
                    <gmd:CI_Date>
                       <gmd:date>
                          <gco:DateTime>2012-01-11T10:00:00.000Z</gco:DateTime>
                       </gmd:date>
                       <gmd:dateType>
                          <gmd:CI_DateTypeCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_DateTypeCode\"
                                               codeListValue=\"creation\">creation</gmd:CI_DateTypeCode>
                       </gmd:dateType>
                    </gmd:CI_Date>
                 </gmd:date>
                 <gmd:edition/>
                 <gmd:identifier>
                    <gmd:MD_Identifier>
                       <gmd:code>
                          <gco:CharacterString>GLDAS_NOAH10_3H.2.0:GLDAS_NOAH10_3H.A19480101.0300.020.nc4</gco:CharacterString>
                       </gmd:code>
                    </gmd:MD_Identifier>
                 </gmd:identifier>
                 <gmd:otherCitationDetails>
                    <gco:CharacterString/>
                 </gmd:otherCitationDetails>
              </gmd:CI_Citation>
           </gmd:citation>
           <gmd:abstract gco:nilReason=\"inapplicable\"/>
           <gmd:purpose gco:nilReason=\"missing\"/>
           <gmd:graphicOverview>
              <gmd:MD_BrowseGraphic>
                 <gmd:fileName>
                    <gmx:FileName src=\"http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5/GLDAS_NOAH10_3H.2.0:GLDAS_NOAH10_3H.A19480101.0300.020.jpg\"/>
                 </gmd:fileName>
                 <gmd:fileDescription>
                    <gco:CharacterString>Some image</gco:CharacterString>
                 </gmd:fileDescription>
                 <gmd:fileType>
                    <gco:CharacterString>image/jpeg</gco:CharacterString>
                 </gmd:fileType>
              </gmd:MD_BrowseGraphic>
           </gmd:graphicOverview>
           <gmd:aggregationInfo>
              <gmd:MD_AggregateInformation>
                 <gmd:aggregateDataSetName>
                    <gmd:CI_Citation>
                       <gmd:title>
                          <gco:CharacterString>ASTER Level 1 precision terrain corrected registered at-sensor radiance V003</gco:CharacterString>
                       </gmd:title>
                       <gmd:date gco:nilReason=\"unknown\"/>
                    </gmd:CI_Citation>
                 </gmd:aggregateDataSetName>
                 <gmd:aggregateDataSetIdentifier>
                    <gmd:MD_Identifier>
                       <gmd:code>
                          <gco:CharacterString>ASTER Level 1 precision terrain corrected registered at-sensor radiance V003</gco:CharacterString>
                       </gmd:code>
                    </gmd:MD_Identifier>
                 </gmd:aggregateDataSetIdentifier>
                 <gmd:associationType>
                    <gmd:DS_AssociationTypeCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#DS_AssociationTypeCode\"
                                                codeListValue=\"LargerWorkCitation\">LargerWorkCitation</gmd:DS_AssociationTypeCode>
                 </gmd:associationType>
              </gmd:MD_AggregateInformation>
           </gmd:aggregationInfo>
           <gmd:language>
              <gco:CharacterString>eng</gco:CharacterString>
           </gmd:language>
           <gmd:characterSet>
              <gmd:MD_CharacterSetCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_CharacterSetCode\"
                                       codeListValue=\"utf8\">utf8</gmd:MD_CharacterSetCode>
           </gmd:characterSet>
           <gmd:extent>
              <gmd:EX_Extent id=\"boundingExtent\">
                 <gmd:description gco:nilReason=\"unknown\"/>
              </gmd:EX_Extent>
           </gmd:extent>
           <gmd:supplementalInformation/>
           <gmd:processingLevel>
              <gmd:MD_Identifier>
                 <gmd:code>
                    <gco:CharacterString/>
                 </gmd:code>
                 <gmd:description>
                    <gco:CharacterString/>
                 </gmd:description>
              </gmd:MD_Identifier>
           </gmd:processingLevel>
        </gmd:MD_DataIdentification>
     </gmd:identificationInfo>
     <gmd:distributionInfo>
        <gmd:MD_Distribution>
           <gmd:distributor>
              <gmd:MD_Distributor>
                 <gmd:distributorContact gco:nilReason=\"missing\"/>
                 <gmd:distributionOrderProcess>
                    <gmd:MD_StandardOrderProcess>
                       <gmd:fees gco:nilReason=\"missing\"/>
                    </gmd:MD_StandardOrderProcess>
                 </gmd:distributionOrderProcess>
                 <gmd:distributorTransferOptions>
                    <gmd:MD_DigitalTransferOptions>
                       <gmd:onLine>
                          <gmd:CI_OnlineResource>
                             <gmd:linkage>
                                <gmd:URL>http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5/GLDAS_NOAH10_3H.2.0:GLDAS_NOAH10_3H.A19480101.0300.020.jpg</gmd:URL>
                             </gmd:linkage>
                             <gmd:applicationProfile>
                                <gco:CharacterString>image/jpeg</gco:CharacterString>
                             </gmd:applicationProfile>
                             <gmd:name>
                                <gco:CharacterString>Browse</gco:CharacterString>
                             </gmd:name>
                             <gmd:description>
                                <gco:CharacterString>Some image</gco:CharacterString>
                             </gmd:description>
                             <gmd:function>
                                <gmd:CI_OnLineFunctionCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode\"
                                                           codeListValue=\"information\">information</gmd:CI_OnLineFunctionCode>
                             </gmd:function>
                          </gmd:CI_OnlineResource>
                       </gmd:onLine>
                    </gmd:MD_DigitalTransferOptions>
                 </gmd:distributorTransferOptions>
              </gmd:MD_Distributor>
           </gmd:distributor>
        </gmd:MD_Distribution>
     </gmd:distributionInfo>
     <gmd:dataQualityInfo>
        <gmd:DQ_DataQuality>
           <gmd:scope>
              <gmd:DQ_Scope>
                 <gmd:level>
                    <gmd:MD_ScopeCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ScopeCode\"
                                      codeListValue=\"dataset\">dataset</gmd:MD_ScopeCode>
                 </gmd:level>
              </gmd:DQ_Scope>
           </gmd:scope>
           <gmd:lineage>
              <gmd:LI_Lineage>
                 <gmd:processStep>
                    <gmi:LE_ProcessStep>
                       <gmd:description gco:nilReason=\"unknown\"/>
                    </gmi:LE_ProcessStep>
                 </gmd:processStep>
              </gmd:LI_Lineage>
           </gmd:lineage>
        </gmd:DQ_DataQuality>
     </gmd:dataQualityInfo>
     <gmd:metadataMaintenance>
        <gmd:MD_MaintenanceInformation>
           <gmd:maintenanceAndUpdateFrequency>
              <gmd:MD_MaintenanceFrequencyCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_MaintenanceFrequencyCode\"
                                               codeListValue=\"irregular\">irregular</gmd:MD_MaintenanceFrequencyCode>
           </gmd:maintenanceAndUpdateFrequency>
           <gmd:maintenanceNote>
              <gco:CharacterString>Translated from ECHO using ECHOToISO.xsl Version: 1.32 (Dec. 9, 2015)</gco:CharacterString>
           </gmd:maintenanceNote>
        </gmd:MD_MaintenanceInformation>
     </gmd:metadataMaintenance>
     <gmi:acquisitionInformation>
        <gmi:MI_AcquisitionInformation/>
     </gmi:acquisitionInformation>
  </gmi:MI_Metadata>")

(def iso-granule-no-browse
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
  <gmi:MI_Metadata xmlns:gmi=\"http://www.isotc211.org/2005/gmi\"
                   xmlns:gco=\"http://www.isotc211.org/2005/gco\"
                   xmlns:gmd=\"http://www.isotc211.org/2005/gmd\"
                   xmlns:gmx=\"http://www.isotc211.org/2005/gmx\"
                   xmlns:gsr=\"http://www.isotc211.org/2005/gsr\"
                   xmlns:gss=\"http://www.isotc211.org/2005/gss\"
                   xmlns:gts=\"http://www.isotc211.org/2005/gts\"
                   xmlns:srv=\"http://www.isotc211.org/2005/srv\"
                   xmlns:gml=\"http://www.opengis.net/gml/3.2\"
                   xmlns:xlink=\"http://www.w3.org/1999/xlink\"
                   xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
                   xmlns:swe=\"http://schemas.opengis.net/sweCommon/2.0/\"
                   xmlns:eos=\"http://earthdata.nasa.gov/schema/eos\"
                   xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><!--Other Properties, all:0, coi:0,ii:0,si:0,pli:0,pri:0,qi:0,gi:0,ci:0,dk:0,pcc:0,icc:0,scc:0-->
     <gmd:fileIdentifier>
        <gco:CharacterString>gov.nasa.echo:GLDAS_NOAH10_3H.2.0:GLDAS_NOAH10_3H.A19480101.0300.020.nc4</gco:CharacterString>
     </gmd:fileIdentifier>
     <gmd:language>
        <gco:CharacterString>eng</gco:CharacterString>
     </gmd:language>
     <gmd:characterSet>
        <gmd:MD_CharacterSetCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_CharacterSetCode\"
                                 codeListValue=\"utf8\">utf8</gmd:MD_CharacterSetCode>
     </gmd:characterSet>
     <gmd:hierarchyLevel>
        <gmd:MD_ScopeCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ScopeCode\"
                          codeListValue=\"dataset\">dataset</gmd:MD_ScopeCode>
     </gmd:hierarchyLevel>
     <gmd:contact gco:nilReason=\"missing\"/>
     <gmd:dateStamp>
        <gco:DateTime>2016-07-08T11:17:34.508-04:00</gco:DateTime>
     </gmd:dateStamp>
     <gmd:metadataStandardName>
        <gco:CharacterString>ISO 19115-2 Geographic Information - Metadata Part 2 Extensions for imagery and gridded data</gco:CharacterString>
     </gmd:metadataStandardName>
     <gmd:metadataStandardVersion>
        <gco:CharacterString>ISO 19115-2:2009(E)</gco:CharacterString>
     </gmd:metadataStandardVersion>
     <gmd:identificationInfo>
        <gmd:MD_DataIdentification>
           <gmd:citation>
              <gmd:CI_Citation>
                 <gmd:title>
                    <gco:CharacterString>ASTER Level 1 precision terrain corrected registered at-sensor radiance V003</gco:CharacterString>
                 </gmd:title>
                 <gmd:date>
                    <gmd:CI_Date>
                       <gmd:date>
                          <gco:DateTime>2012-01-19T18:00:00.000Z</gco:DateTime>
                       </gmd:date>
                       <gmd:dateType>
                          <gmd:CI_DateTypeCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_DateTypeCode\"
                                               codeListValue=\"revision\">revision</gmd:CI_DateTypeCode>
                       </gmd:dateType>
                    </gmd:CI_Date>
                 </gmd:date>
                 <gmd:date>
                    <gmd:CI_Date>
                       <gmd:date>
                          <gco:DateTime>2012-01-11T10:00:00.000Z</gco:DateTime>
                       </gmd:date>
                       <gmd:dateType>
                          <gmd:CI_DateTypeCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_DateTypeCode\"
                                               codeListValue=\"creation\">creation</gmd:CI_DateTypeCode>
                       </gmd:dateType>
                    </gmd:CI_Date>
                 </gmd:date>
                 <gmd:edition/>
                 <gmd:identifier>
                    <gmd:MD_Identifier>
                       <gmd:code>
                          <gco:CharacterString>GLDAS_NOAH10_3H.2.0:GLDAS_NOAH10_3H.A19480101.0300.020.nc4</gco:CharacterString>
                       </gmd:code>
                    </gmd:MD_Identifier>
                 </gmd:identifier>
                 <gmd:otherCitationDetails>
                    <gco:CharacterString/>
                 </gmd:otherCitationDetails>
              </gmd:CI_Citation>
           </gmd:citation>
           <gmd:abstract gco:nilReason=\"inapplicable\"/>
           <gmd:purpose gco:nilReason=\"missing\"/>
           <gmd:aggregationInfo>
              <gmd:MD_AggregateInformation>
                 <gmd:aggregateDataSetName>
                    <gmd:CI_Citation>
                       <gmd:title>
                          <gco:CharacterString>ASTER Level 1 precision terrain corrected registered at-sensor radiance V003</gco:CharacterString>
                       </gmd:title>
                       <gmd:date gco:nilReason=\"unknown\"/>
                    </gmd:CI_Citation>
                 </gmd:aggregateDataSetName>
                 <gmd:aggregateDataSetIdentifier>
                    <gmd:MD_Identifier>
                       <gmd:code>
                          <gco:CharacterString>ASTER Level 1 precision terrain corrected registered at-sensor radiance V003</gco:CharacterString>
                       </gmd:code>
                    </gmd:MD_Identifier>
                 </gmd:aggregateDataSetIdentifier>
                 <gmd:associationType>
                    <gmd:DS_AssociationTypeCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#DS_AssociationTypeCode\"
                                                codeListValue=\"LargerWorkCitation\">LargerWorkCitation</gmd:DS_AssociationTypeCode>
                 </gmd:associationType>
              </gmd:MD_AggregateInformation>
           </gmd:aggregationInfo>
           <gmd:language>
              <gco:CharacterString>eng</gco:CharacterString>
           </gmd:language>
           <gmd:characterSet>
              <gmd:MD_CharacterSetCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_CharacterSetCode\"
                                       codeListValue=\"utf8\">utf8</gmd:MD_CharacterSetCode>
           </gmd:characterSet>
           <gmd:extent>
              <gmd:EX_Extent id=\"boundingExtent\">
                 <gmd:description gco:nilReason=\"unknown\"/>
              </gmd:EX_Extent>
           </gmd:extent>
           <gmd:supplementalInformation/>
           <gmd:processingLevel>
              <gmd:MD_Identifier>
                 <gmd:code>
                    <gco:CharacterString/>
                 </gmd:code>
                 <gmd:description>
                    <gco:CharacterString/>
                 </gmd:description>
              </gmd:MD_Identifier>
           </gmd:processingLevel>
        </gmd:MD_DataIdentification>
     </gmd:identificationInfo>
     <gmd:distributionInfo>
        <gmd:MD_Distribution>
           <gmd:distributor>
              <gmd:MD_Distributor>
                 <gmd:distributorContact gco:nilReason=\"missing\"/>
                 <gmd:distributionOrderProcess>
                    <gmd:MD_StandardOrderProcess>
                       <gmd:fees gco:nilReason=\"missing\"/>
                    </gmd:MD_StandardOrderProcess>
                 </gmd:distributionOrderProcess>
                 <gmd:distributorTransferOptions>
                    <gmd:MD_DigitalTransferOptions>
                       <gmd:onLine>
                          <gmd:CI_OnlineResource>
                             <gmd:linkage>
                                <gmd:URL>http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5/GLDAS_NOAH10_3H.2.0:GLDAS_NOAH10_3H.A19480101.0300.020.nc4</gmd:URL>
                             </gmd:linkage>
                             <gmd:applicationProfile>
                                <gco:CharacterString>application/x-netcdf</gco:CharacterString>
                             </gmd:applicationProfile>
                             <gmd:name>
                                <gco:CharacterString>Browse</gco:CharacterString>
                             </gmd:name>
                             <gmd:description>
                                <gco:CharacterString>OpenDAP URL</gco:CharacterString>
                             </gmd:description>
                             <gmd:function>
                                <gmd:CI_OnLineFunctionCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode\"
                                                           codeListValue=\"information\">information</gmd:CI_OnLineFunctionCode>
                             </gmd:function>
                          </gmd:CI_OnlineResource>
                       </gmd:onLine>
                    </gmd:MD_DigitalTransferOptions>
                 </gmd:distributorTransferOptions>
              </gmd:MD_Distributor>
           </gmd:distributor>
        </gmd:MD_Distribution>
     </gmd:distributionInfo>
     <gmd:dataQualityInfo>
        <gmd:DQ_DataQuality>
           <gmd:scope>
              <gmd:DQ_Scope>
                 <gmd:level>
                    <gmd:MD_ScopeCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ScopeCode\"
                                      codeListValue=\"dataset\">dataset</gmd:MD_ScopeCode>
                 </gmd:level>
              </gmd:DQ_Scope>
           </gmd:scope>
           <gmd:lineage>
              <gmd:LI_Lineage>
                 <gmd:processStep>
                    <gmi:LE_ProcessStep>
                       <gmd:description gco:nilReason=\"unknown\"/>
                    </gmi:LE_ProcessStep>
                 </gmd:processStep>
              </gmd:LI_Lineage>
           </gmd:lineage>
        </gmd:DQ_DataQuality>
     </gmd:dataQualityInfo>
     <gmd:metadataMaintenance>
        <gmd:MD_MaintenanceInformation>
           <gmd:maintenanceAndUpdateFrequency>
              <gmd:MD_MaintenanceFrequencyCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_MaintenanceFrequencyCode\"
                                               codeListValue=\"irregular\">irregular</gmd:MD_MaintenanceFrequencyCode>
           </gmd:maintenanceAndUpdateFrequency>
           <gmd:maintenanceNote>
              <gco:CharacterString>Translated from ECHO using ECHOToISO.xsl Version: 1.32 (Dec. 9, 2015)</gco:CharacterString>
           </gmd:maintenanceNote>
        </gmd:MD_MaintenanceInformation>
     </gmd:metadataMaintenance>
     <gmi:acquisitionInformation>
        <gmi:MI_AcquisitionInformation/>
     </gmi:acquisitionInformation>
  </gmi:MI_Metadata>")

(def constraints-path [:identificationInfo :MD_DataIdentification :resourceConstraints])

(def browse-path [:identificationInfo :MD_DataIdentification :graphicOverview])

(deftest echo10_to_iso19115
 (let [xsl (xslt/read-template (io/resource "xslt/echo10_to_iso19115.xsl"))]
  (testing "ECHO10 to ISO19115 Tranforms"
   (are3 [echo10 expected-iso path]
     (let [iso-parsed (x/parse-str (xslt/transform echo10 xsl))
           iso-element (xml/element-at-path iso-parsed path)
           expected-iso-parsed (x/parse-str expected-iso)
           expected-iso-element (xml/element-at-path expected-iso-parsed path)]
       (def the-iso (xslt/transform echo10 xsl))
       (def the-iso-element iso-element)
       (is (= expected-iso-element iso-element)))

     "Collection with no use constraints"
     echo10-collection-no-use-constraints iso-no-use-constraints constraints-path

     "Collection with use constraints"
     echo10-collection-with-use-constraints iso-with-use-constraints constraints-path

     "Granule with no browse"
     echo10-granule-no-browse iso-granule-no-browse browse-path

     "Granule with Online Resource URL type Browse"
     echo10-granule-browse iso-granule-browse browse-path

     "Granule with Online Resource URL type BROWSE"
     echo10-granule-browse-uppercase iso-granule-browse browse-path

     "Granule with Online Resource URL type browse"
     echo10-granule-browse-lowercase iso-granule-browse browse-path

     "Granule with Online Resource URL type BrOwSe"
     echo10-granule-browse-mixed-case iso-granule-browse browse-path))))
