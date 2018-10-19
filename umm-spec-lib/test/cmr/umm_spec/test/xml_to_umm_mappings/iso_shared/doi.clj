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
                                                <gmd:CI_RoleCode codeList=\"https://cdn.earthdata.nasa.gov/iso/resources/Codelist/gmxCodelists.xml#CI_RoleCode\" codeListValue=\"\">authority</gmd:CI_RoleCode>
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

(def expected-not-applicable-iso-valid-doi
  "This is the expected value when the DOI code is inapplicable."
  {:Explanation "This record doesn't need a DOI.",
   :MissingReason "Not Applicable"})

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

  (testing "test when the record's DOI is missing on purpose."
    (is (= expected-not-applicable-iso-valid-doi
          (doi/parse-doi iso-mends-doi-not-applicable-xml mends/citation-base-xpath))))

  (testing "test when no codespace or description exists there are no DOIs."
    (is (= nil
          (doi/parse-doi iso-mends-no-codespace-nor-description mends/citation-base-xpath)))))
