(ns cmr.search.services.messages.science-keyword-messages
  "Contains messages for reporting responses to the user")

(defn science-keyword-invalid-format-msg
  []
  "Parameter science_keywords is invalid, should be in the format of science_keywords[0/group number (if multiple groups are present)][category/topic/term/variable_level_1/variable_level_2/variable_level_3/detailed_variable].")