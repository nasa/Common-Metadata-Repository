(ns cmr.umm.echo10.collection.org
  "Archive and Processing Center elements of echo10 are mapped umm organization elements."
  (:require [clojure.data.xml :as x]
            [clojure.string :as str]
            [cmr.common.util :as util]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]
            [cmr.umm.echo10.collection.personnel :as pe]
            [camel-snake-kebab :as csk]))

(defn- contacts-xml->Organizations
  "Return organizations for the contacts in the parsed xml structure."
  [contact-elements]
  (for [contact-element contact-elements]
    (let [role (cx/string-at-path contact-element [:Role])
          org-name (cx/string-at-path contact-element [:OrganizationName])
          personnel (pe/xml-elem->personnel contact-element)]
      (c/map->Organization {:org-type role
                            :org-name org-name
                            :personnel personnel}))))

(defn xml-elem->Organizations
  "Return organizations or []"
  [collection-element]
  (let [archive-ctr (cx/string-at-path collection-element [:ArchiveCenter])
        processing-ctr (cx/string-at-path collection-element [:ProcessingCenter])
        contacts (cx/elements-at-path collection-element [:Contacts :Contact])]
    (seq (concat
           (when processing-ctr
             [(c/map->Organization {:org-type :processing-center :org-name processing-ctr})])
           (when archive-ctr
             [(c/map->Organization {:org-type :archive-center :org-name archive-ctr})])
           (when contacts
             (contacts-xml->Organizations contacts))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generators

(defn generate-center
  "Return archive or processing center based on org type"
  [center-type orgs]
  (for [org orgs
        :when (= center-type (:org-type org))]
    (let [elem-name (-> center-type
                        name
                        csk/->CamelCase
                        keyword)]
      (x/element elem-name {} (:org-name org)))))

(defn generate-archive-center
  "Return archive center ignoring other type of organization like processing center"
  [orgs]
  (generate-center :archive-center orgs))

(defn generate-processing-center
  "Return processing center ignoring other type of organization like archive center"
  [orgs]
  (generate-center :processing-center orgs))

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
                              (x/element :City {} city)
                              (x/element :StateProvince {} state-province)
                              (x/element :PostalCode {} postal-code)
                              (x/element :Country {} country))))))

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
                            (generate-contact-emails org)
                            (generate-contact-addresses org)
                            (generate-contact-phones org)
                            (generate-contact-persons org))))))

(comment
  ;;;;;;;;;
  (x/parse-str cmr.umm.test.echo10.collection/all-fields-collection-xml)
  (parse-collection cmr.umm.test.echo10.collection/all-fields-collection-xml)
  (xml-elem->Campaigns (x/parse-str cmr.umm.test.echo10.collection/all-fields-collection-xml))
  (cx/elements-at-path
    (x/parse-str cmr.umm.test.echo10.collection/all-fields-collection-xml)
    [:ArchiveCenter])

  (xml-elem->Organizations (x/parse-str cmr.umm.test.echo10.collection/all-fields-collection-xml))
  (let [orgs (vector (c/map->Organization {:org-type :archive-center :org-name "ac se"})
                     (c/map->Organization {:org-type :processing-center :org-name "pro se"}))
        arctr (generate-archive-center orgs)
        prctr (generate-processing-center orgs)]
    (vector arctr prctr))

  (clojure.repl/dir camel-snake-kebab)

  ;;;;;;;;;;;;
  )


