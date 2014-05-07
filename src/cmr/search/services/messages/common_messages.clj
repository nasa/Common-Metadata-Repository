(ns cmr.search.services.messages.common-messages
  "Contains messages for reporting responses to the user")

(defn invalid-sort-key
  [sort-key type]
  (format "The sort key [%s] is not a valid field for sorting %ss." sort-key (name type)))