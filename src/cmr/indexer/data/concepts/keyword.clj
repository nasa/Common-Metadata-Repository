(ns cmr.indexer.data.concepts.keyword
  "Contains functions to create keyword fields"
  (require [clojure.string :as str]
           [cmr.indexer.data.concepts.science-keyword :as sk]
           [cmr.indexer.data.concepts.attribute :as attrib]))

;; NOTE -  The following fields are marked as deprecated in the UMM documenation
;; and are therefore not used for keyword searches in the CMR:
;    SuggestedUsage (ECHO10)
;; TODO - The following fields are not mentioned in the UMM documentation and should
;; be inquired about
;;   :version-description
;; TODO - the following fields need to be added
;;    :campaign-short-name
;;    :temporal-keyword
;;
(def keyword-fields
  "Fields that are used to construct the keywords field for keyword searches"
  [:entry-title
   :concept-id
   :collection-data-type
   :short-name
   :long-name
   :archive-center
   :summary
   :version-id
   :processing-level-id
   :spatial-keywords
   ])

;; Regex to split strings with special characters into multiple words for keyword searches
(def keywords-separator-regex #"[!@#$%^&()\-=_+{}\[\]|;'.,\"/:<>?`~* ]")

(defn prepare-keyword-field
  [field-value]
  "Convert a string to lowercase then separate it into keywords"
  (when field-value
    (str/split (str/lower-case field-value) keywords-separator-regex)))

(str/join " " (mapcat prepare-keyword-field ["New York" "Washington DC"]))

;; TODO - add :long-name - should be part of product, :temporal-keyword
(defn create-keywords-field
  [collection]
  "Create a keyword field for keyword searches by concatenating several other fields
  into a single string"
  (let [{:keys [concept-id provider-id revision-date format]} collection
        {{:keys [short-name version-id processing-level-id collection-data-type]} :product
         :keys [entry-id entry-title summary spatial-keywords associated-difs]} collection
        platforms (:platforms collection)
        platform-short-name-str (str/join " " (map :short-name platforms))
        platform-long-name-str (str/join " " (map :long-name platforms))
        instruments (mapcat :instruments platforms)
        instrument-short-name-str (str/join " " (keep :short-name instruments))
        sensors (mapcat :sensors instruments)
        sensor-short-name-str (str/join " " (keep :short-name sensors))
        two-d-coord-name-str (str/join " " (map :name (:two-d-coordinate-systems collection)))
        orgs (:organizations collection)
        archive-center-str (str/join " " (remove nil? (for [org orgs]
                                                        (let [{:keys [type org-name]} org]
                                                          (when (= :archive-center type) org-name)))))
        science-keywords (sk/science-keywords->keywords collection)
        science-keyword-str (str/join " " (mapcat prepare-keyword-field science-keywords))
        attrib-keyword-str (str/join " " (mapcat prepare-keyword-field
                                                 (attrib/psas->keywords collection)))
        spatial-keyword-str (str/join " " (mapcat prepare-keyword-field spatial-keywords))
        flat-fields-str (str/join " " (mapcat prepare-keyword-field [concept-id
                                                                     entry-title
                                                                     collection-data-type
                                                                     short-name
                                                                     archive-center-str
                                                                     sensor-short-name-str
                                                                     two-d-coord-name-str
                                                                     summary
                                                                     version-id
                                                                     processing-level-id]))]
    (str/join " "
              (into []
                    [flat-fields-str
                     science-keyword-str
                     attrib-keyword-str
                     spatial-keyword-str
                     platform-short-name-str
                     platform-long-name-str]))))
