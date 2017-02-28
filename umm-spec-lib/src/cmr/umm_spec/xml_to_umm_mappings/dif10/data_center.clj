(ns cmr.umm-spec.xml-to-umm-mappings.dif10.data-center
  "Defines mappings and parsing from DIF 10 elements into UMM records data center fields."
  (:require
   [clojure.set :as set]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select text]]
   [cmr.umm-spec.url :as url]
   [cmr.umm-spec.xml-to-umm-mappings.dif10.data-contact :as contact]))

(defn- parse-contact-information
  "Returns UMM-C ContactInformation from DIF 10 XML Organization"
  [center sanitize?]
  (let [service-hours (value-of center "Hours_Of_Service")
        instruction (value-of center "Instructions")
        related-url (url/format-url (value-of center "Organization_URL") sanitize?)]
    (when (or service-hours instruction related-url)
      {:ServiceHours service-hours
       :ContactInstruction instruction
       :RelatedUrls (when (some? related-url)
                      [{:URL related-url
                        :URLContentType "DataCenterURL"
                        :Type "HOME PAGE"}])})))

(defn parse-data-centers
  "Returns UMM-C data centers from DIF 10 XML document."
  [doc sanitize?]
  (for [center (select doc "/DIF/Organization")]
    {:Roles (seq (values-at center "Organization_Type"))
     :ShortName (value-of center "Organization_Name/Short_Name")
     :LongName (value-of center "Organization_Name/Long_Name")
     :Uuid (:uuid (:attrs (first (filter #(= :Organization_Name (:tag %)) (:content center)))))
     :ContactInformation (parse-contact-information center sanitize?)
     :ContactPersons (contact/parse-contact-persons (select center "Personnel") sanitize?)
     :ContactGroups (contact/parse-contact-groups (select center "Personnel"))}))
