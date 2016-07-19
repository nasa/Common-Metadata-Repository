(ns cmr.umm-spec.umm-to-xml-mappings.dif10.data-center
  "Functions for generating DIF10 XML elements from UMM data centers."
  (:require [cmr.umm-spec.util :as u]))
            ;[cmr.umm-spec.umm-to-xml-mappings.dif9.data-contact :as contact]))

; (def umm-contact-role->dif9-data-center-contact-role
;   "UMM conatct role to DIF9 data center contact role mapping. Here we only define the roles that
;   do not map to DATA CENTER CONTACT which is our default."
;   {"Investigator" "INVESTIGATOR"})
;
; (defn generate-originating-center
;   "Returns the DIF9 originating center element from the given umm collection"
;   [c]
;   (when-let [originating-center (first (filter #(.contains (:Roles %) "ORIGINATOR")
;                                                (:DataCenters c)))]
;     [:Originating_Center (:ShortName originating-center)]))

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
       [:Personnel
        [:Role "DATA CENTER CONTACT"]
        [:Contact_Person
         [:Last_Name u/not-provided]]]])))
       ; ;; Personnel within Data_Center
       ; (if (or (seq (:ContactGroups center)) (seq (:ContactPersons center)))
       ;   (contact/generate-personnel center umm-contact-role->dif9-data-center-contact-role)
       ;   [:Personnel
       ;    [:Role u/not-provided]
       ;    [:Last_Name u/not-provided]])])))
