(ns cmr.umm-spec.umm-to-xml-mappings.iso-smap.data-contact
  "Functions for generating ISO19115-2 XML elements from UMM DataCenters, ContactPersons and ContactGroups."
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [cmr.common.xml.gen :refer :all]
   [cmr.umm-spec.iso19115-2-util :as iso]
   [cmr.umm-spec.util :refer [char-string]]
   [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.data-contact :as data-contact]
   [cmr.umm-spec.umm-to-xml-mappings.iso-shared.distributions-related-url :as related-url]))

(defn- generate-data-center-name
 "Generate data center name from long and short name. ISO only has one field for name, so these
 should be combined with a delimeter."
 [data-center]
 (if (:LongName data-center)
  (str (:ShortName data-center) " &gt; " (:LongName data-center))
  (:ShortName data-center)))

(def umm-role->iso-data-center-role
  {"ARCHIVER" "distributor"
   "ORIGINATOR" "originator"
   "PROCESSOR" "originator"
   "DISTRIBUTOR" "distributor"})

(def smap-role->path
  {"ARCHIVER" :gmd:pointOfContact
   "ORIGINATOR" :gmd:pointOfContact
   "PROCESSOR" :gmd:citedResponsibleParty
   "DISTRIBUTOR" :gmd:pointOfContact})

(def translated-contact-mechanism-types
 {"Direct Line" "Telephone"
  "Email" "Email"
  "Fax" "Fax"
  "Mobile" "Telephone"
  "Primary" "Telephone"
  "TDD/TTY Phone" "Telephone"
  "Telephone" "Telephone"
  "U.S. toll free" "Telephone"})

(defn generate-contact-info
  "Generate contact info xml from ContactInformation"
  [contact-info]
  [:gmd:contactInfo
   [:gmd:CI_Contact
    (when-let [phone-contacts (seq (data-contact/get-phone-contact-mechanisms contact-info))]
      [:gmd:phone
       [:gmd:CI_Telephone
        (for [phone (filter #(= "Telephone" (:Type %)) phone-contacts)]
          [:gmd:voice (char-string (:Value phone))])
        (for [fax (filter #(= "Fax" (:Type %)) phone-contacts)]
          [:gmd:facsimile (char-string (:Value fax))])]])
    (let [address (first (:Addresses contact-info))
          emails (filter #(= "Email" (:Type %)) (:ContactMechanisms contact-info))]
      (when (or address emails)
        [:gmd:address
         [:gmd:CI_Address
          (for [street-address (:StreetAddresses address)]
            [:gmd:deliveryPoint (char-string street-address)])
          (when-let [city (:City address)]
            [:gmd:city (char-string city)])
          (when-let [state (:StateProvince address)]
            [:gmd:administrativeArea (char-string state)])
          (when-let [postal-code (:PostalCode address)]
            [:gmd:postalCode (char-string postal-code)])
          (when-let [country (:Country address)]
            [:gmd:country (char-string country)])
          (for [email emails]
            [:gmd:electronicMailAddress (char-string (:Value email))])]]))
    (when-let [url (first (:RelatedUrls contact-info))]
      (related-url/generate-online-resource-url
       url :gmd:onlineResource false))
    (when-let [hours (:ServiceHours contact-info)]
      [:gmd:hoursOfService (char-string hours)])
    (when-let [instruction (:ContactInstruction contact-info)]
      [:gmd:contactInstructions (char-string instruction)])]])

(defn- generate-contact-person
 "Generate a contact person. If the contact person is associated with a data center, the data center
 name will go under organisationName"
 ([person]
  (generate-contact-person person nil))
 ([person data-center-name]
  (let [{:keys [FirstName MiddleName LastName NonDataCenterAffiliation ContactInformation]} person]
   [:gmd:pointOfContact
    [:gmd:CI_ResponsibleParty
     [:gmd:individualName (char-string (str/trim (str/join " " [FirstName MiddleName LastName])))]
     (when data-center-name
      [:gmd:organisationName (char-string data-center-name)])
     [:gmd:positionName (char-string NonDataCenterAffiliation)]
     (generate-contact-info ContactInformation)
     [:gmd:role
      [:gmd:CI_RoleCode
        {:codeList (:ndgc iso/code-lists)
         :codeListValue "pointOfContact"} "pointOfContact"]]]])))

(defn generate-contact-persons
  "Generate contact persons in ISO."
  [contact-persons]
  (distinct
   (for [person contact-persons
         :let [roles (:Roles person)]
         :when (some #(not= "Metadata Author" %) roles)]
     (generate-contact-person person))))

(defn generate-data-centers-contact-persons
  "Generates all data center contact person entries for roles other than Metadata Author"
  [c & umm-roles]
  (for [data-center (:DataCenters c)
          :let [umm-roles (set umm-roles)
                dc-roles (set (:Roles data-center))
                valid-roles (set/intersection umm-roles dc-roles)]
          :when (not (empty? valid-roles))
          :let [contact-info (:ContactInformation data-center)
                data-center-name (generate-data-center-name data-center)
                persons (:ContactPersons data-center)]]
    (for [person persons
          :let [roles (:Roles person)
                {:keys [FirstName MiddleName LastName NonDataCenterAffiliation ContactInformation]} person]
          :when (some #(not= "Metadata Author" %) roles)]

      (for [umm-role valid-roles
            :let [path (get smap-role->path umm-role)]]
        (distinct
         (for [role roles
               :when (not= "Metadata Author" role)]
           [path
            [:gmd:CI_ResponsibleParty
             [:gmd:individualName (char-string (str/trim (str/join " " [FirstName MiddleName LastName])))]
             (when data-center-name
               [:gmd:organisationName (char-string data-center-name)])
             (when NonDataCenterAffiliation
               [:gmd:positionName (char-string NonDataCenterAffiliation)])
             (when ContactInformation
               (generate-contact-info ContactInformation))
             [:gmd:role
              [:gmd:CI_RoleCode {:codeList (:iso iso/code-lists)
                                 :codeListValue "pointOfContact"} "pointOfContact"]]]]))))))

(defn generate-data-centers
  "Generates DataCenter entries for given Roles, because PROESSOR is mapped to a seperate location then the
   other data center roles"
  [c & umm-roles]
  (for [data-center (:DataCenters c)
         :let [umm-roles (set umm-roles)
               dc-roles (set (:Roles data-center))
               valid-roles (set/intersection umm-roles dc-roles)]
         :when (not (empty? valid-roles))
         :let [contact-info (:ContactInformation data-center)
               data-center-name (generate-data-center-name data-center)]]
    (distinct
     (for [umm-role valid-roles
           :let [path (get smap-role->path umm-role)
                 smap-role (get umm-role->iso-data-center-role umm-role)]]
       [path
        [:gmd:CI_ResponsibleParty
         [:gmd:organisationName
          (char-string data-center-name)]
         (when contact-info
           (generate-contact-info contact-info))
         [:gmd:role
          [:gmd:CI_RoleCode {:codeList (:iso iso/code-lists)
                             :codeListValue smap-role} smap-role]]]]))))

(defn- data-center-metadata-author
  "Returns all contacts with Metadata Author role."
  [data-center]
  (let [{:keys [ShortName LongName]} data-center
        contact-persons (:ContactPersons data-center)]
    (for [person contact-persons]
      (merge person {:ShortName ShortName
                     :LongName LongName}))))

(defn generate-metadata-authors
  "Generates Metadata Author contact entries for DataCenter ContactPersons and root level ContactPersons."
  [c]
  (for [contact-person (apply concat (:ContactPersons c)
                              (map data-center-metadata-author
                                   (:DataCenters c)))
        :when (some #(= "Metadata Author" %)
                    (:Roles contact-person))
        :let [{:keys [FirstName MiddleName LastName NonDataCenterAffiliation ContactInformation]} contact-person
              data-center-name (generate-data-center-name contact-person)]]
    [:gmd:contact
     [:gmd:CI_ResponsibleParty
      [:gmd:individualName (char-string (str/trim (str/join " " [FirstName MiddleName LastName])))]
      [:gmd:organisationName
       (char-string data-center-name)]
      (when NonDataCenterAffiliation
        [:gmd:positionName (char-string NonDataCenterAffiliation)])
      (when ContactInformation
        (generate-contact-info ContactInformation))
      [:gmd:role
       [:gmd:CI_RoleCode {:codeList (:iso iso/code-lists)
                          :codeListValue "author"} "author"]]]]))
