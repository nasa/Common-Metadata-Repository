(ns cmr.umm-spec.xml-to-umm-mappings.dif9.data-center
  "Defines mappings and parsing from DIF 9 elements into UMM records data center fields."
  (:require
   [cmr.common.xml.parse :refer [value-of]]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.url :as url]
   [cmr.umm-spec.xml-to-umm-mappings.dif9.data-contact :as contact]))

(defn parse-processing-centers
  "Returns the UMM data centers from parsing DIF9 Extended_Metadata for Metadata with name
  'Processor'"
  [doc]
  (for [center (select doc "/DIF/Extended_Metadata/Metadata[Name='Processor']")]
    {:Roles ["PROCESSOR"]
     :ShortName (value-of center "Value")
     :LongName (value-of center "Group")}))

(defn parse-data-centers
  "Returns UMM-C data centers from DIF 9 XML document."
  [doc sanitize?]
  (for [center (select doc "/DIF/Data_Center")]
    ;; all DIF Data_Centers have roles of ARCHIVER and DISTRIBUTOR
    {:Roles ["ARCHIVER" "DISTRIBUTOR"]
     :ShortName (value-of center "Data_Center_Name/Short_Name")
     :LongName (value-of center "Data_Center_Name/Long_Name")
     ;; We probably want to refactor the following call into the common parse namespace later
     :Uuid (:uuid (:attrs (first (filter #(= :Data_Center_Name (:tag %)) (:content center)))))
     :ContactInformation (when-let [related-url (value-of center "Data_Center_URL")]
                           {:RelatedUrls [{:URL (url/format-url related-url sanitize?)
                                           :URLContentType "DataCenterURL"
                                           :Type "HOME PAGE"}]})
     :ContactPersons (contact/parse-contact-persons (select center "Personnel"))}))
