(ns cmr.common.parameter-parser
  "Contains helper functions to parse parameter strings."
  (:require [cmr.common.services.errors :as errors]
            [cmr.common.services.messages :as msg]
            [cmr.common.date-time-parser :as dtp]
            [cmr.common.date-time-range-parser :as dtrp]))

(defn range-parameter->map
  "Parses a range parameter in the form 'value' or 'min-value,max-value' where
  min-value and max-value are optional and returns a map with :value, :min-
  value, and :max-value as keys."
  [param-str]
  (if-let [[_ min-value max-value] (re-find #"^(.*),(.*)$" param-str)]
    ;; param-str is of min,max form
    (merge {}
           (when (seq min-value) {:min-value min-value})
           (when (seq max-value) {:max-value max-value}))
    ;; entire param-str is a value
    {:value param-str}))

(defn numeric-range-parameter->map
  "Parses a numeric range parameter in in the form 'value' or 'min-value,max-
  value' where min-value or max-value are optional for open-ended ranges.
  Returns a map of the form '{:value value, :min-value min-value, :max-value
  max-value}'."
  [^String param-str]
  (try
    (let [value-map (if-let [[_ ^String start ^String stop]
                             (re-find #"^(.*),(.*)$" param-str)]
                      {:min-value (when (not (empty? start)) (Double. start))
                       :max-value (when (not (empty? stop)) (Double. stop))}
                      {:value (java.lang.Double. param-str)})]
      (into {} (filter second value-map))) ; remove nil values
    (catch NumberFormatException e
      (msg/data-error :invalid-data msg/invalid-numeric-range-msg param-str))))

(defn numeric-range-string-validation
  "Vaidates that a numeric range string is of the format 'value' or 'min-value
  ,max-value' where min-value/max-value are optional (but at least one must be
  present)."
  [range-str]
  (let [value-map (range-parameter->map range-str)]
    (if-not (empty? value-map)
      ;; validatge the number strings
      (keep (fn [^java.lang.String value]
             (try
               (java.lang.Double. value)
               nil
               (catch NumberFormatException e
                 (msg/invalid-msg Double value))))
           (vals value-map))
      [(msg/invalid-numeric-range-msg range-str)])))

(defn date-time-range-string-validation
  "Validates that a date-time range string is of the format 'value' or 'min-
  value,max-value' where min-value/max-value are optional (but at least one
  must be present.)."
  [range-str]
  (if-not (empty? range-str)
    (try
        (dtrp/parse-datetime-range range-str)
        nil
      (catch clojure.lang.ExceptionInfo e
        [(.getMessage e)]))
    [(msg/invalid-date-range-msg range-str)]))
