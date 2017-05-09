(ns cmr.common-app.services.search.messages
  (:require [camel-snake-kebab.core :as csk]))

(defn invalid-opt-for-param
  "Creates a message saying supplied option is not allowed for parameter."
  [param option]
  (str "Option [" (csk/->snake_case_string option)
       "] is not supported for param [" (csk/->snake_case_string param) "]"))

(defn invalid-sort-key
  [sort-key type]
  (format "The sort key [%s] is not a valid field for sorting %ss." sort-key (name type)))

(defn nil-min-max-msg
  []
  "The min and max values of a numeric range cannot both be nil.")

(defn min-value-greater-than-max
  [min max]
  (format "The maximum value [%f] of the range must be greater than or equal to the minimum value [%f]."
          max min))

(defn invalid-or-opt-setting-msg
  "Creates a message saying which parameter is not allowed to use the 'or' option."
  [param]
  (format "'or' option is not valid for parameter [%s]" param))

(defn invalid-pattern-opt-setting-msg
  "Creates a message saying which parameters would not allow pattern option setting."
  [params-set]
  (let [params (reduce conj '() (seq params-set))]
    (format "Pattern option setting disallowed on these parameters: %s" params)))

(defn invalid-ignore-case-opt-setting-msg
  "Creates a message saying which parameters would not allow ignore case option setting."
  [params-set]
  (let [params (reduce conj '() (seq params-set))]
    (format "Ignore case option setting disallowed on these parameters: %s" params)))

(defn invalid-settings-for-param
  "Creates a message stating that the provided settings for the parameter query are invalid."
  [param settings]
  (format "Invalid settings %s for parameter %s" settings param))
