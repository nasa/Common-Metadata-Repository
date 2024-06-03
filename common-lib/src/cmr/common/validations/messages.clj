(ns cmr.common.validations.messages
  "Validation messages.")

(defn required
  "Something is required"
  []
  "%s is required.")

(defn integer
  "Use an integer, not a float"
  [value]
  (format "%%s must be an integer but was [%s]." value))

(defn datetime
  "Date time is required"
  [value]
  (format "%%s must be a datetime but was [%s]." value))

(defn number
  "Use any kind of number"
  [value]
  (format "%%s must be a number but was [%s]." value))

(defn within-range
  "Value was not between min and max"
  [minv maxv value]
  (format "%%s must be within [%s] and [%s] but was [%s]." minv maxv value))

(defn field-cannot-be-changed
  "Changing a field that can not be changed"
  [existing-value new-value]
  (format
    "%%s cannot be modified. Attempted to change existing value [%s] to [%s]"
    existing-value new-value))
