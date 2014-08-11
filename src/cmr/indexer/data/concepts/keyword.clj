(ns cmr.indexer.data.concepts.keyword
  "Contains functions to create keyword fields"
  (require [clojure.string :as str]
           [cmr.indexer.data.concepts.science-keyword :as sk]
           [cmr.indexer.data.concepts.attribute :as attrib]
           [cmr.indexer.data.concepts.organization :as org]
           [cmr.common.util :as util]))

;; NOTE -  The following fields are marked as deprecated in the UMM documenation
;; and are therefore not used for keyword searches in the CMR:
;;    SuggestedUsage (ECHO10)
;;
;; TODO - The following field is not mentioned in the UMM documentation. A CMRInbox ticket
;; (CRMIN-28) was created inquiring about it.
;;   :version-description

;; Regex to split strings with special characters into multiple words for keyword searches
(def keywords-separator-regex #"[!@#$%^&()\-=_+{}\[\]|;'.,\"/:<>?`~* ]")

(defn prepare-keyword-field
  [field-value]
  "Convert a string to lowercase then separate it into keywords"
  (when field-value
    (let [field-value (str/lower-case field-value)]
      (into [field-value] (str/split field-value keywords-separator-regex)))))


;; TODO - add :temporal-keyword
(defn create-keywords-field
  [collection]
  "Create a keyword field for keyword searches by concatenating several other fields
  into a single string"
  (let [{:keys [concept-id]} collection
        {{:keys [short-name long-name version-id processing-level-id collection-data-type]} :product
         :keys [entry-id entry-title summary spatial-keywords temporal-keywords associated-difs]} collection
        platforms (:platforms collection)
        platform-short-names (map :short-name platforms)
        platform-long-names (map :long-name platforms)
        instruments (mapcat :instruments platforms)
        instrument-short-names (keep :short-name instruments)
        instrument-long-names (keep :long-name instruments)
        sensors (mapcat :sensors instruments)
        sensor-short-names (keep :short-name sensors)
        sensor-long-names (keep :long-name sensors)
        two-d-coord-names (map :name (:two-d-coordinate-systems collection))
        archive-centers (org/extract-archive-centers collection)
        science-keywords (sk/science-keywords->keywords collection)
        attrib-keywords (attrib/psas->keywords collection)
        all-fields (flatten (conj concept-id
                                  entry-title
                                  collection-data-type
                                  short-name
                                  long-name
                                  two-d-coord-names
                                  summary
                                  version-id
                                  processing-level-id
                                  archive-centers
                                  science-keywords
                                  attrib-keywords
                                  spatial-keywords
                                  temporal-keywords
                                  platform-short-names
                                  platform-long-names
                                  instrument-short-names
                                  instrument-long-names
                                  sensor-short-names
                                  sensor-long-names))
        split-fields (set (mapcat prepare-keyword-field all-fields))]

    (str/join " " split-fields)))
