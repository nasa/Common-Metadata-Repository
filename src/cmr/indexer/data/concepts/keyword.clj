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
;; TODO - The following fields are not mentioned in the UMM documentation and should
;; be inquired about
;;   :version-description


;; TODO - add :temporal-keyword
(defn create-keywords-field
  [collection]
  "Create a keyword field for keyword searches by concatenating several other fields
  into a single string"
  (let [{:keys [concept-id ]} collection
        {{:keys [short-name long-name version-id processing-level-id collection-data-type]} :product
         :keys [entry-id entry-title summary spatial-keywords associated-difs]} collection
        platforms (:platforms collection)
        platform-short-names (map :short-name platforms)
        platform-long-names (map :long-name platforms)
        instruments (mapcat :instruments platforms)
        instrument-short-names (keep :short-name instruments)
        sensors (mapcat :sensors instruments)
        sensor-short-names (keep :short-name sensors)
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
                                       platform-short-names
                                       platform-long-names))
        split-fields (set (mapcat util/prepare-keyword-field all-fields))]
    (str/join " " split-fields)))
