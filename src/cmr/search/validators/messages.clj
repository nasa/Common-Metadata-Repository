(ns cmr.search.validators.messages
  "Contains messages for reporting responses to the user")

(defn min-value-greater-than-max
  [min max]
  (format "The maximum value [%f] of the range must be greater than or equal to the minimum value [%f]." max min))