(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.data-contact
 "Functions to parse DataCenters and ContactPersons from ISO 19115-2"
 (:require
  [cmr.common.xml.parse :refer :all]
  [cmr.common.xml.simple-xpath :refer [select text]]
  [cmr.umm-spec.iso19115-2-util :refer [char-string-value]]))

(def xml
 "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
  <gmd:pointOfContact xmlns:gmi=\"http://www.isotc211.org/2005/gmi\" xmlns:eos=\"http://earthdata.nasa.gov/schema/eos\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gml=\"http://www.opengis.net/gml/3.2\" xmlns:gmx=\"http://www.isotc211.org/2005/gmx\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:srv=\"http://www.isotc211.org/2005/srv\" xmlns:swe=\"http://schemas.opengis.net/sweCommon/2.0/\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">
      <gmd:CI_ResponsibleParty>
        <gmd:individualName>
          <gco:CharacterString>Per Gloersen</gco:CharacterString>
        </gmd:individualName>
        <gmd:contactInfo>
          <gmd:CI_Contact>
            <gmd:phone>
              <gmd:CI_Telephone>
                <gmd:voice>
                  <gco:CharacterString>1 301 614 5710 x</gco:CharacterString>
                </gmd:voice>
                <gmd:facsimile>
                  <gco:CharacterString>1 301 614 5644 x</gco:CharacterString>
                </gmd:facsimile>
              </gmd:CI_Telephone>
            </gmd:phone>
            <gmd:address>
              <gmd:CI_Address>
                <gmd:deliveryPoint>
                  <gco:CharacterString>Laboratory for Hydrospheric Processes</gco:CharacterString>
                </gmd:deliveryPoint>
                <gmd:deliveryPoint>
                  <gco:CharacterString>NASA/Goddard Space Flight Center</gco:CharacterString>
                </gmd:deliveryPoint>
                <gmd:deliveryPoint>
                  <gco:CharacterString>Code 971</gco:CharacterString>
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
                <gmd:electronicMailAddress>
                  <gco:CharacterString>per.gloersen@gsfc.nasa.gov</gco:CharacterString>
                </gmd:electronicMailAddress>
              </gmd:CI_Address>
            </gmd:address>
            <gmd:onlineResource>
              <gmd:CI_OnlineResource>
                <gmd:linkage>
                  <gmd:URL>http://nsidc.org</gmd:URL>
                </gmd:linkage>
                <gmd:name gco:nilReason=\"missing\"/>
                <gmd:description gco:nilReason=\"missing\"/>
              </gmd:CI_OnlineResource>
            </gmd:onlineResource>
          </gmd:CI_Contact>
        </gmd:contactInfo>
        <gmd:role>
          <gmd:CI_RoleCode codeList=\"http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_RoleCode\" codeListValue=\"principalInvestigator\">principalInvestigator</gmd:CI_RoleCode>
        </gmd:role>
      </gmd:CI_ResponsibleParty>
    </gmd:pointOfContact>")

(def contact-info-xml
 (first (select xml "gmd:pointOfContact/gmd:CI_ResponsibleParty/gmd:contactInfo/gmd:CI_Contact")))

(defn- parse-phone-contact-info
 [contact-info-xml]
 (when-let [phone (first (select contact-info-xml "gmd:phone/gmd:CI_Telephone"))]
  (seq
   (remove nil?
     (concat
       (for [voice (select phone "gmd:voice")]
         {:Type "Telephone"
          :Value (value-of voice "gco:CharacterString")})
       (for [fax (select phone "gmd:facsimile")]
         {:Type "Fax"
          :Value (value-of fax "gco:CharacterString")}))))))

(defn- parse-email-contact
 [contact-info-xml]
 (for [address (select contact-info-xml "gmd:address/gmd:CI_Address")]
  (when-let [email (char-string-value address "gmd:electronicMailAddress")]
   {:Type "Email"
    :Value email})))

(defn- parse-addresses
 [contact-info-xml]
 (when-let [address (first (select contact-info-xml "gmd:address/gmd:CI_Address"))]
  {:StreetAddresses (for [street-address (select address "gmd:deliveryPoint")]
                      (value-of street-address "gco:CharacterString"))
   :City (char-string-value address "gmd:city")
   :StateProvince (char-string-value address "gmd:administrativeArea")
   :PostalCode (char-string-value address "gmd:postalCode")}))

(defn- parse-online-resources
 [contact-info-xml]
 (when-let [online-resource (first (select contact-info-xml "gmd:onlineResource/gmd:CI_OnlineResource"))]
  {:URLs [(value-of online-resource "gmd:linkage/gmd:URL")]
   :Description (char-string-value online-resource "gmd:description")}))

(defn- parse-contact-information
 [contact-info-xml]
 {:ContactMechanisms (remove nil? (concat
                                         (parse-phone-contact-info contact-info-xml)
                                         (parse-email-contact contact-info-xml)))
  :Addresses (parse-addresses contact-info-xml)
  :RelatedUrls (parse-online-resources contact-info-xml)
  :ServiceHours (char-string-value contact-info-xml "gmd:hoursOfService")
  :ContactInstruction (char-string-value contact-info-xml "gmd:ContactInstructions")})

(defn point-of-contact->contact-person
 [xml]
 {:LastName (char-string-value xml "gmd:pointOfContact/gmd:CI_ResponsibleParty/gmd:individualName")
  :ContactInformation (parse-contact-information (first (select xml "gmd:pointOfContact/gmd:CI_ResponsibleParty/gmd:contactInfo/gmd:CI_Contact")))})
