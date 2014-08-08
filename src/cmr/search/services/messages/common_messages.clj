(ns cmr.search.services.messages.common-messages
  "Contains messages for reporting responses to the user")

(defn invalid-aql
  [msg]
  (str "AQL Query Syntax Error: " msg))

(defn invalid-sort-key
  [sort-key type]
  (format "The sort key [%s] is not a valid field for sorting %ss." sort-key (name type)))

(defn science-keyword-invalid-format-msg
  []
  (str "Parameter science_keywords is invalid, "
       "should be in the format of science_keywords[0/group number (if multiple groups are present)]"
       "[category/topic/term/variable_level_1/variable_level_2/variable_level_3/detailed_variable]."))