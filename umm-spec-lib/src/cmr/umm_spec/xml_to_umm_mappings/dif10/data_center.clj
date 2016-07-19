(ns cmr.umm-spec.xml-to-umm-mappings.dif10.data-center
  "Defines mappings and parsing from DIF 10 elements into UMM records data center fields."
  (:require [clojure.set :as set]
            [cmr.common.xml.parse :refer :all]
            [cmr.common.xml.simple-xpath :refer [select text]]))
            ; [cmr.umm-spec.xml-to-umm-mappings.dif9.data-contact :as contact]
            ; [cmr.umm-spec.umm-to-xml-mappings.dif9.data-center :as center]))

; (def dif9-data-center-contact-role->umm-contact-role
;   "DIF9 data center contact role to UMM conatct role mapping. Here we only define the roles that
;   do not map to Data Center Contact which is our default."
;   (set/map-invert center/umm-contact-role->dif9-data-center-contact-role))
;
; (defn parse-originating-centers
;   "Returns the UMM data centers from parsing the DIF9 Originating_Center in the given xml document.
;   There can only be one originating center, but we retun it in a vector anyways for better handling
;   in the caller."
;   [doc]
;   (when-let [originating-center (value-of doc "/DIF/Originating_Center")]
;     [{:Roles ["ORIGINATOR"]
;       :ShortName originating-center}]))

(defn parse-contact-information
 "Returns UMM-C ContactInformation from DIF 10 XML Organization"
 [center]
 (let [service-hours (value-of center "Hours_Of_Service")
       instruction (value-of center "Instructions")
       related-url (value-of center "Organization_URL")]
   (if (or service-hours instruction related-url)
     {:ServiceHours (value-of center "Hours_Of_Service")
      :ContactInstruction (value-of center "Instructions")
      :RelatedUrls (when-let [related-url (value-of center "Organization_URL")]
                     [{:URLs [related-url]}])}
     nil)))



(defn parse-data-centers
  "Returns UMM-C data centers from DIF 10 XML document."
  [doc]
  (def doc doc)
  ;; TO DO: UUID, Contact Persons, Contact Groups
  (for [center (select doc "/DIF/Organization")]
    {:Roles (seq (values-at center "Organization_Type"))
     :ShortName (value-of center "Organization_Name/Short_Name")
     :LongName (value-of center "Organization_Name/Long_Name")
     ;; We probably want to refactor the following call into the common parse namespace later
     :Uuid (:uuid (:attrs (first (filter #(= :Organization_Name (:tag %)) (:content center)))))
     :ContactInformation (parse-contact-information center)}))
     ;:ContactPersons (contact/parse-contact-persons (select center "Personnel"))}))

(comment
  (println doc))
