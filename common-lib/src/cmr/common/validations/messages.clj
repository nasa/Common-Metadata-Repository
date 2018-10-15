(ns cmr.common.validations.messages)

(defn required
  []
  "%s is required.")

(defn integer
  [value]
  (format "%%s must be an integer but was [%s]." value))

(defn datetime 
  [value]
  (format "%%s must be a datetime but was [%s]." value))

(defn number
  [value]
  (format "%%s must be a number but was [%s]." value))

(defn within-range
  [minv maxv value]
  (format "%%s must be within [%s] and [%s] but was [%s]." minv maxv value))

(defn field-cannot-be-changed
  [existing-value new-value]
  (format
    "%%s cannot be modified. Attempted to change existing value [%s] to [%s]"
    existing-value new-value))
