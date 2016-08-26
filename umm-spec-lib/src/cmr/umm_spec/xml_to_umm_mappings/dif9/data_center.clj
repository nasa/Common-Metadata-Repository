(ns cmr.umm-spec.xml-to-umm-mappings.dif9.data-center
  "Defines mappings and parsing from DIF 9 elements into UMM records data center fields."
  (:require [clojure.set :as set]
            [cmr.common.xml.parse :refer :all]
            [cmr.common.xml.simple-xpath :refer [select text]]
            [cmr.umm-spec.xml-to-umm-mappings.dif9.data-contact :as contact]
            [cmr.umm-spec.umm-to-xml-mappings.dif9.data-center :as center]))

(def dif9-data-center-contact-role->umm-contact-role
  "DIF9 data center contact role to UMM conatct role mapping. Here we only define the roles that
  do not map to Data Center Contact which is our default."
  (set/map-invert center/umm-contact-role->dif9-data-center-contact-role))

(defn parse-originating-centers
  "Returns the UMM data centers from parsing the DIF9 Originating_Center in the given xml document.
  There can only be one originating center, but we retun it in a vector anyways for better handling
  in the caller."
  [doc]
  (when-let [originating-center (value-of doc "/DIF/Originating_Center")]
    [{:Roles ["ORIGINATOR"]
      :ShortName originating-center}]))

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
  [doc]
  (for [center (select doc "/DIF/Data_Center")]
    ;; all DIF Data_Centers have roles of ARCHIVER and DISTRIBUTOR
    {:Roles ["ARCHIVER" "DISTRIBUTOR"]
     :ShortName (value-of center "Data_Center_Name/Short_Name")
     :LongName (value-of center "Data_Center_Name/Long_Name")
     ;; We probably want to refactor the following call into the common parse namespace later
     :Uuid (:uuid (:attrs (first (filter #(= :Data_Center_Name (:tag %)) (:content center)))))
     :ContactInformation (when-let [related-url (value-of center "Data_Center_URL")]
                           {:RelatedUrls [{:URLs [related-url]}]})
     :ContactPersons (contact/parse-contact-persons (select center "Personnel"))}))
