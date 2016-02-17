(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.organizations-personnel
  (:require [cmr.common.xml.simple-xpath :refer [select text]]
            [cmr.common.xml.parse :refer :all]
            [cmr.umm-spec.iso19115-2-util :as iso]
            [clojure.string :as str]))

(def contact-xpath
  "gmd:contactInfo/gmd:CI_Contact")

(defn- parse-contacts
  [resp]
  (seq (concat
      (for [phone (values-at
                    resp (str contact-xpath
                                  "/gmd:phone/gmd:CI_Telephone/gmd:voice/gco:CharacterString"))]
        {:Type "phone"
         :Value phone})
      (for [email (values-at
                    resp (str contact-xpath
                              "/gmd:address/gmd:CI_Address/gmd:electronicMailAddress/gco:CharacterString"))]
        {:Type "email"
         :Value email}))))

(defn- parse-addresses
  [resp]
  (seq (for [address (select resp (str contact-xpath "/gmd:address/gmd:CI_Address"))]
      {:StreetAddresses (values-at address "gmd:deliveryPoint/gco:CharacterString")
       :City (iso/char-string-value address "gmd:city")
       :StateProvince (iso/char-string-value address "gmd:administrativeArea")
       :PostalCode (iso/char-string-value address "gmd:postalCode")
       :Country (iso/char-string-value address "gmd:country")})))

(defn- parse-party-related-urls
  "Parse ISO online resource urls"
  [resp]
  (for [related-url (select resp (str contact-xpath "/gmd:onlineResource/gmd:CI_OnlineResource"))]
    {:URLs (values-at related-url "gmd:linkage/gmd:URL")
     :Description (iso/char-string-value related-url "gmd:description")}))

(defn parse-responsible-parties
  [role resps]
  ; (cmr.common.dev.capture-reveal/capture role)
  ; (cmr.common.dev.capture-reveal/capture resps)
  (for [resp resps]
    (let [xml-role (value-of resp "gmd:role/gmd:CI_RoleCode")]
      (when (= (str/upper-case xml-role) role)
        {:Role role
         :Party {:OrganizationName {:ShortName (iso/char-string-value resp "gmd:organisationName")}
                 :Person (when-let [name (iso/char-string-value resp "gmd:individualName")]
                           {:LastName name})
                 :ServiceHours (iso/char-string-value resp "gmd:contactInfo/gmd:CI_Contact/gmd:hoursOfService")
                 :ContactInstructions (iso/char-string-value resp "gmd:contactInfo/gmd:CI_Contact/gmd:contactInstructions")
                 :Contacts (parse-contacts resp)
                 :Addresses (parse-addresses resp)
                 :RelatedUrls (parse-party-related-urls resp)}}))))
