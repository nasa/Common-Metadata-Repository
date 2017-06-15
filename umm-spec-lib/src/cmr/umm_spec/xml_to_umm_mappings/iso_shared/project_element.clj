(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.project-element
 "Functions to parse Projects from ISO 19115-2 and SMAP"
 (:require
  [clojure.string :as string]
  [cmr.common.util :as util]
  [cmr.common.xml.parse :refer :all]
  [cmr.common.xml.simple-xpath :refer [select text]]
  [cmr.umm-spec.iso19115-2-util :as iso-util :refer [char-string-value]]
  [cmr.umm-spec.util :as su :refer [char-string]]))

(defn parse-projects
  "Returns the projects parsed from the given xml document."
  [doc projects-xpath sanitize?]
  (for [proj (select doc projects-xpath)]
    (let [short-name (value-of proj iso-util/short-name-xpath)
          long-name (value-of proj iso-util/long-name-xpath)
          start-end-date (when-let [date (value-of proj "gmi:description/gco:CharacterString")]
                           (string/split (string/trim date) #"\s+"))
          ;; date is built as: StartDate: 2001:01:01T01:00:00Z EndDate: 2002:02:02T01:00:00Z
          ;; One date can exist without the other.
          start-date (when start-end-date
                       (if (= "StartDate:" (get start-end-date 0))
                         (get start-end-date 1)
                         (get start-end-date 3)))
          end-date (when start-end-date
                     (if (= "EndDate:" (get start-end-date 0))
                       (get start-end-date 1)
                       (get start-end-date 3)))
          campaigns (seq (map #(value-of % iso-util/campaign-xpath) (select proj "gmi:childOperation")))]
      (util/remove-nil-keys
        {:ShortName short-name
         :LongName (su/truncate long-name su/PROJECT_LONGNAME_MAX sanitize?)
         :StartDate start-date
         :EndDate end-date
         :Campaigns campaigns}))))
