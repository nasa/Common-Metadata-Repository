(ns cmr.umm-spec.umm-to-xml-mappings.dif10.data-center
  "Functions for generating DIF10 XML elements from UMM data centers."
  (:require [cmr.umm-spec.util :as u]
            [cmr.umm-spec.umm-to-xml-mappings.dif10.data-contact :as contact]))


(defn generate-organizations
  "Returns the DIF10 Organization elements (Data Centers) from the given UMM collection."
  [c]
  (let [data-centers (if (seq (:DataCenters c))
                       (:DataCenters c)
                       [u/not-provided-data-center])]
    (for [center data-centers
          :let [contact-information (:ContactInformation center)]]
      [:Organization
       (for [role (:Roles center)]
         [:Organization_Type role])
       [:Organization_Name (if-let [uuid (:Uuid center)] {:uuid uuid} {})
        [:Short_Name (:ShortName center)]
        [:Long_Name (:LongName center)]]
       [:Hours_Of_Service (:ServiceHours contact-information)]
       [:Instructions (:ContactInstruction contact-information)]
       [:Organization_URL (-> contact-information
                              :RelatedUrls
                              first
                              :URL)]
       ;; Personnel within Data_Center
       (contact/generate-data-center-personnel center)])))
