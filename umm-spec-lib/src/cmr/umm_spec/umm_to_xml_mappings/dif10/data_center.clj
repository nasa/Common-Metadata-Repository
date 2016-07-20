(ns cmr.umm-spec.umm-to-xml-mappings.dif10.data-center
  "Functions for generating DIF10 XML elements from UMM data centers."
  (:require [cmr.umm-spec.util :as u]
            [cmr.umm-spec.umm-to-xml-mappings.dif10.data-contact :as contact]))

(defn- personnel-roles
  "Get the personnel roles for the center. In UMM-C, the roles are stored on the
   Contact Persons and Contact Groups. In DIF10 they are on the Personnel field."
  [center]
  (if (seq (:ContactPersons center))
    (:Roles (first (:ContactPersons center)))
    (:Roles (first (:ContactGroups center)))))

(defn generate-organizations
  "Returns the DIF10 Organization elements from the given umm collection."
  [c]
  (let [qualified-centers (if (seq (:DataCenters c))
                            (:DataCenters c)
                            [u/not-provided-data-center])]
    (for [center qualified-centers]
       [:Organization
        (for [role (:Roles center)]
         [:Organization_Type role])
        [:Organization_Name (if-let [uuid (:Uuid center)] {:uuid uuid} {})
         [:Short_Name (:ShortName center)]
         [:Long_Name (:LongName center)]]
        [:Hours_Of_Service (get-in [:ContactInformation :ServiceHours] center)]
        [:Instructions (get-in [:ContactInformation :ContactInstruction] center)]
        [:Organization_URL (-> (:ContactInformation center)
                              first
                              :RelatedUrls
                              first
                              :URLs
                              first)]
        ; ;; Personnel within Data_Center
        [:Personnel
         ; (for [role (personnel-roles center)]
         ;   [:Role role])
         [:Role "DATA CENTER CONTACT"]
         (contact/generate-personnel center)]])))
