(ns cmr.umm-spec.test.umm-to-xml-mappings.iso19115-2
  "Tests to verify that ISO19115-2 is generated correctly."
  (:require
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing]]
   [cmr.common.date-time-parser :as dtp]
   [cmr.common.util :as util :refer [are3]]
   [cmr.common.xml :as cx]
   [cmr.common.xml.parse :refer [value-of]]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.models.umm-collection-models :as coll]
   [cmr.umm-spec.umm-spec-core :as core]
   [cmr.umm-spec.umm-to-xml-mappings.iso19115-2 :as iso]
   [cmr.umm-spec.util :as u]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2 :as parser]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.additional-attribute :as aa]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.metadata-association :as ma]
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
                 <gmd:identifier>
                   <gmd:MD_Identifier>
                     <gmd:code>
                       <gco:CharacterString>10.5067/7ZGTHVZFAIDT</gco:CharacterString>
                     </gmd:code>
                     <gmd:codeSpace>
                       <gco:CharacterString>gov.nasa.esdis.umm.doi</gco:CharacterString>
                     </gmd:codeSpace>
                     <gmd:description>
                       <gco:CharacterString>A Digital Object Identifier (DOI)</gco:CharacterString>
                     </gmd:description>
                   </gmd:MD_Identifier>
                 </gmd:identifier>
                 <gmd:identifier>
                   <gmd:MD_Identifier>
                     <gmd:code>
                       <gco:CharacterString>B28C32HIKEHIKE</gco:CharacterString>
                     </gmd:code>
                     <gmd:codeSpace>
                       <gco:CharacterString>gov.nasa.esdis.umm.otheridentifier</gco:CharacterString>
                     </gmd:codeSpace>
                     <gmd:description>
                       <gco:CharacterString>Type: Other DescriptionOfOtherType: DAAC-ID</gco:CharacterString>
                     </gmd:description>
                   </gmd:MD_Identifier>
                 </gmd:identifier>
                 <gmd:identifier>
                   <gmd:MD_Identifier>
                     <gmd:code>
                       <gco:CharacterString>Stage 1 Validation</gco:CharacterString>
                     </gmd:code>
                     <gmd:codeSpace>
                       <gco:CharacterString>gov.nasa.esdis.umm.datamaturity</gco:CharacterString>
                     </gmd:codeSpace>
                     <gmd:description>
                       <gco:CharacterString>Data Maturity</gco:CharacterString>
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
          <gmd:resourceFormat>
             <gmd:MD_Format>
               <gmd:name>
                  <gco:CharacterString>FileNamingConvention</gco:CharacterString>
               </gmd:name>
               <gmd:version gco:nilReason=\"inapplicable\"/>
               <gmd:specification>
                  <gco:CharacterString>FileNameConvention: YYYYDDMM.ZIPO ConventionDescription: Some test value</gco:CharacterString>
               </gmd:specification>
             </gmd:MD_Format>
           </gmd:resourceFormat>
           <gmd:resourceFormat>
             <gmd:MD_Format>
               <gmd:name>
                  <gco:CharacterString>Binary</gco:CharacterString>
               </gmd:name>
               <gmd:version gco:nilReason=\"inapplicable\"/>
               <gmd:specification>
                 <gco:CharacterString>FormatDescription: ABC Binary FormatType: Supported</gco:CharacterString>
               </gmd:specification>
             </gmd:MD_Format>
           </gmd:resourceFormat>
           <!-- Metadata association-->
           <gmd:aggregationInfo>
             <gmd:MD_AggregateInformation>
               <gmd:aggregateDataSetName>
                 <gmd:CI_Citation>
                   <gmd:title>
                     <gco:CharacterString>Entry Id</gco:CharacterString>
                   </gmd:title>
                   <gmd:date gco:nilReason=\"unknown\"></gmd:date>
                   <gmd:edition>
                      <gco:CharacterString>6.1NRT</gco:CharacterString>
                   </gmd:edition>
                   <gmd:otherCitationDetails>
                     <gco:CharacterString>Parent collection for child product MYD021KM.</gco:CharacterString>
                   </gmd:otherCitationDetails>
                 </gmd:CI_Citation>
               </gmd:aggregateDataSetName>
               <gmd:aggregateDataSetIdentifier>
                 <gmd:MD_Identifier>
                   <gmd:code>
                     <gco:CharacterString>Entry Id</gco:CharacterString>
                   </gmd:code>
                 </gmd:MD_Identifier>
               </gmd:aggregateDataSetIdentifier>
               <gmd:associationType>
                 <gmd:DS_AssociationTypeCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#DS_AssociationTypeCode\"
                                             codeListValue=\"parent\">PARENT
                 </gmd:DS_AssociationTypeCode>
               </gmd:associationType>
             </gmd:MD_AggregateInformation>
           </gmd:aggregationInfo>
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
                             <gmd:CI_RoleCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#CI_RoleCode\" codeListValue=\"authority\">
                               authority
                             </gmd:CI_RoleCode>
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
                             <gmd:CI_RoleCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#CI_RoleCode\" codeListValue=\"authority\">
                               authority
                             </gmd:CI_RoleCode>
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
                 <gmd:DS_AssociationTypeCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/eosCodelists.xml#DS_AssociationTypeCode\"
                                             codeListValue=\"parentDataset\">Parent_Dataset
                 </gmd:DS_AssociationTypeCode>
               </gmd:associationType>
             </gmd:MD_AggregateInformation>
           </gmd:aggregationInfo>
           <gmd:aggregationInfo>
             <gmd:MD_AggregateInformation>
               <gmd:aggregateDataSetName>
                 <gmd:CI_Citation>
                   <gmd:title>
                     <gco:CharacterString>DOI 3 landing page title</gco:CharacterString>
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
                             <gmd:CI_RoleCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#CI_RoleCode\" codeListValue=\"authority\">
                               authority
                             </gmd:CI_RoleCode>
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
                     <gco:CharacterString>Assocaited DOI 3</gco:CharacterString>
                   </gmd:description>
                 </gmd:MD_Identifier>
               </gmd:aggregateDataSetIdentifier>
               <gmd:associationType>
                 <gmd:DS_AssociationTypeCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/eosCodelists.xml#DS_AssociationTypeCode\"
                                             codeListValue=\"other\">Description type of other
                 </gmd:DS_AssociationTypeCode>
               </gmd:associationType>
             </gmd:MD_AggregateInformation>
           </gmd:aggregationInfo>
           <gmd:aggregationInfo>
             <gmd:MD_AggregateInformation>
               <gmd:aggregateDataSetName>
                 <gmd:CI_Citation>
                   <gmd:title>
                     <gco:CharacterString>DOI Previous Version</gco:CharacterString>
                   </gmd:title>
                   <gmd:date gco:nilReason=\"missing\"/>
                 </gmd:CI_Citation>
               </gmd:aggregateDataSetName>
               <gmd:aggregateDataSetIdentifier>
                 <gmd:MD_Identifier>
                   <gmd:authority>
                     <gmd:CI_Citation>
                       <gmd:title gco:nilReason=\"inapplicable\"/>
                       <gmd:date gco:nilReason=\"inapplicable\"/>
                       <gmd:edition>
                         <gco:CharacterString>Previous Version Version</gco:CharacterString>
                       </gmd:edition>
                       <gmd:editionDate><gco:Date>2024-01-01</gco:Date></gmd:editionDate>
                       <gmd:otherCitationDetails>
                         <gco:CharacterString>Previous Version Description Text</gco:CharacterString>
                       </gmd:otherCitationDetails>
                     </gmd:CI_Citation>
                   </gmd:authority>
                   <gmd:code><gco:CharacterString>10.5067/7ZGTHVZFAIDG</gco:CharacterString></gmd:code>
                   <gmd:codeSpace><gco:CharacterString>gov.nasa.esdis.umm.doi.previousversion</gco:CharacterString></gmd:codeSpace>
                   <gmd:description><gco:CharacterString>DOI Previous Version</gco:CharacterString></gmd:description>
                 </gmd:MD_Identifier>
               </gmd:aggregateDataSetIdentifier>
               <gmd:associationType>
                 <gmd:DS_AssociationTypeCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/eosCodelists.xml#EOS_AssociationTypeCode\" codeListValue=\"doiPreviousVersion\">DOI_Previous_Version</gmd:DS_AssociationTypeCode>
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
              <gmd:temporalElement uuidref=\"temporal_extent_1\">
                <gmd:EX_TemporalExtent>
                  <gmd:extent>
                    <gml:TimePeriod gml:id=\"temporal_extent_1\">
                      <gml:beginPosition>2003-03-26T00:00:00.000Z</gml:beginPosition>
                      <gml:endPosition>2003-07-15T23:59:59.999Z</gml:endPosition>
                      <gml:timeInterval unit=\"second\">30</gml:timeInterval>
                    </gml:TimePeriod>
                  </gmd:extent>
                </gmd:EX_TemporalExtent>
              </gmd:temporalElement>
              <gmd:temporalElement uuidref=\"temporal_extent_2\">
                <gmd:EX_TemporalExtent>
                  <gmd:extent>
                    <gml:TimePeriod gml:id=\"temporal_extent_2\">
                      <gml:beginPosition>2003-03-26T00:00:00.000Z</gml:beginPosition>
                      <gml:endPosition indeterminatePosition=\"now\"/>
                    </gml:TimePeriod>
                  </gmd:extent>
                </gmd:EX_TemporalExtent>
              </gmd:temporalElement>
              <gmd:temporalElement uuidref=\"temporal_extent_2\">
                <gmd:EX_TemporalExtent>
                  <gmd:extent>
                    <gml:TimeInstant gml:id=\"temporal_extent_2_resolution\">
                      <gml:timePosition>Varies</gml:timePosition>
                    </gml:TimeInstant>
                  </gmd:extent>
                </gmd:EX_TemporalExtent>
              </gmd:temporalElement>
              <gmd:temporalElement uuidref=\"temporal_extent_3\">
                <gmd:EX_TemporalExtent>
                  <gmd:extent>
                    <gml:TimeInstant gml:id=\"temporal_extent_3\">
                      <gml:timePosition>2003-07-15T23:59:59.999Z</gml:timePosition>
                     </gml:TimeInstant>
                  </gmd:extent>
                </gmd:EX_TemporalExtent>
              </gmd:temporalElement>
              <gmd:temporalElement uuidref=\"temporal_extent_3\">
                <gmd:EX_TemporalExtent>
                  <gmd:extent>
                    <gml:TimeInstant gml:id=\"temporal_extent_3_resolution\">
                      <gml:timePosition>Constant</gml:timePosition>
                    </gml:TimeInstant>
                  </gmd:extent>
                </gmd:EX_TemporalExtent>
              </gmd:temporalElement>
             </gmd:EX_Extent>
           </gmd:extent>
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
     <gmd:identificationInfo>
       <gmd:MD_DataIdentification>
         <gmd:extent>
           <gmd:EX_Extent>
             <gmd:temporalElement uuidref=\"temporal_extent_4\">
                <gmd:EX_TemporalExtent>
                  <gmd:extent>
                    <gml:TimeInstant gml:id=\"temporal_extent_4_resolution\">
                      <gml:timePosition>Varies</gml:timePosition>
                    </gml:TimeInstant>
                  </gmd:extent>
                </gmd:EX_TemporalExtent>
              </gmd:temporalElement>
           </gmd:EX_Extent>
         </gmd:extent>
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
           <gmd:report uuidref=\"temporal_extent_2\">
             <gmd:DQ_AccuracyOfATimeMeasurement>
               <gmd:measureIdentification>
                 <gmd:MD_Identifier>
                   <gmd:code>
                     <gco:CharacterString>PrecisionOfSeconds</gco:CharacterString>
                   </gmd:code>
                 </gmd:MD_Identifier>
               </gmd:measureIdentification>
               <gmd:result>
                 <gmd:DQ_QuantitativeResult>
                   <gmd:valueUnit gco:nilReason=\"inapplicable\"/>
                   <gmd:value>
                     <gco:Record xsi:type=\"gco:Real_PropertyType\">
                       <gco:Real>30</gco:Real>
                     </gco:Record>
                   </gmd:value>
                 </gmd:DQ_QuantitativeResult>
               </gmd:result>
             </gmd:DQ_AccuracyOfATimeMeasurement>
           </gmd:report>
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

(declare expected-iso umm-map)
(deftest iso-constraints
  (testing "Use constraints"
   (are3 [expected-iso umm-map]
     (let [expected-iso-parsed (xml/parse-str expected-iso)
           expected-iso-constraints (cx/element-at-path expected-iso-parsed constraints-path)
           generated-iso (iso/umm-c-to-iso19115-2-xml (coll/map->UMM-C umm-map))
           generated-iso-parsed (xml/parse-str generated-iso)
           generated-iso-constraints (cx/element-at-path generated-iso-parsed constraints-path)]
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
    (let [parsed (#'parser/parse-iso19115-xml iso-with-use-constraints u/default-parsing-options)
          ;; all the parsed additional attributes are from dataQualityInfo and we use it as the expected value
          expected-additional-attributes (:AdditionalAttributes parsed)
          generated-iso (iso/umm-c-to-iso19115-2-xml parsed)
          ;; parse out the dataQualtiyInfo additional attributes
          parsed-additional-attributes (#'aa/parse-data-quality-info-additional-attributes
                                         generated-iso (:sanitize? u/default-parsing-options))]
      ;; validate against xml schema
      (is (empty? (core/validate-xml :collection :iso19115 generated-iso)))
      (is (seq parsed-additional-attributes))
      (is (= expected-additional-attributes parsed-additional-attributes)))))

(deftest granule-spatial-representation
  (testing "granule spatial representation is parsed correctly"
    (let [parsed (#'parser/parse-iso19115-xml iso-with-use-constraints u/default-parsing-options)
          gran-spatial-representation (get-in parsed [:SpatialExtent :GranuleSpatialRepresentation])]
      (is (= "CARTESIAN" gran-spatial-representation)))))

(deftest direct-distribution-information-test
  (testing "direct distribution information that should go to distribution section are
            written out correctly."
    (let [parsed (#'parser/parse-iso19115-xml iso-with-use-constraints u/default-parsing-options)
          ;; use the parsed out direct distribution info as the expected value
          expected-direct-distribution (:DirectDistributionInformation parsed)
          generated-iso (iso/umm-c-to-iso19115-2-xml parsed)
          ;; parse out the direct distribution information
          parsed-direct-distribution (dist-info/parse-direct-dist-info
                                         generated-iso parser/dist-info-xpath)]
      ;; validate against xml schema
      (is (empty? (core/validate-xml :collection :iso19115 generated-iso)))
      (is (seq parsed-direct-distribution))
      (is (= expected-direct-distribution parsed-direct-distribution)))))

(declare iso-record expect-empty)
(deftest associated-doi-test
  (testing "Testing the associated DOIs"
    (are3 [iso-record expect-empty]
          (let [parsed (#'parser/parse-iso19115-xml iso-record
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
              (is (seq parsed-associated-dois)))
            (is (= expected-associated-dois parsed-associated-dois)))

          "Associated DOIs are written out correctly."
          iso-with-use-constraints
          false

          "Associated DOIs not used"
          iso-no-use-constraints
          true)))

(deftest associated-metadata-test
  (testing "Testing the associated metadata"
    (are3 [iso-record expect-empty]
          (let [parsed (#'parser/parse-iso19115-xml iso-record
                                                    u/default-parsing-options)
                ;; use the parsed associated DOIs as the expected value
                expected-metadata-associations (:MetadataAssociations parsed)
                generated-iso (iso/umm-c-to-iso19115-2-xml parsed)
                ;; parse out the associated DOIs
                parsed-metadata-associations (ma/xml-elem->metadata-associations generated-iso)]
            ;; validate against xml schema
            (is (empty? (core/validate-xml :collection :iso19115 generated-iso)))
            (if expect-empty
              (is (empty? parsed-metadata-associations))
              (is (seq parsed-metadata-associations)))
            (is (= expected-metadata-associations parsed-metadata-associations)))

           "Associated Metadata are written out correctly."
           iso-with-use-constraints
           false

           "Associated Metadata are not used"
           iso-no-use-constraints
           true)))

(deftest previous-version-test
  (testing "Testing the previous version metadata"
    (are3 [iso-record expect-empty]
          (let [parsed (#'parser/parse-iso19115-xml iso-record
                                                    u/default-parsing-options)
                ;; use the parsed doi previous version as the expected value
                expected-previous-version (get-in parsed [:DOI :PreviousVersion])
                generated-iso (iso/umm-c-to-iso19115-2-xml parsed)
                ;; parse out the doi previous version
                parsed-previous-version (doi/parse-previous-version generated-iso)]
            ;; validate against xml schema
            (is (empty? (core/validate-xml :collection :iso19115 generated-iso)))
            (if expect-empty
              (is (empty? parsed-previous-version))
              (is (seq parsed-previous-version)))
            (is (= expected-previous-version parsed-previous-version)))

          "Previous version is written out correctly."
          iso-with-use-constraints
          false

          "Previous Version is not used"
          iso-no-use-constraints
          true)))

(deftest other-identifiers-test
  (testing "Testing the Other Identifiers translation"
    (are3 [iso-record expect-empty]
          (let [parsed (#'parser/parse-iso19115-xml iso-record
                                                    u/default-parsing-options)
                ;; use the parsed other identifiers as the expected value
                expected-other-identifier (:OtherIdentifiers parsed)
                generated-iso (iso/umm-c-to-iso19115-2-xml parsed)
                ;; parse out the other identifiers
                parsed-other-identifier (parser/parse-other-identifiers generated-iso)]
            ;; validate against xml schema
            (is (empty? (core/validate-xml :collection :iso19115 generated-iso)))
            (if expect-empty
              (is (empty? parsed-other-identifier))
              (is (seq parsed-other-identifier)))
            (is (= expected-other-identifier parsed-other-identifier)))

          "Other identifiers is written out correctly."
          iso-with-use-constraints
          false

          "Other identifiers is not used"
          iso-no-use-constraints
          true)))

(deftest file-naming-convention-test
  (testing "Testing file naming convention translation"
    (are3 [iso-record expect-empty]
          (let [parsed (#'parser/parse-iso19115-xml iso-record
                                                    u/default-parsing-options)
                ;; use the parsed file naming resource as the expected value
                expected-convention (:FileNamingConvention parsed)
                generated-iso (iso/umm-c-to-iso19115-2-xml parsed)
                ;; parse out the resource format
                parsed-convention (parser/parse-file-naming-convention generated-iso)]
            ;; validate against xml schema
            (is (empty? (core/validate-xml :collection :iso19115 generated-iso)))
            (if expect-empty
              (is (empty? parsed-convention))
              (is (seq parsed-convention)))
            (is (= expected-convention parsed-convention)))

          "Naming Convention is written out correctly."
          iso-with-use-constraints
          false

          "Naming Convention is not used"
          iso-no-use-constraints
          true)))

(deftest archive-format-test
  (testing "Testing the archive format translation"
    (are3 [iso-record expect-empty]
          (let [parsed (#'parser/parse-iso19115-xml iso-record
                                                    u/default-parsing-options)
                ;; use the parsed archive format as the expected value
                expected-format (get-in parsed [:ArchiveAndDistributionInformation :FileArchiveInformation])
                generated-iso (iso/umm-c-to-iso19115-2-xml parsed)
                ;; parse out the archive format
                parsed-format (dist-info/parse-archive-info generated-iso parser/archive-info-xpath)]
            ;; validate against xml schema
            (is (empty? (core/validate-xml :collection :iso19115 generated-iso)))
            (if expect-empty
              (is (empty? parsed-format))
              (is (seq parsed-format)))
            (is (= expected-format parsed-format)))

          "Archive format is written out correctly."
          iso-with-use-constraints
          false

          "Archive format is not used"
          iso-no-use-constraints
          true)))

(deftest data-maturity-test
  (testing "Testing the data maturity translation"
    (are3 [iso-record expect-empty]
          (let [parsed (#'parser/parse-iso19115-xml iso-record
                                                    u/default-parsing-options)
                ;; use the parsed archive format as the expected value
                expected-maturity (:DataMaturity parsed)
                generated-iso (iso/umm-c-to-iso19115-2-xml parsed)
                ;; parse out the archive format
                parsed-maturity (parser/parse-data-maturity generated-iso)]
            ;; validate against xml schema
            (is (empty? (core/validate-xml :collection :iso19115 generated-iso)))
            (if expect-empty
              (is (empty? parsed-maturity))
              (is (seq parsed-maturity)))
            (is (= expected-maturity parsed-maturity)))

          "Data maturity is written out correctly."
          iso-with-use-constraints
          false

          "Data Maturity is not used"
          iso-no-use-constraints
          true)))

(deftest temporal-extent-with-no-temporal-data-test
  (testing "Testing the temporal extent translation with no temporal data"
    (let [parsed (#'parser/parse-iso19115-xml iso-no-use-constraints
                                              u/default-parsing-options)
          generated-iso (iso/umm-c-to-iso19115-2-xml parsed)
          ;; use the parsed temporal extent as the expected value
          expected-temporal-extent (:TemporalExtents parsed)
          expected-date-time (-> expected-temporal-extent
                                 first
                                 (:RangeDateTimes)
                                 first
                                 (:BeginningDateTime)
                                 dtp/clj-time->date-time-str)

          ;; parse out the temporal extent
          parsed-temporal-extent (parser/parse-temporal-extents generated-iso)
          parsed-date-time (-> parsed-temporal-extent
                                  first
                                  (:RangeDateTimes)
                                  first
                                  (:BeginningDateTime))]
      ;; validate against xml schema
      (is (empty? (core/validate-xml :collection :iso19115 generated-iso)))
      (is (= expected-date-time parsed-date-time)))))

(deftest temporal-extent-with-temporal-data-test
  (testing "Testing the temporal extent translation"
    (let [parsed (#'parser/parse-iso19115-xml iso-with-use-constraints
                                              u/default-parsing-options)
          generated-iso (iso/umm-c-to-iso19115-2-xml parsed)
          ;; use the parsed temporal extent as the expected value
          expected-temporal-extent (set (:TemporalExtents parsed))

          ;; parse out the temporal extent
          parsed-temporal-extent (set (parser/parse-temporal-extents generated-iso))]
      ;; validate against xml schema
      (is (empty? (core/validate-xml :collection :iso19115 generated-iso)))
      (is (seq parsed-temporal-extent))
      (is (= expected-temporal-extent parsed-temporal-extent)))))

(deftest find-temporal-resolution-non-value-unit-test
  (testing "Finding the temporal resolution non value unit"
    (let [temporal-extents-list (select iso-with-use-constraints (str parser/temporal-xpath
                                                                      "[contains(@uuidref,'temporal_extent_2')]"))]
      (is (= "Varies"
             (some #(parser/find-temporal-resolution-non-value-unit % "temporal_extent_2_resolution")
                   temporal-extents-list))))))

(deftest find-temporal-resolution-value-unit-test
  (testing "Finding the temporal resolution value and unit"
    (let [temporal-extents-list (select iso-with-use-constraints (str parser/temporal-xpath
                                                                      "[contains(@uuidref,'temporal_extent_1')]"))]
      (is (= {:Unit "Second"
              :Value 30}
             (some #(parser/find-temporal-resolution-value-unit %) temporal-extents-list))))))

(deftest parse-temporal-resolution-test
  (testing "testing parse temporal resolution"
    (is (= '({:Value 30 :Unit "Second"}
             {:Unit "Varies"}
             {:Unit "Constant"}
             {:Unit "Varies"})
        (for [temporal-group (group-by #(value-of % "@uuidref") (select iso-with-use-constraints parser/temporal-xpath))
              :let [temporal-extents-list (val temporal-group)]]
          (parser/parse-temporal-resolution temporal-extents-list (key temporal-group)))))))

(def umm-temporal-test-record
  {:TemporalExtents
   [{:SingleDateTimes [(dtp/parse-datetime "1982-09-13T10:57:10.054Z")
                       (dtp/parse-datetime "2055-08-18T04:35:47.867Z")]
     :TemporalResolution {:Unit "Week", :Value 0.0}}
    {:PrecisionOfSeconds 0
     :SingleDateTimes [(dtp/parse-datetime "1995-07-12T07:40:24.568Z")
                       (dtp/parse-datetime "2079-05-10T11:44:01.003Z")
                       (dtp/parse-datetime "2013-06-28T19:40:49.678Z")]
     :TemporalResolution {:Unit "Month", :Value 1.0}}
    {:RangeDateTimes [{:BeginningDateTime (dtp/parse-datetime "2047-09-02T00:47:50.343Z")
                       :EndingDateTime (dtp/parse-datetime "2048-06-27T07:50:49.335Z")}]
     :TemporalResolution {:Unit "Varies"}}]})

(deftest generate-temporal-umm-maps-test
  (testing "Testing generating the temporal extent temporary structure to simplify adding this data to ISO."
    (is (= '({:Id "temporal_extent_6" :Begin "2047-09-02T00:47:50.343Z" :End "2048-06-27T07:50:49.335Z"}
             {:Id "temporal_extent_6" :Instant "" :Unit "Varies"}
             {:Id "temporal_extent_3" :Instant "1995-07-12T07:40:24.568Z"}
             {:Id "temporal_extent_3" :Begin "" :End "" :Value 1.0 :Unit "month"}
             {:Id "temporal_extent_4" :Instant "2079-05-10T11:44:01.003Z"}
             {:Id "temporal_extent_4" :Begin "" :End "" :Value 1.0 :Unit "month"}
             {:Id "temporal_extent_5" :Instant "2013-06-28T19:40:49.678Z"}
             {:Id "temporal_extent_5" :Begin "" :End "" :Value 1.0 :Unit "month"}
             {:Id "temporal_extent_1" :Instant "1982-09-13T10:57:10.054Z"}
             {:Id "temporal_extent_1" :Begin "" :End "" :Value 0.0 :Unit "day"}
             {:Id "temporal_extent_2" :Instant "2055-08-18T04:35:47.867Z"}
             {:Id "temporal_extent_2" :Begin "" :End "" :Value 0.0 :Unit "day"})
           (flatten (util/remove-nils-empty-maps-seqs (iso/generate-temporal-umm-maps (:TemporalExtents umm-temporal-test-record))))))))
