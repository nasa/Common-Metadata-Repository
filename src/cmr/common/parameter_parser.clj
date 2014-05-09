(ns cmr.common.parameter-parser
  "Contains helper functions to parse parameter strings."
  (:require [cmr.common.services.errors :as errors]
            [cmr.common.services.messages :as msg]
            [cmr.common.date-time-parser :as dtp]))

(defn numeric-range-parameter->map
  "Parses a numeric range parameter in in the form 'value' or 'min-value,max-value' where
  min-value or max-value are optional for open-ended ranges. Returns a map of the form
  '{:value value, :min-value min-value, :max-value max-value}'."
  [^String param-str]
  (try
    (let [value-map (if-let [[_ ^String start ^String stop] (re-find #"^(.*),(.*)$"
                                                                                         param-str)]
                      {:min-value (when (not (empty? start)) (Double. start))
                       :max-value (when (not (empty? stop)) (Double. stop))}
                      {:value (Double. param-str)})]
      (into {} (filter second value-map))) ; remove nil values
    (catch NumberFormatException e
      (msg/data-error :invalid-data msg/invalid-numeric-range-msg param-str))))

(defn numeric-range-string-validation
  "Vaidates that a numeric range string is of the format 'value' or 'min-value,max-value'
  where min-value/max-value are optional (but at least one must be present)."
  [range-str]
  (try
    (let [range-map (numeric-range-parameter->map range-str)]
      (if (empty? (filter second range-map))
        [(msg/invalid-numeric-range-msg range-str)]
        []))
    (catch clojure.lang.ExceptionInfo e
      [(msg/invalid-numeric-range-msg range-str)])))

(defn date-time-range-parameter->map
  "Parses a date range parameter in the form of 'value' or 'min-value,max-value'.
  Returns a map of the form {:date date, :start-date start-date, :end-date end-date}
  where start-date and end-date are dates parsed from min-value/max-value."
  [param-str]

  (let [value-map (if-let [[_ ^java.lang.String start ^java.lang.String stop] (re-find #"^(.*),(.*)$"
                                                                                       param-str)]
                    {:start-date (when (not (empty? start)) (dtp/parse-datetime start))
                     :end-date (when (not (empty? stop)) (dtp/parse-datetime stop))}
                    {:date (dtp/parse-datetime param-str)})]
    (into {} (filter second value-map)))) ; filter nils

(defn date-time-range-string-validation
  "Validates that a date-time range string is f the format 'value' or 'min-value,max-value'
  where min-value/max-value are optional (but at least one must be present.)."
  [range-str]
  (try
    (let [range-map (date-time-range-parameter->map range-str)]
      (if (empty? (filter second range-map))
        [(msg/invalid-date-range-msg range-str)]
        []))
    (catch clojure.lang.ExceptionInfo e
      [(msg/invalid-date-range-msg range-str)])))

