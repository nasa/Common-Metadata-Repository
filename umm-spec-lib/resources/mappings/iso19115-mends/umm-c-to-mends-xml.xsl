<xsl:stylesheet
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0"
  xmlns:gmd="http://www.isotc211.org/2005/gmd"
  xmlns:gco="http://www.isotc211.org/2005/gco"
  xmlns:gmi="http://www.isotc211.org/2005/gmi"
  xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:swe="http://schemas.opengis.net/sweCommon/2.0/"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:gml="http://www.opengis.net/gml/3.2"
  xmlns:eos="http://earthdata.nasa.gov/schema/eos">

  <xsl:output method="xml" indent="yes"/>

  <xsl:template match='/'>
    <gmi:MI_Metadata>
      <gmd:identificationInfo>
        <gmd:MD_DataIdentification>
          <gmd:citation>
            <gmd:CI_Citation>

              <!-- Entry Title -->
              <gmd:title>
                <gco:CharacterString>
                  <xsl:value-of select="/UMM-C/EntryTitle"/>
                </gco:CharacterString>
              </gmd:title>

              <!-- Entry Id -->
              <gmd:identifier>
                <gmd:MD_Identifier>
                  <gmd:code>
                    <gco:CharacterString>
                      <xsl:value-of select="/UMM-C/EntryId/Id"/>
                    </gco:CharacterString>
                  </gmd:code>
                </gmd:MD_Identifier>
              </gmd:identifier>

            </gmd:CI_Citation>
          </gmd:citation>
        </gmd:MD_DataIdentification>
      </gmd:identificationInfo>
    </gmi:MI_Metadata>
  </xsl:template>

</xsl:stylesheet>