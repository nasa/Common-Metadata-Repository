(ns cmr.common.parameter-parser
  "Contains helper functions to parse parameter strings."
  (:require [cmr.common.services.errors :as errors]
            [cmr.common.services.messages :as msg]))


(defn numeric-range-parameter->map
  "Parses a numeric range parameter in in the form 'value' or 'min-value,max-value' where
  min-value or max-value are optional for open-ended ranges. Returns a map of the form
  '{:value value, :min-value min-value, :max-value max-value}'."
  [param-str]
  (try
    (if-let [[_ ^java.lang.String start ^java.lang.String stop] (re-find #"^(.*),(.*)$" ons)]
      {:min-value (when (not (empty? start)) (Double. start))
       :max-value (when (not (empty? stop)) (Double. stop))}
      {:value (Double. ons)})
    (catch NumberFormatException e
      (msg/data-error :invalid-data msg/invalid-numeric-range-msg param-str))))

(defn numeric-range-string-validator
  "Vaidates that a numeric range string is of the format 'value' or 'min-value,max-value'
  where min-value/max-value are optional (but at least one must be present)."
  [range-str]
  (try
    (let [range-map (numeric-range-parameter->map range-str)]
      (if (empty? (filter second range-map))
        [(msg/invalid-numeric-range-msg param-str)]
        []))
    (catch clojure.lang.ExceptionInfo e
      [(msg/invalid-numeric-range-msg param-str)])))