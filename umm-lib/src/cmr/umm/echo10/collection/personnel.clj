(ns cmr.umm.echo10.collection.personnel
  "Provides functions to parse and generate DIF Personnel elements."
  (:require [clojure.data.xml :as x]
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
  (for [phone-element (cx/elements-at-path contact-element [:OrganizatonPhones :Phone])]
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
          street-address-lines [(cx/string-at-path address-element [:StreetAddress])]]
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
  ;; In practice, there seems to be only one contact person listed per collection, so we
  ;; associate all email, phones, etc. with that one contact.
  (let [role (cx/string-at-path contact-element [:Role])
        first-name (cx/string-at-path contact-element [:ContactPersons :ContactPerson :FirstName])
        last-name (cx/string-at-path contact-element [:ContactPersons :ContactPerson :LastName])
        middle-name (cx/string-at-path contact-element [:ContactPersons :ContactPerson :MiddleName])
        addresses (xml-elem->addresses contact-element)
        emails (xml-elem->emails contact-element)
        phones (xml-elem->phones contact-element)]
    [(c/map->ContactPerson {:first-name first-name
                            :middle-name middle-name
                            :last-name last-name
                            :roles [role]
                            :addresses (seq addresses)
                            :emails (seq emails)
                            :phones (seq phones)})]))

(defn generate-address
  [address]
  (let [{:keys [city state-province country postal-code street-address-lines]} address]
    (x/element :Contact_Address {}
               (for [line street-address-lines]
                 (x/element :Address {} line))
               (when city
                 (x/element :City {} city))
               (when state-province
                 (x/element :Province_or_State {} state-province))
               (when postal-code
                 (x/element :Postal_Code {} postal-code))
               (when country
                 (x/element :Country {} country)))))

(defn generate-contacts
  [personnel]
  (when (not-empty personnel)
    (x/element :Contacts {}
               (for [contact personnel]
                 (let [{:keys [roles first-name middle-name last-name emails phones address]} contact]
                   (x/element :Contact {}
                              (x/element :Role {} (first roles))
                              (let [person (first personnel)
                                    {:keys [roles first-name middle-name last-name emails phones addresses]} person]
                                (x/element :Personnel {}
                                           (for [role roles]
                                             (x/element :Role {} role))
                                           (when first-name
                                             (x/element :First_Name {} first-name))
                                           (when middle-name
                                             (x/element :Middle_Name {} middle-name))
                                           (x/element :Last_Name {} last-name)
                                           (for [email emails]
                                             (x/element :Email {} email))
                                           (for [phone phones]
                                             (x/element :Phone {} (:number phone)))
                                           (generate-address (first addresses))))))))))
