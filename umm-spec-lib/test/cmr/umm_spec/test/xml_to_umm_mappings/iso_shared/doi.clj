(ns cmr.umm-spec.test.xml-to-umm-mappings.iso-shared.doi
  (:require
   [clojure.test :refer :all]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.doi :as doi]
   [cmr.umm-spec.xml-to-umm-mappings.iso-smap :as smap]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2 :as mends]))

(def base-valid-example-iso-mends-doi-xml
  "<gmi:MI_Metadata xmlns:eos=\"http://earthdata.nasa.gov/schema/eos\"
    xmlns:gco=\"http://www.isotc211.org/2005/gco\"
    xmlns:gmd=\"http://www.isotc211.org/2005/gmd\"
    xmlns:gmi=\"http://www.isotc211.org/2005/gmi\"
    xmlns:gml=\"http://www.opengis.net/gml/3.2\"
    xmlns:gmx=\"http://www.isotc211.org/2005/gmx\"
    xmlns:gsr=\"http://www.isotc211.org/2005/gsr\"
    xmlns:gss=\"http://www.isotc211.org/2005/gss\"
    xmlns:gts=\"http://www.isotc211.org/2005/gts\"
    xmlns:srv=\"http://www.isotc211.org/2005/srv\"
    xmlns:xlink=\"http://www.w3.org/1999/xlink\"
    xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"
    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
    xsi:schemaLocation=\"http://earthdata.nasa.gov/schema/eos https://cdn.earthdata.nasa.gov/iso/eos/1.0/eos.xsd
                        http://www.isotc211.org/2005/gco https://cdn.earthdata.nasa.gov/iso/gco/1.0/gco.xsd
                        http://www.isotc211.org/2005/gmd https://cdn.earthdata.nasa.gov/iso/gmd/1.0/gmd.xsd
                        http://www.isotc211.org/2005/gmi https://cdn.earthdata.nasa.gov/iso/gmi/1.0/gmi.xsd
                        http://www.opengis.net/gml/3.2 https://cdn.earthdata.nasa.gov/iso/gml/1.0/gml.xsd
                        http://www.isotc211.org/2005/gmx https://cdn.earthdata.nasa.gov/iso/gmx/1.0/gmx.xsd
                        http://www.isotc211.org/2005/gsr https://cdn.earthdata.nasa.gov/iso/gsr/1.0/gsr.xsd
                        http://www.isotc211.org/2005/gss https://cdn.earthdata.nasa.gov/iso/gss/1.0/gss.xsd
                        http://www.isotc211.org/2005/gts https://cdn.earthdata.nasa.gov/iso/gts/1.0/gts.xsd
                        http://www.isotc211.org/2005/srv https://cdn.earthdata.nasa.gov/iso/srv/1.0/srv.xsd\">
    <gmd:identificationInfo>
        <gmd:MD_DataIdentification>
            <gmd:citation>
                <gmd:CI_Citation>
                    <!-- ShortName -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:code>
                                <gco:CharacterString>Collection-Short-Name</gco:CharacterString>
                            </gmd:code>
                            <gmd:codeSpace>
                                <gco:CharacterString>gov.nasa.esdis.umm.shortname</gco:CharacterString>
                            </gmd:codeSpace>
                            <gmd:description>
                                <gco:CharacterString>Short Name</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                    <!-- LongName -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:code>
                                <gco:CharacterString>Collection-Long-Name</gco:CharacterString>
                            </gmd:code>
                            <gmd:codeSpace>
                                <gco:CharacterString>gov.nasa.esdis.umm.longname</gco:CharacterString>
                            </gmd:codeSpace>
                            <gmd:description>
                                <gco:CharacterString>Long Name</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                    <!-- DOI -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:authority>
                                <gmd:CI_Citation>
                                    <gmd:title/>
                                    <gmd:date/>
                                    <gmd:citedResponsibleParty>
                                        <gmd:CI_ResponsibleParty>
                                            <gmd:organisationName>
                                                <gco:CharacterString>Authority - NASA DAAC or Entity that came up with the DOI Name</gco:CharacterString>
                                            </gmd:organisationName>
                                            <gmd:role>
                                                <gmd:CI_RoleCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#CI_RoleCode\" codeListValue=\"\">authority</gmd:CI_RoleCode>
                                            </gmd:role>
                                        </gmd:CI_ResponsibleParty>
                                    </gmd:citedResponsibleParty>
                                </gmd:CI_Citation>
                            </gmd:authority>
                            <gmd:code>
                                <gco:CharacterString>10.5067/IAGYM8Q26QRE</gco:CharacterString>
                            </gmd:code>
                            <gmd:codeSpace>
                                <gco:CharacterString>gov.nasa.esdis.umm.doi</gco:CharacterString>
                            </gmd:codeSpace>
                            <gmd:description>
                                <gco:CharacterString>A Digital Object Identifier (DOI)</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                </gmd:CI_Citation>
            </gmd:citation>
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
        </gmd:MD_DataIdentification>
    </gmd:identificationInfo>
    </gmi:MI_Metadata>")

(def base-valid-example-iso-mends-doi-xml2
  "<gmi:MI_Metadata xmlns:eos=\"http://earthdata.nasa.gov/schema/eos\"
    xmlns:gco=\"http://www.isotc211.org/2005/gco\"
    xmlns:gmd=\"http://www.isotc211.org/2005/gmd\"
    xmlns:gmi=\"http://www.isotc211.org/2005/gmi\"
    xmlns:gml=\"http://www.opengis.net/gml/3.2\"
    xmlns:gmx=\"http://www.isotc211.org/2005/gmx\"
    xmlns:gsr=\"http://www.isotc211.org/2005/gsr\"
    xmlns:gss=\"http://www.isotc211.org/2005/gss\"
    xmlns:gts=\"http://www.isotc211.org/2005/gts\"
    xmlns:srv=\"http://www.isotc211.org/2005/srv\"
    xmlns:xlink=\"http://www.w3.org/1999/xlink\"
    xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"
    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
    xsi:schemaLocation=\"http://earthdata.nasa.gov/schema/eos https://cdn.earthdata.nasa.gov/iso/eos/1.0/eos.xsd
                        http://www.isotc211.org/2005/gco https://cdn.earthdata.nasa.gov/iso/gco/1.0/gco.xsd
                        http://www.isotc211.org/2005/gmd https://cdn.earthdata.nasa.gov/iso/gmd/1.0/gmd.xsd
                        http://www.isotc211.org/2005/gmi https://cdn.earthdata.nasa.gov/iso/gmi/1.0/gmi.xsd
                        http://www.opengis.net/gml/3.2 https://cdn.earthdata.nasa.gov/iso/gml/1.0/gml.xsd
                        http://www.isotc211.org/2005/gmx https://cdn.earthdata.nasa.gov/iso/gmx/1.0/gmx.xsd
                        http://www.isotc211.org/2005/gsr https://cdn.earthdata.nasa.gov/iso/gsr/1.0/gsr.xsd
                        http://www.isotc211.org/2005/gss https://cdn.earthdata.nasa.gov/iso/gss/1.0/gss.xsd
                        http://www.isotc211.org/2005/gts https://cdn.earthdata.nasa.gov/iso/gts/1.0/gts.xsd
                        http://www.isotc211.org/2005/srv https://cdn.earthdata.nasa.gov/iso/srv/1.0/srv.xsd\">
    <gmd:identificationInfo>
        <gmd:MD_DataIdentification>
            <gmd:citation>
                <gmd:CI_Citation>
                    <!-- ShortName -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:code>
                                <gco:CharacterString>Collection-Short-Name</gco:CharacterString>
                            </gmd:code>
                            <gmd:codeSpace>
                                <gco:CharacterString>gov.nasa.esdis.umm.shortname</gco:CharacterString>
                            </gmd:codeSpace>
                            <gmd:description>
                                <gco:CharacterString>Short Name</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                    <!-- LongName -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:code>
                                <gco:CharacterString>Collection-Long-Name</gco:CharacterString>
                            </gmd:code>
                            <gmd:codeSpace>
                                <gco:CharacterString>gov.nasa.esdis.umm.longname</gco:CharacterString>
                            </gmd:codeSpace>
                            <gmd:description>
                                <gco:CharacterString>Long Name</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                    <!-- DOI -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:authority>
                                <gmd:CI_Citation>
                                    <gmd:title/>
                                    <gmd:date/>
                                    <gmd:citedResponsibleParty>
                                        <gmd:CI_ResponsibleParty>
                                            <gmd:organisationName>
                                                <gco:CharacterString>Authority - NASA DAAC or Entity that came up with the DOI Name</gco:CharacterString>
                                            </gmd:organisationName>
                                            <gmd:role>
                                                <gmd:CI_RoleCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#CI_RoleCode\" codeListValue=\"\">authority</gmd:CI_RoleCode>
                                            </gmd:role>
                                        </gmd:CI_ResponsibleParty>
                                    </gmd:citedResponsibleParty>
                                </gmd:CI_Citation>
                            </gmd:authority>
                            <gmd:code>
                                <gco:CharacterString>10.5067/IAGYM8Q26QRE</gco:CharacterString>
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
                            <gmd:authority>
                                <gmd:CI_Citation>
                                    <gmd:title/>
                                    <gmd:date/>
                                    <gmd:citedResponsibleParty>
                                        <gmd:CI_ResponsibleParty>
                                            <gmd:organisationName>
                                                <gco:CharacterString>Authority2 - NASA DAAC or Entity that came up with the DOI Name</gco:CharacterString>
                                            </gmd:organisationName>
                                            <gmd:role>
                                                <gmd:CI_RoleCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#CI_RoleCode\" codeListValue=\"authority\">authority</gmd:CI_RoleCode>
                                            </gmd:role>
                                        </gmd:CI_ResponsibleParty>
                                    </gmd:citedResponsibleParty>
                                </gmd:CI_Citation>
                            </gmd:authority>
                            <gmd:code>
                                <gco:CharacterString>10.5067/IAGYM8Q26QRE2</gco:CharacterString>
                            </gmd:code>
                            <gmd:codeSpace>
                                <gco:CharacterString>gov.nasa.esdis.umm.doi</gco:CharacterString>
                            </gmd:codeSpace>
                            <gmd:description>
                                <gco:CharacterString>A Digital Object Identifier (DOI)</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                </gmd:CI_Citation>
            </gmd:citation>
        </gmd:MD_DataIdentification>
    </gmd:identificationInfo>
    </gmi:MI_Metadata>")

(def base-valid-example-iso-mends-doi-codespace-only-no-description-doi-xml
  "<gmi:MI_Metadata xmlns:eos=\"http://earthdata.nasa.gov/schema/eos\"
    xmlns:gco=\"http://www.isotc211.org/2005/gco\"
    xmlns:gmd=\"http://www.isotc211.org/2005/gmd\"
    xmlns:gmi=\"http://www.isotc211.org/2005/gmi\"
    xmlns:gml=\"http://www.opengis.net/gml/3.2\"
    xmlns:gmx=\"http://www.isotc211.org/2005/gmx\"
    xmlns:gsr=\"http://www.isotc211.org/2005/gsr\"
    xmlns:gss=\"http://www.isotc211.org/2005/gss\"
    xmlns:gts=\"http://www.isotc211.org/2005/gts\"
    xmlns:srv=\"http://www.isotc211.org/2005/srv\"
    xmlns:xlink=\"http://www.w3.org/1999/xlink\"
    xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"
    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
    xsi:schemaLocation=\"http://earthdata.nasa.gov/schema/eos https://cdn.earthdata.nasa.gov/iso/eos/1.0/eos.xsd
                        http://www.isotc211.org/2005/gco https://cdn.earthdata.nasa.gov/iso/gco/1.0/gco.xsd
                        http://www.isotc211.org/2005/gmd https://cdn.earthdata.nasa.gov/iso/gmd/1.0/gmd.xsd
                        http://www.isotc211.org/2005/gmi https://cdn.earthdata.nasa.gov/iso/gmi/1.0/gmi.xsd
                        http://www.opengis.net/gml/3.2 https://cdn.earthdata.nasa.gov/iso/gml/1.0/gml.xsd
                        http://www.isotc211.org/2005/gmx https://cdn.earthdata.nasa.gov/iso/gmx/1.0/gmx.xsd
                        http://www.isotc211.org/2005/gsr https://cdn.earthdata.nasa.gov/iso/gsr/1.0/gsr.xsd
                        http://www.isotc211.org/2005/gss https://cdn.earthdata.nasa.gov/iso/gss/1.0/gss.xsd
                        http://www.isotc211.org/2005/gts https://cdn.earthdata.nasa.gov/iso/gts/1.0/gts.xsd
                        http://www.isotc211.org/2005/srv https://cdn.earthdata.nasa.gov/iso/srv/1.0/srv.xsd\">
    <gmd:identificationInfo>
        <gmd:MD_DataIdentification>
            <gmd:citation>
                <gmd:CI_Citation>
                    <!-- ShortName -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:code>
                                <gco:CharacterString>Collection-Short-Name</gco:CharacterString>
                            </gmd:code>
                            <gmd:codeSpace>
                                <gco:CharacterString>gov.nasa.esdis.umm.shortname</gco:CharacterString>
                            </gmd:codeSpace>
                            <gmd:description>
                                <gco:CharacterString>Short Name</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                    <!-- LongName -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:code>
                                <gco:CharacterString>Collection-Long-Name</gco:CharacterString>
                            </gmd:code>
                            <gmd:codeSpace>
                                <gco:CharacterString>gov.nasa.esdis.umm.longname</gco:CharacterString>
                            </gmd:codeSpace>
                            <gmd:description>
                                <gco:CharacterString>Long Name</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                    <!-- DOI -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:authority>
                                <gmd:CI_Citation>
                                    <gmd:title/>
                                    <gmd:date/>
                                    <gmd:citedResponsibleParty>
                                        <gmd:CI_ResponsibleParty>
                                            <gmd:organisationName>
                                                <gco:CharacterString>Authority - NASA DAAC or Entity that came up with the DOI Name</gco:CharacterString>
                                            </gmd:organisationName>
                                            <gmd:role>
                                                <gmd:CI_RoleCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#CI_RoleCode\" codeListValue=\"\">authority</gmd:CI_RoleCode>
                                            </gmd:role>
                                        </gmd:CI_ResponsibleParty>
                                    </gmd:citedResponsibleParty>
                                </gmd:CI_Citation>
                            </gmd:authority>
                            <gmd:code>
                                <gco:CharacterString>10.5067/IAGYM8Q26QRE</gco:CharacterString>
                            </gmd:code>
                            <gmd:codeSpace>
                                <gco:CharacterString>gov.nasa.esdis.umm.doi</gco:CharacterString>
                            </gmd:codeSpace>
                            <gmd:description>
                                <gco:CharacterString>A Digital Object Identifier</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                </gmd:CI_Citation>
            </gmd:citation>
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
                    <gmd:code gco:nilReason=\"inapplicable\"/>
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
        </gmd:MD_DataIdentification>
    </gmd:identificationInfo>
    </gmi:MI_Metadata>")

(def base-valid-example-iso-mends-description-only-doi-xml
  "<gmi:MI_Metadata xmlns:eos=\"http://earthdata.nasa.gov/schema/eos\"
    xmlns:gco=\"http://www.isotc211.org/2005/gco\"
    xmlns:gmd=\"http://www.isotc211.org/2005/gmd\"
    xmlns:gmi=\"http://www.isotc211.org/2005/gmi\"
    xmlns:gml=\"http://www.opengis.net/gml/3.2\"
    xmlns:gmx=\"http://www.isotc211.org/2005/gmx\"
    xmlns:gsr=\"http://www.isotc211.org/2005/gsr\"
    xmlns:gss=\"http://www.isotc211.org/2005/gss\"
    xmlns:gts=\"http://www.isotc211.org/2005/gts\"
    xmlns:srv=\"http://www.isotc211.org/2005/srv\"
    xmlns:xlink=\"http://www.w3.org/1999/xlink\"
    xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"
    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
    xsi:schemaLocation=\"http://earthdata.nasa.gov/schema/eos https://cdn.earthdata.nasa.gov/iso/eos/1.0/eos.xsd
                        http://www.isotc211.org/2005/gco https://cdn.earthdata.nasa.gov/iso/gco/1.0/gco.xsd
                        http://www.isotc211.org/2005/gmd https://cdn.earthdata.nasa.gov/iso/gmd/1.0/gmd.xsd
                        http://www.isotc211.org/2005/gmi https://cdn.earthdata.nasa.gov/iso/gmi/1.0/gmi.xsd
                        http://www.opengis.net/gml/3.2 https://cdn.earthdata.nasa.gov/iso/gml/1.0/gml.xsd
                        http://www.isotc211.org/2005/gmx https://cdn.earthdata.nasa.gov/iso/gmx/1.0/gmx.xsd
                        http://www.isotc211.org/2005/gsr https://cdn.earthdata.nasa.gov/iso/gsr/1.0/gsr.xsd
                        http://www.isotc211.org/2005/gss https://cdn.earthdata.nasa.gov/iso/gss/1.0/gss.xsd
                        http://www.isotc211.org/2005/gts https://cdn.earthdata.nasa.gov/iso/gts/1.0/gts.xsd
                        http://www.isotc211.org/2005/srv https://cdn.earthdata.nasa.gov/iso/srv/1.0/srv.xsd\">
    <gmd:identificationInfo>
        <gmd:MD_DataIdentification>
            <gmd:citation>
                <gmd:CI_Citation>
                    <!-- ShortName -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:code>
                                <gco:CharacterString>Collection-Short-Name</gco:CharacterString>
                            </gmd:code>
                            <gmd:codeSpace>
                                <gco:CharacterString>gov.nasa.esdis.umm.shortname</gco:CharacterString>
                            </gmd:codeSpace>
                            <gmd:description>
                                <gco:CharacterString>Short Name</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                    <!-- LongName -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:code>
                                <gco:CharacterString>Collection-Long-Name</gco:CharacterString>
                            </gmd:code>
                            <gmd:codeSpace>
                                <gco:CharacterString>gov.nasa.esdis.umm.longname</gco:CharacterString>
                            </gmd:codeSpace>
                            <gmd:description>
                                <gco:CharacterString>Long Name</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                    <!-- DOI -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:authority>
                                <gmd:CI_Citation>
                                    <gmd:title/>
                                    <gmd:date/>
                                    <gmd:citedResponsibleParty>
                                        <gmd:CI_ResponsibleParty>
                                            <gmd:organisationName>
                                                <gco:CharacterString>Authority - NASA DAAC or Entity that came up with the DOI Name</gco:CharacterString>
                                            </gmd:organisationName>
                                            <gmd:role>
                                                <gmd:CI_RoleCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#CI_RoleCode\" codeListValue=\"\">authority</gmd:CI_RoleCode>
                                            </gmd:role>
                                        </gmd:CI_ResponsibleParty>
                                    </gmd:citedResponsibleParty>
                                </gmd:CI_Citation>
                            </gmd:authority>
                            <gmd:code>
                                <gco:CharacterString>10.5067/IAGYM8Q26QRE</gco:CharacterString>
                            </gmd:code>
                            <gmd:description>
                                <gco:CharacterString>A Digital Object Identifier (DOI)</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                </gmd:CI_Citation>
            </gmd:citation>
        </gmd:MD_DataIdentification>
    </gmd:identificationInfo>
    </gmi:MI_Metadata>")

(def base-valid-example-iso-mends-anchor-doi-xml
  "<gmi:MI_Metadata xmlns:eos=\"http://earthdata.nasa.gov/schema/eos\"
    xmlns:gco=\"http://www.isotc211.org/2005/gco\"
    xmlns:gmd=\"http://www.isotc211.org/2005/gmd\"
    xmlns:gmi=\"http://www.isotc211.org/2005/gmi\"
    xmlns:gml=\"http://www.opengis.net/gml/3.2\"
    xmlns:gmx=\"http://www.isotc211.org/2005/gmx\"
    xmlns:gsr=\"http://www.isotc211.org/2005/gsr\"
    xmlns:gss=\"http://www.isotc211.org/2005/gss\"
    xmlns:gts=\"http://www.isotc211.org/2005/gts\"
    xmlns:srv=\"http://www.isotc211.org/2005/srv\"
    xmlns:xlink=\"http://www.w3.org/1999/xlink\"
    xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"
    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
    xsi:schemaLocation=\"http://earthdata.nasa.gov/schema/eos https://cdn.earthdata.nasa.gov/iso/eos/1.0/eos.xsd
                        http://www.isotc211.org/2005/gco https://cdn.earthdata.nasa.gov/iso/gco/1.0/gco.xsd
                        http://www.isotc211.org/2005/gmd https://cdn.earthdata.nasa.gov/iso/gmd/1.0/gmd.xsd
                        http://www.isotc211.org/2005/gmi https://cdn.earthdata.nasa.gov/iso/gmi/1.0/gmi.xsd
                        http://www.opengis.net/gml/3.2 https://cdn.earthdata.nasa.gov/iso/gml/1.0/gml.xsd
                        http://www.isotc211.org/2005/gmx https://cdn.earthdata.nasa.gov/iso/gmx/1.0/gmx.xsd
                        http://www.isotc211.org/2005/gsr https://cdn.earthdata.nasa.gov/iso/gsr/1.0/gsr.xsd
                        http://www.isotc211.org/2005/gss https://cdn.earthdata.nasa.gov/iso/gss/1.0/gss.xsd
                        http://www.isotc211.org/2005/gts https://cdn.earthdata.nasa.gov/iso/gts/1.0/gts.xsd
                        http://www.isotc211.org/2005/srv https://cdn.earthdata.nasa.gov/iso/srv/1.0/srv.xsd\">
    <gmd:identificationInfo>
        <gmd:MD_DataIdentification>
            <gmd:citation>
                <gmd:CI_Citation>
                    <!-- ShortName -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:code>
                                <gco:CharacterString>Collection-Short-Name</gco:CharacterString>
                            </gmd:code>
                            <gmd:codeSpace>
                                <gco:CharacterString>gov.nasa.esdis.umm.shortname</gco:CharacterString>
                            </gmd:codeSpace>
                            <gmd:description>
                                <gco:CharacterString>Short Name</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                    <!-- LongName -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:code>
                                <gco:CharacterString>Collection-Long-Name</gco:CharacterString>
                            </gmd:code>
                            <gmd:codeSpace>
                                <gco:CharacterString>gov.nasa.esdis.umm.longname</gco:CharacterString>
                            </gmd:codeSpace>
                            <gmd:description>
                                <gco:CharacterString>Long Name</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                    <!-- DOI -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:authority>
                                <gmd:CI_Citation>
                                    <gmd:title/>
                                    <gmd:date/>
                                    <gmd:citedResponsibleParty>
                                        <gmd:CI_ResponsibleParty>
                                            <gmd:organisationName>
                                                <gco:CharacterString>Authority - NASA DAAC or Entity that came up with the DOI Name</gco:CharacterString>
                                            </gmd:organisationName>
                                            <gmd:role>
                                                <gmd:CI_RoleCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#CI_RoleCode\" codeListValue=\"\">authority</gmd:CI_RoleCode>
                                            </gmd:role>
                                        </gmd:CI_ResponsibleParty>
                                    </gmd:citedResponsibleParty>
                                </gmd:CI_Citation>
                            </gmd:authority>
                            <gmd:code>
                                <gmx:Anchor>10.5067/IAGYM8Q26QRE</gmx:Anchor>
                            </gmd:code>
                            <gmd:codeSpace>
                                <gco:CharacterString>gov.nasa.esdis.umm.doi</gco:CharacterString>
                            </gmd:codeSpace>
                            <gmd:description>
                                <gco:CharacterString>A Digital Object Identifier (DOI)</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                </gmd:CI_Citation>
            </gmd:citation>
        </gmd:MD_DataIdentification>
    </gmd:identificationInfo>
    </gmi:MI_Metadata>")

(def base-valid-example-iso-mends-noaa-anchor-doi-xml
  "<gmi:MI_Metadata xmlns:eos=\"http://earthdata.nasa.gov/schema/eos\"
    xmlns:gco=\"http://www.isotc211.org/2005/gco\"
    xmlns:gmd=\"http://www.isotc211.org/2005/gmd\"
    xmlns:gmi=\"http://www.isotc211.org/2005/gmi\"
    xmlns:gml=\"http://www.opengis.net/gml/3.2\"
    xmlns:gmx=\"http://www.isotc211.org/2005/gmx\"
    xmlns:gsr=\"http://www.isotc211.org/2005/gsr\"
    xmlns:gss=\"http://www.isotc211.org/2005/gss\"
    xmlns:gts=\"http://www.isotc211.org/2005/gts\"
    xmlns:srv=\"http://www.isotc211.org/2005/srv\"
    xmlns:xlink=\"http://www.w3.org/1999/xlink\"
    xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"
    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
    xsi:schemaLocation=\"http://earthdata.nasa.gov/schema/eos https://cdn.earthdata.nasa.gov/iso/eos/1.0/eos.xsd
                        http://www.isotc211.org/2005/gco https://cdn.earthdata.nasa.gov/iso/gco/1.0/gco.xsd
                        http://www.isotc211.org/2005/gmd https://cdn.earthdata.nasa.gov/iso/gmd/1.0/gmd.xsd
                        http://www.isotc211.org/2005/gmi https://cdn.earthdata.nasa.gov/iso/gmi/1.0/gmi.xsd
                        http://www.opengis.net/gml/3.2 https://cdn.earthdata.nasa.gov/iso/gml/1.0/gml.xsd
                        http://www.isotc211.org/2005/gmx https://cdn.earthdata.nasa.gov/iso/gmx/1.0/gmx.xsd
                        http://www.isotc211.org/2005/gsr https://cdn.earthdata.nasa.gov/iso/gsr/1.0/gsr.xsd
                        http://www.isotc211.org/2005/gss https://cdn.earthdata.nasa.gov/iso/gss/1.0/gss.xsd
                        http://www.isotc211.org/2005/gts https://cdn.earthdata.nasa.gov/iso/gts/1.0/gts.xsd
                        http://www.isotc211.org/2005/srv https://cdn.earthdata.nasa.gov/iso/srv/1.0/srv.xsd\">
    <gmd:identificationInfo>
        <gmd:MD_DataIdentification>
            <gmd:citation>
                <gmd:CI_Citation>
                    <!-- ShortName -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:code>
                                <gco:CharacterString>Collection-Short-Name</gco:CharacterString>
                            </gmd:code>
                            <gmd:codeSpace>
                                <gco:CharacterString>gov.nasa.esdis.umm.shortname</gco:CharacterString>
                            </gmd:codeSpace>
                            <gmd:description>
                                <gco:CharacterString>Short Name</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                    <!-- LongName -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:code>
                                <gco:CharacterString>Collection-Long-Name</gco:CharacterString>
                            </gmd:code>
                            <gmd:codeSpace>
                                <gco:CharacterString>gov.nasa.esdis.umm.longname</gco:CharacterString>
                            </gmd:codeSpace>
                            <gmd:description>
                                <gco:CharacterString>Long Name</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                    <!-- some other identifier that uses Anchor -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:code>
                                <gmx:Anchor xlink:href=\"https://someothersite.com\" xlink:title=\"BLAH\" xlink:actuate=\"onRequest\">some other text</gmx:Anchor>
                            </gmd:code>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                    <!-- DOI -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:authority>
                                <gmd:CI_Citation>
                                    <gmd:title/>
                                    <gmd:date/>
                                    <gmd:citedResponsibleParty>
                                        <gmd:CI_ResponsibleParty>
                                            <gmd:organisationName>
                                                <gco:CharacterString>Authority - NASA DAAC or Entity that came up with the DOI Name</gco:CharacterString>
                                            </gmd:organisationName>
                                            <gmd:role>
                                                <gmd:CI_RoleCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#CI_RoleCode\" codeListValue=\"\">authority</gmd:CI_RoleCode>
                                            </gmd:role>
                                        </gmd:CI_ResponsibleParty>
                                    </gmd:citedResponsibleParty>
                                </gmd:CI_Citation>
                            </gmd:authority>
                            <gmd:code>
                                <gmx:Anchor xlink:href=\"https://doi.org/10.7289/V5QR4VBJ\" xlink:title=\"DOI\" xlink:actuate=\"onRequest\">doi:10.7289/V5QR4VBJ</gmx:Anchor>
                            </gmd:code>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                </gmd:CI_Citation>
            </gmd:citation>
        </gmd:MD_DataIdentification>
    </gmd:identificationInfo>
    </gmi:MI_Metadata>")

(def base-valid-example-iso-smap-doi-xml
  "<gmd:DS_Series xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
    xsi:schemaLocation=\"http://www.isotc211.org/2005/gmi http://cdn.earthdata.nasa.gov/iso/schema/1.0/ISO19115-2_EOS.xsd\"
    xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\"
    xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gml=\"http://www.opengis.net/gml/3.2\"
    xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gss=\"http://www.isotc211.org/2005/gss\"
    xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gmi=\"http://www.isotc211.org/2005/gmi\"
    xmlns:gmx=\"http://www.isotc211.org/2005/gmx\" xmlns:srv=\"http://www.isotc211.org/2005/srv\"
    xmlns:eos=\"http://earthdata.nasa.gov/schema/eos\">
    <gmd:composedOf gco:nilReason=\"inapplicable\"/>
    <gmd:seriesMetadata>
      <gmi:MI_Metadata>
        <gmd:identificationInfo>
            <gmd:MD_DataIdentification>
                <gmd:citation>
                    <gmd:CI_Citation>
                        <!-- ShortName -->
                        <gmd:identifier>
                            <gmd:MD_Identifier>
                                <gmd:code>
                                    <gco:CharacterString>Collection-Short-Name</gco:CharacterString>
                                </gmd:code>
                                <gmd:codeSpace>
                                    <gco:CharacterString>gov.nasa.esdis.umm.shortname</gco:CharacterString>
                                </gmd:codeSpace>
                                <gmd:description>
                                    <gco:CharacterString>Short Name</gco:CharacterString>
                                </gmd:description>
                            </gmd:MD_Identifier>
                        </gmd:identifier>
                        <!-- LongName -->
                        <gmd:identifier>
                            <gmd:MD_Identifier>
                                <gmd:code>
                                    <gco:CharacterString>Collection-Long-Name</gco:CharacterString>
                                </gmd:code>
                                <gmd:codeSpace>
                                    <gco:CharacterString>gov.nasa.esdis.umm.longname</gco:CharacterString>
                                </gmd:codeSpace>
                                <gmd:description>
                                    <gco:CharacterString>Long Name</gco:CharacterString>
                                </gmd:description>
                            </gmd:MD_Identifier>
                        </gmd:identifier>
                        <!-- DOI -->
                        <gmd:identifier>
                            <gmd:MD_Identifier>
                                <gmd:authority>
                                    <gmd:CI_Citation>
                                        <gmd:title/>
                                        <gmd:date/>
                                        <gmd:citedResponsibleParty>
                                            <gmd:CI_ResponsibleParty>
                                                <gmd:organisationName>
                                                    <gco:CharacterString>Authority - NASA DAAC or Entity that came up with the DOI Name</gco:CharacterString>
                                                </gmd:organisationName>
                                                <gmd:role>
                                                    <gmd:CI_RoleCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#CI_RoleCode\" codeListValue=\"\">authority</gmd:CI_RoleCode>
                                                </gmd:role>
                                            </gmd:CI_ResponsibleParty>
                                        </gmd:citedResponsibleParty>
                                    </gmd:CI_Citation>
                                </gmd:authority>
                                <gmd:code>
                                    <gco:CharacterString>10.5067/IAGYM8Q26QRE</gco:CharacterString>
                                </gmd:code>
                                <gmd:codeSpace>
                                    <gco:CharacterString>gov.nasa.esdis.umm.doi</gco:CharacterString>
                                </gmd:codeSpace>
                                <gmd:description>
                                    <gco:CharacterString>A Digital Object Identifier (DOI)</gco:CharacterString>
                                </gmd:description>
                            </gmd:MD_Identifier>
                        </gmd:identifier>
                    </gmd:CI_Citation>
                </gmd:citation>
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
            </gmd:MD_DataIdentification>
        </gmd:identificationInfo>
      </gmi:MI_Metadata>
    </gmd:seriesMetadata>
  </gmd:DS_Series>")

(def iso-mends-doi-not-applicable-xml
  "<gmi:MI_Metadata xmlns:eos=\"http://earthdata.nasa.gov/schema/eos\"
    xmlns:gco=\"http://www.isotc211.org/2005/gco\"
    xmlns:gmd=\"http://www.isotc211.org/2005/gmd\"
    xmlns:gmi=\"http://www.isotc211.org/2005/gmi\"
    xmlns:gml=\"http://www.opengis.net/gml/3.2\"
    xmlns:gmx=\"http://www.isotc211.org/2005/gmx\"
    xmlns:gsr=\"http://www.isotc211.org/2005/gsr\"
    xmlns:gss=\"http://www.isotc211.org/2005/gss\"
    xmlns:gts=\"http://www.isotc211.org/2005/gts\"
    xmlns:srv=\"http://www.isotc211.org/2005/srv\"
    xmlns:xlink=\"http://www.w3.org/1999/xlink\"
    xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"
    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
    xsi:schemaLocation=\"http://earthdata.nasa.gov/schema/eos https://cdn.earthdata.nasa.gov/iso/eos/1.0/eos.xsd
                        http://www.isotc211.org/2005/gco https://cdn.earthdata.nasa.gov/iso/gco/1.0/gco.xsd
                        http://www.isotc211.org/2005/gmd https://cdn.earthdata.nasa.gov/iso/gmd/1.0/gmd.xsd
                        http://www.isotc211.org/2005/gmi https://cdn.earthdata.nasa.gov/iso/gmi/1.0/gmi.xsd
                        http://www.opengis.net/gml/3.2 https://cdn.earthdata.nasa.gov/iso/gml/1.0/gml.xsd
                        http://www.isotc211.org/2005/gmx https://cdn.earthdata.nasa.gov/iso/gmx/1.0/gmx.xsd
                        http://www.isotc211.org/2005/gsr https://cdn.earthdata.nasa.gov/iso/gsr/1.0/gsr.xsd
                        http://www.isotc211.org/2005/gss https://cdn.earthdata.nasa.gov/iso/gss/1.0/gss.xsd
                        http://www.isotc211.org/2005/gts https://cdn.earthdata.nasa.gov/iso/gts/1.0/gts.xsd
                        http://www.isotc211.org/2005/srv https://cdn.earthdata.nasa.gov/iso/srv/1.0/srv.xsd\">
    <gmd:identificationInfo>
        <gmd:MD_DataIdentification>
            <gmd:citation>
                <gmd:CI_Citation>
                    <!-- ShortName -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:code>
                                <gco:CharacterString>Collection-Short-Name</gco:CharacterString>
                            </gmd:code>
                            <gmd:codeSpace>
                                <gco:CharacterString>gov.nasa.esdis.umm.shortname</gco:CharacterString>
                            </gmd:codeSpace>
                            <gmd:description>
                                <gco:CharacterString>Short Name</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                    <!-- DOI -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:code gco:nilReason=\"inapplicable\"/>
                            <gmd:codeSpace>
                                <gco:CharacterString>gov.nasa.esdis.umm.doi</gco:CharacterString>
                            </gmd:codeSpace>
                            <gmd:description>
                                <gco:CharacterString>Explanation: This record doesn't need a DOI.</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                </gmd:CI_Citation>
            </gmd:citation>
        </gmd:MD_DataIdentification>
    </gmd:identificationInfo>
    </gmi:MI_Metadata>")

(def iso-mends-doi-unknown-xml
  "<gmi:MI_Metadata xmlns:eos=\"http://earthdata.nasa.gov/schema/eos\"
    xmlns:gco=\"http://www.isotc211.org/2005/gco\"
    xmlns:gmd=\"http://www.isotc211.org/2005/gmd\"
    xmlns:gmi=\"http://www.isotc211.org/2005/gmi\"
    xmlns:gml=\"http://www.opengis.net/gml/3.2\"
    xmlns:gmx=\"http://www.isotc211.org/2005/gmx\"
    xmlns:gsr=\"http://www.isotc211.org/2005/gsr\"
    xmlns:gss=\"http://www.isotc211.org/2005/gss\"
    xmlns:gts=\"http://www.isotc211.org/2005/gts\"
    xmlns:srv=\"http://www.isotc211.org/2005/srv\"
    xmlns:xlink=\"http://www.w3.org/1999/xlink\"
    xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"
    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
    xsi:schemaLocation=\"http://earthdata.nasa.gov/schema/eos https://cdn.earthdata.nasa.gov/iso/eos/1.0/eos.xsd
                        http://www.isotc211.org/2005/gco https://cdn.earthdata.nasa.gov/iso/gco/1.0/gco.xsd
                        http://www.isotc211.org/2005/gmd https://cdn.earthdata.nasa.gov/iso/gmd/1.0/gmd.xsd
                        http://www.isotc211.org/2005/gmi https://cdn.earthdata.nasa.gov/iso/gmi/1.0/gmi.xsd
                        http://www.opengis.net/gml/3.2 https://cdn.earthdata.nasa.gov/iso/gml/1.0/gml.xsd
                        http://www.isotc211.org/2005/gmx https://cdn.earthdata.nasa.gov/iso/gmx/1.0/gmx.xsd
                        http://www.isotc211.org/2005/gsr https://cdn.earthdata.nasa.gov/iso/gsr/1.0/gsr.xsd
                        http://www.isotc211.org/2005/gss https://cdn.earthdata.nasa.gov/iso/gss/1.0/gss.xsd
                        http://www.isotc211.org/2005/gts https://cdn.earthdata.nasa.gov/iso/gts/1.0/gts.xsd
                        http://www.isotc211.org/2005/srv https://cdn.earthdata.nasa.gov/iso/srv/1.0/srv.xsd\">
    <gmd:identificationInfo>
        <gmd:MD_DataIdentification>
            <gmd:citation>
                <gmd:CI_Citation>
                    <!-- ShortName -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:code>
                                <gco:CharacterString>Collection-Short-Name</gco:CharacterString>
                            </gmd:code>
                            <gmd:codeSpace>
                                <gco:CharacterString>gov.nasa.esdis.umm.shortname</gco:CharacterString>
                            </gmd:codeSpace>
                            <gmd:description>
                                <gco:CharacterString>Short Name</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                    <!-- DOI -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:code gco:nilReason=\"unknown\"/>
                            <gmd:codeSpace>
                                <gco:CharacterString>gov.nasa.esdis.umm.doi</gco:CharacterString>
                            </gmd:codeSpace>
                            <gmd:description>
                                <gco:CharacterString>Explanation: It is unknown if this record has a DOI.</gco:CharacterString>
                            </gmd:description>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                </gmd:CI_Citation>
            </gmd:citation>
        </gmd:MD_DataIdentification>
    </gmd:identificationInfo>
    </gmi:MI_Metadata>")

(def iso-mends-no-codespace-nor-description
  "<gmi:MI_Metadata xmlns:eos=\"http://earthdata.nasa.gov/schema/eos\"
    xmlns:gco=\"http://www.isotc211.org/2005/gco\"
    xmlns:gmd=\"http://www.isotc211.org/2005/gmd\"
    xmlns:gmi=\"http://www.isotc211.org/2005/gmi\"
    xmlns:gml=\"http://www.opengis.net/gml/3.2\"
    xmlns:gmx=\"http://www.isotc211.org/2005/gmx\"
    xmlns:gsr=\"http://www.isotc211.org/2005/gsr\"
    xmlns:gss=\"http://www.isotc211.org/2005/gss\"
    xmlns:gts=\"http://www.isotc211.org/2005/gts\"
    xmlns:srv=\"http://www.isotc211.org/2005/srv\"
    xmlns:xlink=\"http://www.w3.org/1999/xlink\"
    xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"
    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
    xsi:schemaLocation=\"http://earthdata.nasa.gov/schema/eos https://cdn.earthdata.nasa.gov/iso/eos/1.0/eos.xsd
                        http://www.isotc211.org/2005/gco https://cdn.earthdata.nasa.gov/iso/gco/1.0/gco.xsd
                        http://www.isotc211.org/2005/gmd https://cdn.earthdata.nasa.gov/iso/gmd/1.0/gmd.xsd
                        http://www.isotc211.org/2005/gmi https://cdn.earthdata.nasa.gov/iso/gmi/1.0/gmi.xsd
                        http://www.opengis.net/gml/3.2 https://cdn.earthdata.nasa.gov/iso/gml/1.0/gml.xsd
                        http://www.isotc211.org/2005/gmx https://cdn.earthdata.nasa.gov/iso/gmx/1.0/gmx.xsd
                        http://www.isotc211.org/2005/gsr https://cdn.earthdata.nasa.gov/iso/gsr/1.0/gsr.xsd
                        http://www.isotc211.org/2005/gss https://cdn.earthdata.nasa.gov/iso/gss/1.0/gss.xsd
                        http://www.isotc211.org/2005/gts https://cdn.earthdata.nasa.gov/iso/gts/1.0/gts.xsd
                        http://www.isotc211.org/2005/srv https://cdn.earthdata.nasa.gov/iso/srv/1.0/srv.xsd\">
    <gmd:identificationInfo>
        <gmd:MD_DataIdentification>
            <gmd:citation>
                <gmd:CI_Citation>
                    <!-- ShortName -->
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:code>
                                <gco:CharacterString>Collection-Short-Name</gco:CharacterString>
                            </gmd:code>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                </gmd:CI_Citation>
            </gmd:citation>
        </gmd:MD_DataIdentification>
    </gmd:identificationInfo>
    </gmi:MI_Metadata>")

(def expected-iso-valid-doi
  "This is the normal expected value for most of the tests."
  {:DOI "10.5067/IAGYM8Q26QRE",
   :Authority "Authority - NASA DAAC or Entity that came up with the DOI Name"})

(def expected-iso-valid-doi-noaa
  "This is the normal expected value for most of the tests."
  {:DOI "doi:10.7289/V5QR4VBJ",
   :Authority "Authority - NASA DAAC or Entity that came up with the DOI Name"})

(def expected-not-applicable-iso-valid-doi
  "This is the expected value when the DOI code is inapplicable."
  {:Explanation "This record doesn't need a DOI.",
   :MissingReason "Not Applicable"})

(def expected-unknown-doi
  "This is the expected value when the codespace or description doesnt exist and there are no DOIs."
  {:Explanation "It is unknown if this record has a DOI.",
   :MissingReason "Unknown"})

(deftest iso-mends-valid-doi-test
  (testing "test the main test case with ISO MENDS."
    (is (= expected-iso-valid-doi
           (doi/parse-doi base-valid-example-iso-mends-doi-xml mends/citation-base-xpath))))

  (testing "test the main test case with ISO SMAP"
    (is (= expected-iso-valid-doi
           (doi/parse-doi base-valid-example-iso-smap-doi-xml smap/citation-base-xpath))))

  (testing "test the main test case with 2 DOI's with ISO MENDS - only the first should come back."
    (is (= expected-iso-valid-doi
           (doi/parse-doi base-valid-example-iso-mends-doi-xml2 mends/citation-base-xpath))))

  (testing "test the main test case with only codespace defined as a DOI and the description does not contain DOI."
    (is (= expected-iso-valid-doi
           (doi/parse-doi base-valid-example-iso-mends-doi-codespace-only-no-description-doi-xml mends/citation-base-xpath))))

  (testing "test the main test case with only description defined as a DOI."
    (is (= expected-iso-valid-doi
           (doi/parse-doi base-valid-example-iso-mends-description-only-doi-xml mends/citation-base-xpath))))

  (testing "test the main test case with only the DOI defined by the Anchor and not the CharacterString."
    (is (= expected-iso-valid-doi
           (doi/parse-doi base-valid-example-iso-mends-anchor-doi-xml mends/citation-base-xpath))))

  (testing "test the main test case with only the DOI defined by the Anchor and not the CharacterString."
    (is (= expected-iso-valid-doi-noaa
           (doi/parse-doi base-valid-example-iso-mends-noaa-anchor-doi-xml mends/citation-base-xpath))))

  (testing "test when the record's DOI is missing on purpose."
    (is (= expected-not-applicable-iso-valid-doi
           (doi/parse-doi iso-mends-doi-not-applicable-xml mends/citation-base-xpath))))

  (testing "test when it is stated that the records DOI is unknown."
    (is (= expected-unknown-doi
           (doi/parse-doi iso-mends-doi-unknown-xml mends/citation-base-xpath))))

  (testing "test when no codespace or description exists - there are no DOIs."
    (is (= expected-unknown-doi
           (doi/parse-doi iso-mends-no-codespace-nor-description mends/citation-base-xpath)))))

(deftest associated-doi-tests
  (testing "test the associated DOI main test case with ISO MENDS."
    (is (= [{:DOI "10.5678/AssociatedDOI1"
             :Title "DOI 1 landing page title"
             :Authority "https://doi.org"}
            {:DOI "10.5678/AssociatedDOI2"
             :Title "DOI 2 landing page title"
             :Authority "https://doi.org"}]
           (doi/parse-associated-dois base-valid-example-iso-mends-doi-xml mends/associated-doi-xpath))))

  (testing "test the associated DOI main test case with ISO SMAP."
    (is (= [{:DOI "10.5678/AssociatedDOI1"
             :Title "DOI 1 landing page title"
             :Authority "https://doi.org"}
            {:DOI "10.5678/AssociatedDOI2"
             :Title "DOI 2 landing page title"
             :Authority "https://doi.org"}]
           (doi/parse-associated-dois base-valid-example-iso-smap-doi-xml smap/associated-doi-xpath))))

  (testing "test associated DOIs do not exist."
    (is (= nil
           (doi/parse-associated-dois base-valid-example-iso-mends-doi-xml2 mends/associated-doi-xpath))))

  (testing "test when 1 associated DOI is valid and the other doesn't have a DOI."
    (is (= [{:DOI "10.5678/AssociatedDOI1"
             :Title "DOI 1 landing page title"
             :Authority "https://doi.org"}]
           (doi/parse-associated-dois base-valid-example-iso-mends-doi-codespace-only-no-description-doi-xml
                                      mends/associated-doi-xpath)))))
