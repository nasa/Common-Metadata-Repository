(ns cmr.umm-spec.umm-to-xml-mappings.dif9.data-center
  "Functions for generating DIF9 XML elements from UMM data centers."
  (:require
   [cmr.umm-spec.umm-to-xml-mappings.dif9.data-contact :as contact]
   [cmr.umm-spec.util :as u]))

(def umm-contact-role->dif9-data-center-contact-role
  "UMM conatct role to DIF9 data center contact role mapping. Here we only define the roles that
  do not map to DATA CENTER CONTACT which is our default."
  {"Investigator" "INVESTIGATOR"})

(def dif9-processor-group
  "Value to use as the Group for the Metadata entry for a processing center"
  "gov.nasa.esdis.cmr.processor")

(defn generate-processing-centers
  "Returns the dif9 processing centers as extended metadata elements. Can have multiple processing
  centers."
  [c]
  (for [processing-center (:DataCenters c)
        :let [long-name (:LongName processing-center)]
        :when (and (.contains (:Roles processing-center) "PROCESSOR")
                   (or (nil? long-name) (.endsWith ".processor" long-name)))]
    [:Metadata
     [:Group (or (:LongName processing-center) dif9-processor-group)]
     [:Name "Processor"]
     [:Value (:ShortName processing-center)]]))

(defn generate-data-centers
  "Returns the DIF9 data center elements from the given umm collection. We generate a DIF9 data
  center if the data center role is either ARCHIVER or DISTRIBUTOR."
  [c]
  (let [qualified-centers (filter #(or (.contains (:Roles %) "ARCHIVER")
                                       (.contains (:Roles %) "DISTRIBUTOR"))
                                  (:DataCenters c))
        qualified-centers (if (seq qualified-centers)
                            qualified-centers
                            [u/not-provided-data-center])]
    (for [center qualified-centers]
      [:Data_Center
       [:Data_Center_Name (if-let [uuid (:Uuid center)] {:uuid uuid} {})
        [:Short_Name (:ShortName center)]
        [:Long_Name (:LongName center)]]
       [:Data_Center_URL (-> (:ContactInformation center)
                             :RelatedUrls
                             first
                             :URL)]
       ;; Personnel within Data_Center
       (if (or (seq (:ContactGroups center)) (seq (:ContactPersons center)))
         (contact/generate-personnel center umm-contact-role->dif9-data-center-contact-role)
         [:Personnel
          [:Role u/not-provided]
          [:Last_Name u/not-provided]])])))
