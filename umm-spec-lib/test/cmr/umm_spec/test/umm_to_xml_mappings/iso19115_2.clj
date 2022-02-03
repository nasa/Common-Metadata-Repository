(ns cmr.umm-spec.test.umm-to-xml-mappings.iso19115-2
  "Tests to verify that ISO19115-2 is generated correctly."
  (:require
    [clojure.data.xml :as x]
    [clojure.string :as string]
    [clojure.test :refer :all]
    [cmr.common.util :refer [are3]]
    [cmr.common.xml :as xml]
    [cmr.umm-spec.models.umm-collection-models :as coll]
    [cmr.umm-spec.test.location-keywords-helper :as lkt]
    [cmr.umm-spec.umm-spec-core :as core]
    [cmr.umm-spec.umm-to-xml-mappings.iso19115-2 :as iso]
    [cmr.umm-spec.util :as u]
    [cmr.umm-spec.xml-to-umm-mappings.iso19115-2 :as parser]
    [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.additional-attribute :as aa]
    [cmr.umm-spec.xml-to-umm-mappings.iso-shared.archive-and-dist-info :as dist-info]
    [cmr.umm-spec.xml-to-umm-mappings.iso-shared.doi :as doi]))

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
                 <gmd:accessConstraints>
                   <gmd:MD_RestrictionCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#MD_RestrictionCode\" codeListValue=\"otherRestrictions\">otherRestrictions</gmd:MD_RestrictionCode>
                 </gmd:accessConstraints>
                 <gmd:otherConstraints>
                    <gco:CharacterString>Access Constraints Description:Dummy Comment</gco:CharacterString>
                 </gmd:otherConstraints>
                 <gmd:otherConstraints>
                    <gco:CharacterString>Access Constraints Value:0</gco:CharacterString>
                 </gmd:otherConstraints>
              </gmd:MD_LegalConstraints>
           </gmd:resourceConstraints>
           <!-- Associatd DOIs -->
           <gmd:aggregationInfo>
             <gmd:MD_AggregateInformation>
               <gmd:aggregateDataSetName>
                 <gmd:CI_Citation>
                   <gmd:title>
                     <gco:CharacterString>DOI 1 landing page title</gco:CharacterString>
                   </gmd:title>
                 </gmd:CI_Citation>
               </gmd:aggregateDataSetName>
               <gmd:aggregateDataSetIdentifier>
                 <gmd:MD_Identifier>
                   <gmd:authority>
                     <gmd:CI_Citation>
                       <gmd:title/>
                       <gmd:date/>
                       <gmd:citedResponsibleParty>
                         <gmd:CI_ResponsibleParty>
                           <gmd:organisationName>
                             <gco:CharacterString>https://doi.org</gco:CharacterString>
                           </gmd:organisationName>
                           <gmd:role>
                             <gmd:RoleCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#CI_RoleCode\" codeListValue=\"authority\">
                               authority
                             </gmd:RoleCode>
                           </gmd:role>
                         </gmd:CI_ResponsibleParty>
                       </gmd:citedResponsibleParty>
                     </gmd:CI_Citation>
                   </gmd:authority>
                   <gmd:code>
                     <gco:CharacterString>10.5678/AssociatedDOI1</gco:CharacterString>
                   </gmd:code>
                   <gmd:codeSpace>
                     <gco:CharacterString>gov.nasa.esdis.umm.associateddoi</gco:CharacterString>
                   </gmd:codeSpace>
                   <gmd:description>
                     <gco:CharacterString>Assocaited DOI 1</gco:CharacterString>
                   </gmd:description>
                 </gmd:MD_Identifier>
               </gmd:aggregateDataSetIdentifier>
               <gmd:associationType>
                 <gmd:DS_AssociationTypeCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#DS_AssociationTypeCode\"
                                             codeListValue=\"associatedDOI\">associatedDOI
                 </gmd:DS_AssociationTypeCode>
               </gmd:associationType>
             </gmd:MD_AggregateInformation>
           </gmd:aggregationInfo>
           <gmd:aggregationInfo>
             <gmd:MD_AggregateInformation>
               <gmd:aggregateDataSetName>
                 <gmd:CI_Citation>
                   <gmd:title>
                     <gco:CharacterString>DOI 2 landing page title</gco:CharacterString>
                   </gmd:title>
                 </gmd:CI_Citation>
               </gmd:aggregateDataSetName>
               <gmd:aggregateDataSetIdentifier>
                 <gmd:MD_Identifier>
                   <gmd:authority>
                     <gmd:CI_Citation>
                       <gmd:title/>
                       <gmd:date/>
                       <gmd:citedResponsibleParty>
                         <gmd:CI_ResponsibleParty>
                           <gmd:organisationName>
                             <gco:CharacterString>https://doi.org</gco:CharacterString>
                           </gmd:organisationName>
                           <gmd:role>
                             <gmd:RoleCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#CI_RoleCode\" codeListValue=\"authority\">
                               authority
                             </gmd:RoleCode>
                           </gmd:role>
                         </gmd:CI_ResponsibleParty>
                       </gmd:citedResponsibleParty>
                     </gmd:CI_Citation>
                   </gmd:authority>
                   <gmd:code>
                     <gco:CharacterString>10.5678/AssociatedDOI2</gco:CharacterString>
                   </gmd:code>
                   <gmd:codeSpace>
                     <gco:CharacterString>gov.nasa.esdis.umm.associateddoi</gco:CharacterString>
                   </gmd:codeSpace>
                   <gmd:description>
                     <gco:CharacterString>Assocaited DOI 2</gco:CharacterString>
                   </gmd:description>
                 </gmd:MD_Identifier>
               </gmd:aggregateDataSetIdentifier>
               <gmd:associationType>
                 <gmd:DS_AssociationTypeCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#DS_AssociationTypeCode\"
                                             codeListValue=\"associatedDOI\">associatedDOI
                 </gmd:DS_AssociationTypeCode>
               </gmd:associationType>
             </gmd:MD_AggregateInformation>
           </gmd:aggregationInfo>
           <gmd:language>
              <gco:CharacterString>eng</gco:CharacterString>
           </gmd:language>
           <gmd:characterSet>
              <gmd:MD_CharacterSetCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_CharacterSetCode\" codeListValue=\"utf8\">utf8</gmd:MD_CharacterSetCode>
           </gmd:characterSet>
           <gmd:extent>
            <gmd:EX_Extent id=\"boundingExtent\">
              <gmd:description>
                <gco:CharacterString>SpatialCoverageType=Horizontal, SpatialGranuleSpatialRepresentation=CARTESIAN, Temporal Range Type=Continuous Range, Time Type=UTC</gco:CharacterString>
              </gmd:description>
              <gmd:geographicElement>
                <gmd:EX_GeographicBoundingBox id=\"foo\">
                  <gmd:westBoundLongitude>
                    <gco:Decimal>-109.0</gco:Decimal>
                  </gmd:westBoundLongitude>
                  <gmd:eastBoundLongitude>
                    <gco:Decimal>11.0</gco:Decimal>
                  </gmd:eastBoundLongitude>
                  <gmd:southBoundLatitude>
                    <gco:Decimal>57.0</gco:Decimal>
                  </gmd:southBoundLatitude>
                  <gmd:northBoundLatitude>
                    <gco:Decimal>85.0</gco:Decimal>
                  </gmd:northBoundLatitude>
                </gmd:EX_GeographicBoundingBox>
              </gmd:geographicElement>
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
            <gmd:distributor xlink:href=\"DirectDistributionInformation\">
                <gmd:MD_Distributor>
                    <gmd:distributorContact gco:nilReason=\"inapplicable\"/>
                    <gmd:distributionOrderProcess>
                        <gmd:MD_StandardOrderProcess>
                            <gmd:orderingInstructions>
                                <gco:CharacterString>Region: us-east-2 S3BucketAndObjectPrefixNames: HiHo Off to work we go </gco:CharacterString>
                            </gmd:orderingInstructions>
                        </gmd:MD_StandardOrderProcess>
                    </gmd:distributionOrderProcess>
                    <gmd:distributorTransferOptions xlink:href=\"DirectDistributionInformation_S3CredentialsAPIEndpoint\">
                        <gmd:MD_DigitalTransferOptions>
                            <gmd:onLine>
                                <gmd:CI_OnlineResource>
                                    <gmd:linkage>
                                        <gmd:URL>https://find-a-good-s3-cred-api-endpoint.com</gmd:URL>
                                    </gmd:linkage>
                                    <gmd:description>
                                        <gco:CharacterString>The S3 credentials API endpoint.</gco:CharacterString>
                                    </gmd:description>
                                </gmd:CI_OnlineResource>
                            </gmd:onLine>
                        </gmd:MD_DigitalTransferOptions>
                    </gmd:distributorTransferOptions>
                    <gmd:distributorTransferOptions xlink:href=\"DirectDistributionInformation_S3CredentialsAPIDocumentationURL\">
                        <gmd:MD_DigitalTransferOptions>
                            <gmd:onLine>
                                <gmd:CI_OnlineResource>
                                    <gmd:linkage>
                                        <gmd:URL>https://find-a-good-s3-cred-api-document.com</gmd:URL>
                                    </gmd:linkage>
                                    <gmd:description>
                                        <gco:CharacterString>The S3 credentials API Documentation URL.</gco:CharacterString>
                                    </gmd:description>
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
                    <gmd:MD_ScopeCode codeList=\"http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ScopeCode\" codeListValue=\"series\">series</gmd:MD_ScopeCode>
                 </gmd:level>
              </gmd:DQ_Scope>
           </gmd:scope>
           <gmd:lineage>
              <gmd:LI_Lineage>
                 <gmd:processStep>
                    <gmi:LE_ProcessStep>
                       <gmd:description gco:nilReason=\"unknown\" />
                       <gmi:processingInformation>
                        <eos:EOS_Processing>
                          <gmi:identifier/>
                          <eos:otherPropertyType>
                            <gco:RecordType xlink:href=\"http://earthdata.nasa.gov/metadata/schema/eos/1.0/eos.xsd#xpointer(//element[@name='AdditionalAttributes'])\">Echo Additional Attributes</gco:RecordType>
                          </eos:otherPropertyType>
                          <eos:otherProperty>
                            <gco:Record>
                              <eos:AdditionalAttributes>
                                <eos:AdditionalAttribute>
                                  <eos:reference>
                                    <eos:EOS_AdditionalAttributeDescription>
                                      <eos:type>
                                        <eos:EOS_AdditionalAttributeTypeCode codeList=\"http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeTypeCode\" codeListValue=\"processingInformation\">processingInformation</eos:EOS_AdditionalAttributeTypeCode>
                                      </eos:type>
                                      <eos:name>
                                        <gco:CharacterString>SIPSMetGenVersion</gco:CharacterString>
                                      </eos:name>
                                      <eos:description>
                                        <gco:CharacterString>The version of the SIPSMetGen software used to produce the metadata file for this granule</gco:CharacterString>
                                      </eos:description>
                                      <eos:dataType>
                                        <eos:EOS_AdditionalAttributeDataTypeCode codeList=\"http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeDataTypeCode\" codeListValue=\"STRING\">STRING</eos:EOS_AdditionalAttributeDataTypeCode>
                                      </eos:dataType>
                                      <eos:measurementResolution>
                                        <gco:CharacterString>Measurement resolution</gco:CharacterString>
                                      </eos:measurementResolution>
                                      <eos:parameterRangeBegin>
                                        <gco:CharacterString>Parameter begin</gco:CharacterString>
                                      </eos:parameterRangeBegin>
                                      <eos:parameterRangeEnd>
                                        <gco:CharacterString>Parameter End</gco:CharacterString>
                                      </eos:parameterRangeEnd>
                                      <eos:parameterUnitsOfMeasure>
                                        <gco:CharacterString>Units of Measure</gco:CharacterString>
                                      </eos:parameterUnitsOfMeasure>
                                      <eos:parameterValueAccuracy>
                                        <gco:CharacterString>Parameter Value Accuracy</gco:CharacterString>
                                      </eos:parameterValueAccuracy>
                                      <eos:valueAccuracyExplanation>
                                        <gco:CharacterString>Value Accuracy Explanation</gco:CharacterString>
                                      </eos:valueAccuracyExplanation>
                                    </eos:EOS_AdditionalAttributeDescription>
                                  </eos:reference>
                                  <eos:value>
                                    <gco:CharacterString>A Value</gco:CharacterString>
                                  </eos:value>
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

(def constraints-path [:identificationInfo :MD_DataIdentification :resourceConstraints])

(deftest iso-constraints
  (testing "Use constraints"
   (are3 [expected-iso umm-map]
     (let [expected-iso-parsed (x/parse-str expected-iso)
           expected-iso-constraints (xml/element-at-path expected-iso-parsed constraints-path)
           generated-iso (iso/umm-c-to-iso19115-2-xml (coll/map->UMM-C umm-map))
           generated-iso-parsed (x/parse-str generated-iso)
           generated-iso-constraints (xml/element-at-path generated-iso-parsed constraints-path)]
       (is (= expected-iso-constraints generated-iso-constraints)))

    "No use constraints"
    iso-no-use-constraints {}

    "With access constraints"
    iso-with-use-constraints {:AccessConstraints {:Description "Dummy Comment" :Value 0}}

    "With use FreeAndOpenData flag constraints"
    (string/replace iso-no-use-constraints
                    #"<gmd:purpose gco:nilReason=\"missing\" />[ *|\n|\r]*<gmd:language>"
                    "<gmd:purpose gco:nilReason=\"missing\" />
                     <gmd:resourceConstraints>
                       <gmd:MD_LegalConstraints>
                         <gmd:useConstraints>
                           <gmd:MD_RestrictionCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#MD_RestrictionCode\" codeListValue=\"otherRestrictions\">otherRestrictions</gmd:MD_RestrictionCode>
                         </gmd:useConstraints>
                         <gmd:otherConstraints>
                           <gco:CharacterString>FreeAndOpenData:false</gco:CharacterString>
                         </gmd:otherConstraints>
                       </gmd:MD_LegalConstraints>
                     </gmd:resourceConstraints>
                    <gmd:language>")
    {:UseConstraints {:FreeAndOpenData false}})))


(deftest data-quality-info-additional-attributes
  (testing "additional attributes that should go to dataQualityInfo section are written out correctly"
    (let [parsed (#'parser/parse-iso19115-xml (lkt/setup-context-for-test)
                                              iso-with-use-constraints u/default-parsing-options)
          ;; all the parsed additional attributes are from dataQualityInfo and we use it as the expected value
          expected-additional-attributes (:AdditionalAttributes parsed)
          generated-iso (iso/umm-c-to-iso19115-2-xml parsed)
          ;; parse out the dataQualtiyInfo additional attributes
          parsed-additional-attributes (#'aa/parse-data-quality-info-additional-attributes
                                         generated-iso (:sanitize? u/default-parsing-options))]
      ;; validate against xml schema
      (is (empty? (core/validate-xml :collection :iso19115 generated-iso)))
      (is (not (empty? parsed-additional-attributes)))
      (is (= expected-additional-attributes parsed-additional-attributes)))))

(deftest granule-spatial-representation
  (testing "granule spatial representation is parsed correctly"
    (let [parsed (#'parser/parse-iso19115-xml (lkt/setup-context-for-test)
                                              iso-with-use-constraints u/default-parsing-options)
          gran-spatial-representation (get-in parsed [:SpatialExtent :GranuleSpatialRepresentation])]
      (is (= "CARTESIAN" gran-spatial-representation)))))

(deftest direct-distribution-information-test
  (testing "direct distribution information that should go to distribution section are
            written out correctly."
    (let [parsed (#'parser/parse-iso19115-xml (lkt/setup-context-for-test)
                                              iso-with-use-constraints u/default-parsing-options)
          ;; use the parsed out direct distribution info as the expected value
          expected-direct-distribution (:DirectDistributionInformation parsed)
          generated-iso (iso/umm-c-to-iso19115-2-xml parsed)
          ;; parse out the direct distribution information
          parsed-direct-distribution (dist-info/parse-direct-dist-info
                                         generated-iso parser/dist-info-xpath)]
      ;; validate against xml schema
      (is (empty? (core/validate-xml :collection :iso19115 generated-iso)))
      (is (not (empty? parsed-direct-distribution)))
      (is (= expected-direct-distribution parsed-direct-distribution)))))

(deftest associated-doi-test
  "Testing the associated DOIs"
  (are3 [iso-record expect-empty]
    (let [parsed (#'parser/parse-iso19115-xml (lkt/setup-context-for-test)
                                              iso-record
                                              u/default-parsing-options)
          ;; use the parsed associated DOIs as the expected value
          expected-associated-dois (:AssociatedDOIs parsed)
          generated-iso (iso/umm-c-to-iso19115-2-xml parsed)
          ;; parse out the associated DOIs
          parsed-associated-dois (doi/parse-associated-dois
                                         generated-iso parser/associated-doi-xpath)]
      ;; validate against xml schema
      (is (empty? (core/validate-xml :collection :iso19115 generated-iso)))
      (if expect-empty
        (is (empty? parsed-associated-dois))
        (is (not (empty? parsed-associated-dois))))
      (is (= expected-associated-dois parsed-associated-dois)))

    "Associated DOIs are written out correctly."
    iso-with-use-constraints
    false

    "Associated DOIs not used"
    iso-no-use-constraints
    true))
