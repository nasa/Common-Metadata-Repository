(ns cmr.umm.echo10.collection.personnel
  "Provides functions to parse and generate ECHO10 Contact elements for the UMM ContactPerson
  personnel attribute."
  (:require [clojure.data.xml :as x]
            [clojure.string :as str]
            [cmr.common.util :as util]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]))

(defn xml-elem->Address
  [addr-element]
  (let [city (cx/string-at-path addr-element [:City])
        state-province (cx/string-at-path addr-element [:Province_or_State])
        country (cx/string-at-path addr-element [:Country])
        postal-code (cx/string-at-path addr-element [:Postal_Code])
        street-address-lines (cx/strings-at-path addr-element [:Address])]
    (c/map->Address {:city city
                     :state-province state-province
                     :country country
                     :postal-code postal-code
                     :street-address-lines street-address-lines})))

(defn- xml-elem->phones
  "Return a list of Phone records for the parsed xml structure."
  [contact-element]
  (for [phone-element (cx/elements-at-path contact-element [:OrganizationPhones :Phone])]
    (c/map->Phone {:number (cx/string-at-path phone-element [:Number])
                   :number-type (cx/string-at-path phone-element [:Type])})))

(defn- xml-elem->emails
  "Return a list of emails for the parsed xml structure."
  [contact-element]
  (cx/strings-at-path contact-element [:OrganizationEmails :Email]))

(defn- xml-elem->addresses
  "Return a list of Addresses for the parsed xml structure."
  [contact-element]
  (for [address-element (cx/elements-at-path contact-element [:OrganizationAddresses :Address])]
    (let [city (cx/string-at-path address-element [:City])
          country (cx/string-at-path address-element [:Country])
          postal-code (cx/string-at-path address-element [:PostalCode])
          state-province (cx/string-at-path address-element [:StateProvince])
          street-address-lines (str/split (cx/string-at-path address-element [:StreetAddress])
                                          #"\n")]
      (c/map->Address {:city city
                       :country country
                       :postal-code postal-code
                       :state-province state-province
                       :street-address-lines street-address-lines}))))

(comment

  ; (cmr.common.dev.capture-reveal/reveal contact-element)

  ; (let [x #clojure.data.xml.Element{:tag :Contact, :attrs {}, :content (#clojure.data.xml.Element{:tag :Role, :attrs {}, :content ("distribution-center")} #clojure.data.xml.Element{:tag :OrganizationName, :attrs {}, :content ("!")} #clojure.data.xml.Element{:tag :OrganizationEmails, :attrs {}, :content ()} #clojure.data.xml.Element{:tag :OrganizationAddresses, :attrs {}, :content (#clojure.data.xml.Element{:tag :Address, :attrs {}, :content (#clojure.data.xml.Element{:tag :StreetAddress, :attrs {}, :content ("!")} #clojure.data.xml.Element{:tag :City, :attrs {}, :content ("!")} #clojure.data.xml.Element{:tag :StateProvince, :attrs {}, :content ("!")} #clojure.data.xml.Element{:tag :PostalCode, :attrs {}, :content ("!")} #clojure.data.xml.Element{:tag :Country, :attrs {}, :content ("!")})})} #clojure.data.xml.Element{:tag :OrganizationPhones, :attrs {}, :content (#clojure.data.xml.Element{:tag :Phone, :attrs {}, :content (#clojure.data.xml.Element{:tag :Number, :attrs {}, :content ("!")} #clojure.data.xml.Element{:tag :Type, :attrs {}, :content ("!")})})} #clojure.data.xml.Element{:tag :ContactPersons, :attrs {}, :content (#clojure.data.xml.Element{:tag :ContactPerson, :attrs {}, :content (#clojure.data.xml.Element{:tag :FirstName, :attrs {}, :content ("!")} #clojure.data.xml.Element{:tag :LastName, :attrs {}, :content ("!")})})})}
  ;       y (cx/elements-at-path x [:OrganizationAddresses :Address])]
  ;   y)

  ; (cmr.common.dev.capture-reveal/reveal addresses)

  ; (let [x (cmr.common.dev.capture-reveal/reveal contact-element)]
  ;       (for [a (cx/elements-at-path x [:OrganizationAddresses :Address])]
  ;         (let [city (cx/string-at-path a [:City])]
  ;           {:city city})))


  )



(defn xml-elem->personnel
  "Return a list of ContactPersons for the parsed xml structure."
  [contact-element]
  ;; Note: ECHO10 splits the emails, phones, addresses out from the contact persons list, so it is
  ;; in theory impossible to know which contact person is associated with which email, phone, etc.
  ;; In practice, however, there seems to be only one contact person listed per collection, so we
  ;; associate all email, phones, etc. with that one contact.
  (let [role (cx/string-at-path contact-element [:Role])
        first-name (cx/string-at-path contact-element [:ContactPersons :ContactPerson :FirstName])
        last-name (cx/string-at-path contact-element [:ContactPersons :ContactPerson :LastName])
        middle-name (cx/string-at-path contact-element [:ContactPersons :ContactPerson :MiddleName])
        addresses (seq (xml-elem->addresses contact-element))
        emails (seq (xml-elem->emails contact-element))
        phones (xml-elem->phones contact-element)]
    (when (or first-name last-name addresses emails)
      [(c/map->ContactPerson {:first-name first-name
                              :middle-name middle-name
                              :last-name last-name
                              :roles [role]
                              :addresses (seq addresses)
                              :emails (seq emails)
                              :phones (seq phones)})])))

(defn- generate-contact-phones
  "Returns the Phone entries for the given organization."
  [org]
  (x/element :OrganizationPhones {}
             (for [person (:personnel org)
                   phone (:phones person)]
               (let [{:keys [number number-type]} phone]
                 (x/element :Phone {}
                            (x/element :Number {} number)
                            (x/element :Type {} number-type))))))

(defn- generate-contact-addresses
  "Returns the Address entries for the given organization."
  [org]
  (x/element :OrganizationAddresses {}
             (for [person (:personnel org)
                   address (:addresses person)]
               (let [{:keys [city country state-province postal-code
                             street-address-lines]} address]
                 (x/element :Address {}
                            (x/element :StreetAddress {}
                                       (util/trunc (str/join "\n" street-address-lines) 1024))
                            (x/element :City {} (util/trunc city 80))
                            (x/element :StateProvince {} (util/trunc state-province 30))
                            (x/element :PostalCode {} (util/trunc postal-code 20))
                            (x/element :Country {} (util/trunc country 10)))))))

(defn- generate-contact-emails
  "Returns the Email entries for the given organization."
  [org]
  (x/element :OrganizationEmails {}
             (for [person (:personnel org)
                   email (:emails person)]
               (x/element :Email {} email))))

(defn- generate-contact-persons
  "Returns the ContactPersons entry for the given organization."
  [org]
  (x/element :ContactPersons {}
             (for [person (:personnel org)]
               (let [{:keys [first-name middle-name last-name]} person]
                 (x/element :ContactPerson {}
                            (when first-name
                              (x/element :FirstName {} first-name))
                            (when middle-name
                              (x/element :MiddleName {} middle-name))
                            (when last-name
                              (x/element :LastName {} last-name)))))))

(defn generate-contacts
  "Return Contacts from the organizations that are not archive centers or processing centers."
  [orgs]
  (x/element :Contacts {}
             (for [org orgs
                   :when (and (not= :archive-center (:org-type org))
                              (not= :processing-center (:org-type org)))]
               (let [{:keys [org-type org-name]} org]
                 (x/element :Contact {}
                            (x/element :Role {} (name org-type))
                            (x/element :OrganizationName {} org-name)
                            (generate-contact-addresses org)
                            (generate-contact-phones org)
                            (generate-contact-emails org)
                            (generate-contact-persons org))))))