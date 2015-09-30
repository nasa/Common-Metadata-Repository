(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.organizations-personnel
   (:require [cmr.umm-spec.simple-xpath :refer [select text]]
            [cmr.umm-spec.xml.parse :refer :all]
            [cmr.umm-spec.iso19115-2-util :as iso]
            [clojure.string :as str]))

(def contact-xpath
  "gmd:contactInfo/gmd:CI_Contact")

(defn- parse-contacts
  [resp]
  (concat
    (for [phone (select resp (str contact-xpath "/gmd:phone/gmd:CI_Telephone/gmd:voice/gco:CharacterString"))]
      {:Type "phone"
       :Value phone})
    (for [email (select resp (str contact-xpath "/gmd:address/gmd:CI_Address/gmd:electronicMailAddress"))]
      {:Type "email"
       :Value email})))

(defn- parse-addresses
  [resp]
  (for [address (select resp "gmd:address/gmd:CI_Address")]
    {:StreetAddresses (values-at address "gmd:deliveryPoint/gco:CharacterString")
     :City (iso/char-string-value address "gmd:city")
     :StateProvince (iso/char-string-value address "gmd:administrativeArea")
     :PostalCode (iso/char-string-value address "gmd:postalCode")
     :Country (iso/char-string-value address "gmd:country")}))

(defn- parse-party-related-urls
  "Parse ISO online resource urls"
  [resp]
  (when-let [related-url (select resp "gmd:onlineResource")]
    {:URLs (values-at related-url "gmd:linkage/gmd:URL")
     :Description (iso/char-string-value related-url "gmd:description")}))

(defn parse-responsible-party
  [role resps]
  ; (cmr.common.dev.capture-reveal/capture role)
  ; (cmr.common.dev.capture-reveal/capture resps)
  (for [resp resps]
    (let [xml-role (value-of resp "gmd:role/gmd:CI_RoleCode")]
      (when (= (str/upper-case xml-role) role)
        {
         :Role role
         :Party {
                 :OrganizationName (iso/char-string-value resp "gmd:OrganizationName")
                 :Person {:LastName (iso/char-string-value resp "gmd:individualName")}
                 :ServiceHours (iso/char-string-value resp "gmd:ServiceHours")
                 :ContactInstructions (iso/char-string-value resp "gmd:ContactInstructions")
                 :Contacts (parse-contacts resp)
                 :Addresses (parse-addresses resp)
                 :RelatedUrls (parse-party-related-urls resp)}}))))
