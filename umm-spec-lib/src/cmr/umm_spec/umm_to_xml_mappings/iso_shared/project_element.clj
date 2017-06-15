(ns cmr.umm-spec.umm-to-xml-mappings.iso-shared.project-element
  "Functions for generating ISO-19115 and ISO-SMAP XML elements from UMM project records."
  (:require
    [cmr.common.xml.gen :refer :all]
    [cmr.umm-spec.iso19115-2-util :as iso]
    [cmr.umm-spec.util :as su :refer [char-string]]))

(defn- generate-date-string
  "Generate date string for ISO"
  [start-date end-date]
  (let [start-date-str (when start-date
                         (str "StartDate: " start-date))
        end-date-str (when end-date
                       (str " EndDate: " end-date))]
    (if start-date-str
      (str start-date-str " " end-date-str)
      end-date-str)))

(defn generate-projects
  "Generate ISO projects XML from UMM Projects"
  [projects]
  (for [proj projects]
    (let [{short-name :ShortName
           long-name  :LongName
           start-date :StartDate
           end-date   :EndDate
           campaigns  :Campaigns} proj]
      [:gmi:operation
       [:gmi:MI_Operation
         (when-let [date-str (generate-date-string start-date end-date)]
           [:gmi:description
            (char-string date-str)])
         [:gmi:identifier
          [:gmd:MD_Identifier
           [:gmd:code
            (char-string short-name)]
           [:gmd:codeSpace
            (char-string "gov.nasa.esdis.umm.projectshortname")]
           [:gmd:description
            (char-string long-name)]]]
         [:gmi:status ""]
         [:gmi:parentOperation {:gco:nilReason "inapplicable"}]
        (for [campaign campaigns]
          [:gmi:childOperation
           [:gmi:MI_Operation
            [:gmi:identifier
             [:gmd:MD_Identifier
              [:gmd:code
               (char-string campaign)]
              [:gmd:codeSpace
               (char-string "gov.nasa.esdis.umm.campaignshortname")]]]
            [:gmi:status ""]
            [:gmi:parentOperation {:gco:nilReason "inapplicable"}]]])]])))
