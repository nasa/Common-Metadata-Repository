(ns cmr.umm.dif10.collection.science-keyword
  "Provide functions to parse and generate DIF10 Science Keyword elements."
  (:require
   [clojure.data.xml :as xml]
   [cmr.common.xml :as cx]
   [cmr.umm.umm-collection :as coll]
   [cmr.umm.generator-util :as gu]))

(defn xml-elem->ScienceKeyword
  [science-keyword-elem]
  (coll/map->ScienceKeyword
    {:category (cx/string-at-path science-keyword-elem [:Category])
     :topic (cx/string-at-path science-keyword-elem [:Topic])
     :term (cx/string-at-path science-keyword-elem [:Term])
     :variable-level-1 (cx/string-at-path science-keyword-elem [:Variable_Level_1])
     :variable-level-2 (cx/string-at-path science-keyword-elem [:Variable_Level_2])
     :variable-level-3 (cx/string-at-path science-keyword-elem [:Variable_Level_3])
     :detailed-variable (cx/string-at-path science-keyword-elem [:Detailed_Variable])}))

(defn xml-elem->ScienceKeywords
  [collection-element]
  (seq (map xml-elem->ScienceKeyword
            (cx/elements-at-path
              collection-element
              [:Science_Keywords]))))

(defn generate-science-keywords
  [science-keywords]
  (if (seq science-keywords)
    (for [{:keys [category topic term variable-level-1
                  variable-level-2 variable-level-3 detailed-variable]} science-keywords]
      (xml/element :Science_Keywords {}
                 (xml/element :Category {} category)
                 (xml/element :Topic {} topic)
                 (xml/element :Term {} term)
                 (gu/optional-elem :Variable_Level_1 variable-level-1)
                 (gu/optional-elem :Variable_Level_2 variable-level-2)
                 (gu/optional-elem :Variable_Level_3 variable-level-3)
                 (gu/optional-elem :Detailed_Variable detailed-variable)))
    ;; Added since Science Keywords is a required field in DIF10. CMRIN-79
    (xml/element :Science_Keywords {}
               (xml/element :Category {} coll/not-provided)
               (xml/element :Topic {} coll/not-provided)
               (xml/element :Term {} coll/not-provided))))
