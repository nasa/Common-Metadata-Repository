(ns cmr.indexer.data.concepts.science-keyword-util
  "Contains functions to help dealing with UMM ScienceKeyowrds.")

(defn science-keyword->keywords
  "Converts a science keyword into a vector of terms for keyword searches"
  [science-keyword]
  (let [{category :Category topic :Topic term :Term variable-level-1 :VariableLevel1
         variable-level-2 :VariableLevel2 variable-level-3 :VariableLevel3
         detailed-variable :DetailedVariable} science-keyword]
    [category topic term variable-level-1 variable-level-2 variable-level-3 detailed-variable]))
