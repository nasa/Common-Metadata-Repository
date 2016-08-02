(ns cmr.umm-spec.umm-to-xml-mappings.echo10.data-contact
  "Functions for generating ECHO10 XML contact elements from UMM data centers and contact persons."
  (:require [cmr.umm-spec.util :as u]))

(def umm-data-center-role->echo10-contact-organization-role
 {"ARCHIVER" "ARCHIVER"
  "DISTRIBUTOR" "DISTRIBUTOR"
  "ORIGINATOR" "Data Originator"
  "PROCESSOR" "PROCESSOR"})

(defn- generate-organization-contacts
  "Returns the ECHO10 Contact elements from the given UMM collection data centers."
  [c]
  (let [data-centers (if (seq (:DataCenters c))
                       (:DataCenters c)
                       [u/not-provided-data-center])]
    (for [center data-centers
          :let [contact-information (:ContactInformation center)]]
      [:Contact
       [:Role (first (map #(get umm-data-center-role->echo10-contact-organization-role %)
                       (:Roles center)))]
       [:HoursOfService (:ServiceHours contact-information)]
       [:Instructions (:ContactInstruction contact-information)]
       [:OrganizationName (:ShortName center)]])))
       ;[:OrganizationName (:LongName center)]])))

(defn generate-contacts
  "Returns the ECHO10 Contact elements from the given UMM"
  [c]
  [:Contacts
   (let [x (generate-organization-contacts c)]
     (proto-repl.saved-values/save 26)
     x)])
