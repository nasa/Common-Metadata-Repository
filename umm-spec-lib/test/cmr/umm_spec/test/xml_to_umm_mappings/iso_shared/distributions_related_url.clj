(ns cmr.umm-spec.test.xml-to-umm-mappings.iso-shared.distributions-related-url
  (:require
   [clojure.test :refer :all]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.distributions-related-url :as sru]
   [cmr.umm-spec.xml-to-umm-mappings.iso-smap.distributions-related-url :as smap-ru]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.distributions-related-url :as mends-ru]))

(def distribution-related-url-iso-mends-record
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
      <gmd:distributionInfo>
          <gmd:MD_Distribution>
              <gmd:distributionFormat>
                  <gmd:MD_Format>
                      <gmd:name>
                          <gco:CharacterString>Comma-Separated Values (.csv)</gco:CharacterString>
                      </gmd:name>
                      <gmd:version gco:nilReason=\"missing\"/>
                      <gmd:specification>
                          <gco:CharacterString>HTTPS 9.96 GB</gco:CharacterString>
                      </gmd:specification>
                  </gmd:MD_Format>
              </gmd:distributionFormat>
              <gmd:distributionFormat>
                  <gmd:MD_Format>
                      <gmd:name>
                          <gco:CharacterString>XML</gco:CharacterString>
                      </gmd:name>
                      <gmd:version gco:nilReason=\"missing\"/>
                      <gmd:specification>
                          <gco:CharacterString>HTTPS 0.2 GB</gco:CharacterString>
                      </gmd:specification>
                  </gmd:MD_Format>
              </gmd:distributionFormat>
              <gmd:distributor>
                  <gmd:MD_Distributor>
                      <gmd:distributorContact>
                      </gmd:distributorContact>
                      <gmd:distributorTransferOptions>
                          <gmd:MD_DigitalTransferOptions>
                              <gmd:onLine>
                              <gmd:CI_OnlineResource>
                              <gmd:linkage>
                              <gmd:URL>http://nsidc.org/icebridge/portal/</gmd:URL>
                              </gmd:linkage>
                              <gmd:name>
                              <gco:CharacterString>IceBridge Portal</gco:CharacterString>
                              </gmd:name>
                              <gmd:description>
                              <gco:CharacterString>Tool to visualize, search, and download IceBridge data.</gco:CharacterString>
                              </gmd:description>
                              <gmd:function>

                              <gmd:CI_OnLineFunctionCode
                              codeList=\"http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode\" codeListValue=\"function\">download</gmd:CI_OnLineFunctionCode>
                              </gmd:function>
                              </gmd:CI_OnlineResource>
                              </gmd:onLine>
                              <gmd:onLine>
                              <gmd:CI_OnlineResource>
                              <gmd:linkage>
                              <gmd:URL>https://n5eil01u.ecs.nsidc.org/ICEBRIDGE/ILATM2.002/</gmd:URL>
                              </gmd:linkage>
                              <gmd:name>
                              <gco:CharacterString>HTTPS</gco:CharacterString>
                              </gmd:name>
                              <gmd:description>
                              <gco:CharacterString>Direct download via HTTPS protocol.</gco:CharacterString>
                              </gmd:description>
                              <gmd:function>

                              <gmd:CI_OnLineFunctionCode
                              codeList=\"http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode\" codeListValue=\"function\">download</gmd:CI_OnLineFunctionCode>
                              </gmd:function>
                              </gmd:CI_OnlineResource>
                              </gmd:onLine>
                              <gmd:onLine>
                              <gmd:CI_OnlineResource>
                              <gmd:linkage>
                              <gmd:URL>https://search.earthdata.nasa.gov/search?q=ILATM2</gmd:URL>
                              </gmd:linkage>
                              <gmd:name>
                              <gco:CharacterString>Earthdata Search</gco:CharacterString>
                              </gmd:name>
                              <gmd:description>
                              <gco:CharacterString>NASA's newest search and order tool for subsetting, reprojecting, and reformatting data.</gco:CharacterString>
                              </gmd:description>
                              <gmd:function>

                              <gmd:CI_OnLineFunctionCode
                              codeList=\"http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode\" codeListValue=\"function\">download</gmd:CI_OnLineFunctionCode>
                              </gmd:function>
                              </gmd:CI_OnlineResource>
                              </gmd:onLine>
                              <gmd:onLine>
                              <gmd:CI_OnlineResource>
                              <gmd:linkage>
                              <gmd:URL>http://dx.doi.org/10.5067/CPRXXK3F39RV</gmd:URL>
                              </gmd:linkage>
                              <gmd:name>
                              <gco:CharacterString>Documentation</gco:CharacterString>
                              </gmd:name>
                              <gmd:description>
                              <gco:CharacterString>Documentation explaining the data and how it was processed.</gco:CharacterString>
                              </gmd:description>
                              <gmd:function>

                              <gmd:CI_OnLineFunctionCode
                              codeList=\"http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode\" codeListValue=\"function\">information</gmd:CI_OnLineFunctionCode>
                              </gmd:function>
                              </gmd:CI_OnlineResource>
                              </gmd:onLine>
                          </gmd:MD_DigitalTransferOptions>
                      </gmd:distributorTransferOptions>
                      <gmd:distributorTransferOptions>
                          <gmd:MD_DigitalTransferOptions>
                              <gmd:onLine>
                              <gmd:CI_OnlineResource>
                              <gmd:linkage>
                              <gmd:URL>http://nsidc.org/icebridge/portal/2</gmd:URL>
                              </gmd:linkage>
                              <gmd:name>
                              <gco:CharacterString>IceBridge Portal2</gco:CharacterString>
                              </gmd:name>
                              <gmd:description>
                              <gco:CharacterString>Tool to visualize, search, and download IceBridge data 2.</gco:CharacterString>
                              </gmd:description>
                              <gmd:function>
                              <gmd:CI_OnLineFunctionCode
                              codeList=\"http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode\" codeListValue=\"function\">download</gmd:CI_OnLineFunctionCode>
                              </gmd:function>
                              </gmd:CI_OnlineResource>
                              </gmd:onLine>
                          </gmd:MD_DigitalTransferOptions>
                      </gmd:distributorTransferOptions>
                  </gmd:MD_Distributor>
              </gmd:distributor>
          </gmd:MD_Distribution>
      </gmd:distributionInfo>
    </gmi:MI_Metadata>")

(def distribution-related-url-iso-smap-record
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
        <gmd:distributionInfo>
            <gmd:MD_Distribution>
                <gmd:distributionFormat>
                    <gmd:MD_Format>
                        <gmd:name>
                            <gco:CharacterString>Comma-Separated Values (.csv)</gco:CharacterString>
                        </gmd:name>
                        <gmd:version gco:nilReason=\"missing\"/>
                        <gmd:specification>
                            <gco:CharacterString>HTTPS 9.96 GB</gco:CharacterString>
                        </gmd:specification>
                    </gmd:MD_Format>
                </gmd:distributionFormat>
                <gmd:distributionFormat>
                    <gmd:MD_Format>
                        <gmd:name>
                            <gco:CharacterString>XML</gco:CharacterString>
                        </gmd:name>
                        <gmd:version gco:nilReason=\"missing\"/>
                        <gmd:specification>
                            <gco:CharacterString>HTTPS 0.2 GB</gco:CharacterString>
                        </gmd:specification>
                    </gmd:MD_Format>
                </gmd:distributionFormat>
                <gmd:distributor>
                    <gmd:MD_Distributor>
                        <gmd:distributorContact>
                        </gmd:distributorContact>
                        <gmd:distributorTransferOptions>
                            <gmd:MD_DigitalTransferOptions>
                                <gmd:onLine>
                                <gmd:CI_OnlineResource>
                                <gmd:linkage>
                                <gmd:URL>http://nsidc.org/icebridge/portal/</gmd:URL>
                                </gmd:linkage>
                                <gmd:name>
                                <gco:CharacterString>IceBridge Portal</gco:CharacterString>
                                </gmd:name>
                                <gmd:description>
                                <gco:CharacterString>Tool to visualize, search, and download IceBridge data.</gco:CharacterString>
                                </gmd:description>
                                <gmd:function>

                                <gmd:CI_OnLineFunctionCode
                                codeList=\"http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode\" codeListValue=\"function\">download</gmd:CI_OnLineFunctionCode>
                                </gmd:function>
                                </gmd:CI_OnlineResource>
                                </gmd:onLine>
                                <gmd:onLine>
                                <gmd:CI_OnlineResource>
                                <gmd:linkage>
                                <gmd:URL>https://n5eil01u.ecs.nsidc.org/ICEBRIDGE/ILATM2.002/</gmd:URL>
                                </gmd:linkage>
                                <gmd:name>
                                <gco:CharacterString>HTTPS</gco:CharacterString>
                                </gmd:name>
                                <gmd:description>
                                <gco:CharacterString>Direct download via HTTPS protocol.</gco:CharacterString>
                                </gmd:description>
                                <gmd:function>

                                <gmd:CI_OnLineFunctionCode
                                codeList=\"http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode\" codeListValue=\"function\">download</gmd:CI_OnLineFunctionCode>
                                </gmd:function>
                                </gmd:CI_OnlineResource>
                                </gmd:onLine>
                                <gmd:onLine>
                                <gmd:CI_OnlineResource>
                                <gmd:linkage>
                                <gmd:URL>https://search.earthdata.nasa.gov/search?q=ILATM2</gmd:URL>
                                </gmd:linkage>
                                <gmd:name>
                                <gco:CharacterString>Earthdata Search</gco:CharacterString>
                                </gmd:name>
                                <gmd:description>
                                <gco:CharacterString>NASA's newest search and order tool for subsetting, reprojecting, and reformatting data.</gco:CharacterString>
                                </gmd:description>
                                <gmd:function>

                                <gmd:CI_OnLineFunctionCode
                                codeList=\"http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode\" codeListValue=\"function\">download</gmd:CI_OnLineFunctionCode>
                                </gmd:function>
                                </gmd:CI_OnlineResource>
                                </gmd:onLine>
                                <gmd:onLine>
                                <gmd:CI_OnlineResource>
                                <gmd:linkage>
                                <gmd:URL>http://dx.doi.org/10.5067/CPRXXK3F39RV</gmd:URL>
                                </gmd:linkage>
                                <gmd:name>
                                <gco:CharacterString>Documentation</gco:CharacterString>
                                </gmd:name>
                                <gmd:description>
                                <gco:CharacterString>Documentation explaining the data and how it was processed.</gco:CharacterString>
                                </gmd:description>
                                <gmd:function>

                                <gmd:CI_OnLineFunctionCode
                                codeList=\"http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode\" codeListValue=\"function\">information</gmd:CI_OnLineFunctionCode>
                                </gmd:function>
                                </gmd:CI_OnlineResource>
                                </gmd:onLine>
                            </gmd:MD_DigitalTransferOptions>
                        </gmd:distributorTransferOptions>
                        <gmd:distributorTransferOptions>
                            <gmd:MD_DigitalTransferOptions>
                                <gmd:onLine>
                                <gmd:CI_OnlineResource>
                                <gmd:linkage>
                                <gmd:URL>http://nsidc.org/icebridge/portal/2</gmd:URL>
                                </gmd:linkage>
                                <gmd:name>
                                <gco:CharacterString>IceBridge Portal2</gco:CharacterString>
                                </gmd:name>
                                <gmd:description>
                                <gco:CharacterString>Tool to visualize, search, and download IceBridge data 2.</gco:CharacterString>
                                </gmd:description>
                                <gmd:function>
                                <gmd:CI_OnLineFunctionCode
                                codeList=\"http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_OnLineFunctionCode\" codeListValue=\"function\">download</gmd:CI_OnLineFunctionCode>
                                </gmd:function>
                                </gmd:CI_OnlineResource>
                                </gmd:onLine>
                            </gmd:MD_DigitalTransferOptions>
                        </gmd:distributorTransferOptions>
                    </gmd:MD_Distributor>
                </gmd:distributor>
            </gmd:MD_Distribution>
        </gmd:distributionInfo>
      </gmi:MI_Metadata>
    </gmd:seriesMetadata>
  </gmd:DS_Series>")

(def expected-distribution-related-url-record
  "This is the normal expected value for most of the tests."
  '({:URL "http://nsidc.org/icebridge/portal/",
     :URLContentType "DistributionURL",
     :Type "GET DATA",
     :Subtype nil,
     :Description
     "Tool to visualize, search, and download IceBridge data.",
     :GetData
     {:Format "Not provided",
      :Size 0.0,
      :Unit "KB",
      :Fees nil,
      :Checksum nil,
      :MimeType nil}},
    {:URL
     "https://n5eil01u.ecs.nsidc.org/ICEBRIDGE/ILATM2.002/",
     :URLContentType "DistributionURL",
     :Type "GET DATA",
     :Subtype nil,
     :Description "Direct download via HTTPS protocol.",
     :GetData
     {:Format "Not provided",
      :Size 0.0,
      :Unit "KB",
      :Fees nil,
      :Checksum nil,
      :MimeType nil}},
    {:URL "https://search.earthdata.nasa.gov/search?q=ILATM2",
     :URLContentType "DistributionURL",
     :Type "GET DATA",
     :Subtype nil,
     :Description
     "NASA's newest search and order tool for subsetting, reprojecting, and reformatting data.",
     :GetData
     {:Format "Not provided",
      :Size 0.0,
      :Unit "KB",
      :Fees nil,
      :Checksum nil,
      :MimeType nil}},
    {:URL "http://dx.doi.org/10.5067/CPRXXK3F39RV",
     :URLContentType "DistributionURL",
     :Type "GET DATA",
     :Subtype nil,
     :Description
     "Documentation explaining the data and how it was processed.",
     :GetData
     {:Format "Not provided",
      :Size 0.0,
      :Unit "KB",
      :Fees nil,
      :Checksum nil,
      :MimeType nil}},
    {:URL "http://nsidc.org/icebridge/portal/2",
       :URLContentType "DistributionURL",
       :Type "GET DATA",
       :Subtype nil,
       :Description
       "Tool to visualize, search, and download IceBridge data 2.",
       :GetData
       {:Format "Not provided",
        :Size 0.0,
        :Unit "KB",
        :Fees nil,
        :Checksum nil,
        :MimeType nil}}))


(deftest iso-mends-multiple-distributed-related-url-test
  (testing "The the software that checks multiple related urls for multiple distributors,
            multiple transferOptions, and muliple online resources."
    (let [sanitize? true]
      (is (= expected-distribution-related-url-record
            (sru/parse-online-urls distribution-related-url-iso-mends-record sanitize? mends-ru/service-url-path mends-ru/distributor-xpaths-map))))))

(deftest iso-smap-multiple-distributed-related-url-test
  (testing "The the software that checks multiple related urls for multiple distributors,
            multiple transferOptions, and muliple online resources."
    (let [sanitize? true]
      (is (= expected-distribution-related-url-record
            (sru/parse-online-urls distribution-related-url-iso-smap-record sanitize? smap-ru/service-url-path smap-ru/distributor-xpaths-map))))))
