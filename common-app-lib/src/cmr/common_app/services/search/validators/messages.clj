(ns cmr.common-app.services.search.validators.messages
  "Contains messages related to query validation")

(defn nil-min-max-msg
  []
  "The min and max values of a numeric range cannot both be nil.")

(defn min-value-greater-than-max
  [min max]
  (format "The maximum value [%f] of the range must be greater than or equal to the minimum value [%f]." max min))