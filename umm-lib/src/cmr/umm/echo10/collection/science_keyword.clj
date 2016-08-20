(ns cmr.umm.echo10.collection.science-keyword
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.generator-util :as gu]))

(defn xml-elem->ScienceKeyword
  [science-keyword-elem]
  (let [category (cx/string-at-path science-keyword-elem [:CategoryKeyword])
        topic (cx/string-at-path science-keyword-elem [:TopicKeyword])
        term (cx/string-at-path science-keyword-elem [:TermKeyword])
        variable-level-1 (cx/string-at-path science-keyword-elem [:VariableLevel1Keyword :Value])
        variable-level-2 (cx/string-at-path science-keyword-elem [:VariableLevel1Keyword :VariableLevel2Keyword :Value])
        variable-level-3 (cx/string-at-path science-keyword-elem [:VariableLevel1Keyword :VariableLevel2Keyword :VariableLevel3Keyword])
        detailed-variable (cx/string-at-path science-keyword-elem [:DetailedVariableKeyword])]
    (c/map->ScienceKeyword
      {:category category
       :topic topic
       :term term
       :variable-level-1 variable-level-1
       :variable-level-2 variable-level-2
       :variable-level-3 variable-level-3
       :detailed-variable detailed-variable})))

(defn xml-elem->ScienceKeywords
  [collection-element]
  (seq (map xml-elem->ScienceKeyword
            (cx/elements-at-path
              collection-element
              [:ScienceKeywords :ScienceKeyword]))))

(defn generate-science-keywords
  [science-keywords]
  (when-not (empty? science-keywords)
    (x/element
      :ScienceKeywords {}
      (for [science-keyword science-keywords]
        (let [{:keys [category topic term variable-level-1 variable-level-2 variable-level-3 detailed-variable]} science-keyword]
          (x/element :ScienceKeyword {}
                     (x/element :CategoryKeyword {} category)
                     (x/element :TopicKeyword {} topic)
                     (x/element :TermKeyword {} term)
                     (when (some? variable-level-1)
                       (x/element :VariableLevel1Keyword {}
                                  (x/element :Value {} variable-level-1)
                                  (when (some? variable-level-2)
                                    (x/element :VariableLevel2Keyword {}
                                               (x/element :Value {} variable-level-2)
                                               (when (some? variable-level-3)
                                                 (x/element :VariableLevel3Keyword {} variable-level-3))))))
                     (gu/optional-elem :DetailedVariableKeyword detailed-variable)))))))
