(ns cmr.search.services.messages.attribute-messages
  "Contains messages for reporting responses to the user")

(defn invalid-num-parts-msg
  []
  (str "Invalid number of additional attribute parts. "
       "Format is \"type,name,value\" or \"type,name,min,max\"."))

(defn invalid-type-msg
  [type]
  (format "[%s] is an invalid type" (str type)))

(defn invalid-value-msg
  [type value]
  (format "[%s] is an invalid value for type [%s]" (str value) (name type)))

(defn invalid-name-msg
  [n]
  (format "[%s] is not a valid name for an attribute." (str n)))

(defn one-of-min-max-msg
  []
  "At least one of min or max must be provided for an additional attribute search.")

(defn max-must-be-greater-than-min-msg
  [minv maxv]
  (format "The maximum value [%s] must be greater than the minimum value [%s]" maxv minv))

(defn attributes-must-be-sequence-msg
  []
  "'attribute' is not a valid parameter. You must use 'attribute[]'.")