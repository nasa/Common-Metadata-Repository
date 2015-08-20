(ns cmr.umm-spec.xml-to-umm-mappings.dif9
  "Defines mappings from DIF9 XML into UMM records"
  (:require [cmr.umm-spec.xml-to-umm-mappings.dsl :refer :all]
            [cmr.umm-spec.xml-to-umm-mappings.add-parse-type :as apt]
            [cmr.umm-spec.json-schema :as js]))

(def dif9-xml-to-umm-c
  (apt/add-parsing-types
    js/umm-c-schema
    (object
      {:EntryTitle (xpath "/DIF/Entry_Title")
       :EntryId (xpath "/DIF/Entry_ID")
       :Abstract (xpath "/DIF/Summary/Abstract")
       :Purpose (xpath "/DIF/Summary/Purpose")
       :DataLanguage (xpath "/DIF/Data_Set_Language")
       :TemporalExtents (for-each "."
                          (object {:RangeDateTimes (for-each "/DIF/Temporal_Coverage"
                                                     (object {:BeginningDateTime (xpath "Start_Date")
                                                              :EndingDateTime    (xpath "Stop_Date")}))}))})))
