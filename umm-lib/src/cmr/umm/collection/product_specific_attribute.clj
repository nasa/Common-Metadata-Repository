(ns cmr.umm.collection.product-specific-attribute
  (:require [clojure.string :as str]
            [clj-time.format :as f]
            [camel-snake-kebab.core :as csk]
            [cmr.common.services.errors :as errors]))

(defn parse-data-type
  "Parses the string data type from the XML into the keyword data type."
  [data-type]
  (when data-type
    (keyword (csk/->kebab-case data-type))))

(defn gen-data-type
  "Generates the string data type for XML from the keyword data type."
  [data-type]
  (when data-type
    (csk/->SCREAMING_SNAKE_CASE_STRING (name data-type))))

(defmulti parse-value
  "Parses a value based on the data type given"
  (fn [data-type value]
    data-type))

(defmethod parse-value :default
  [data-type value]
  (when value
    (str value)))

(defmethod parse-value :int
  [data-type ^String value]
  (when value (Long. value)))

(defmethod parse-value :float
  [data-type ^String value]
  (when value (Double. value)))

(defmethod parse-value :boolean
  [data-type ^String value]
  (when value
    (case value
      "true" true
      "false" false
      "1" true
      "0" false
      :else (errors/internal-error! (format "Unexpected boolean value [%s]" value)))))

(def datetime-regex->formatter
  "A map of regular expressions matching a date time to the formatter to use"
  {#"^[^T]+T[^.]+\.\d+(?:(?:[+-]\d\d:\d\d)|Z)$" (f/formatters :date-time)
   #"^[^T]+T[^.]+(?:(?:[+-]\d\d:\d\d)|Z)$" (f/formatters :date-time-no-ms)
   #"^[^T]+T[^.]+\.\d+$" (f/formatters :date-hour-minute-second-ms)
   #"^[^T]+T[^.]+$" (f/formatters :date-hour-minute-second)})

(def time-regex->formatter
  "A map of regular expressions matching a time to the formatter to use"
  {#"^[^.]+\.\d+(?:(?:[+-]\d\d:\d\d)|Z)$" (f/formatters :time)
   #"^[^.]+(?:(?:[+-]\d\d:\d\d)|Z)$" (f/formatters :time-no-ms)
   #"^[^.]+\.\d+$" (f/formatters :hour-minute-second-ms)
   #"^[^.]+$" (f/formatters :hour-minute-second)})

(defn find-formatter
  [datetime regex-formatter-map]
  (->> regex-formatter-map
       (filter (fn [[regex formatter]]
                 (re-matches regex datetime)))
       first
       second))

(defmethod parse-value :datetime
  [data-type value]
  (when value
    (f/parse (find-formatter value datetime-regex->formatter) value)))

(defmethod parse-value :time
  [data-type value]
  (when value
    (f/parse (find-formatter value time-regex->formatter) value)))

(defmethod parse-value :date
  [data-type value]
  (when value
    (let [value (str/replace value "Z" "")]
      (f/parse (f/formatters :date) value))))

(defn safe-parse-value
  "Returns the parsed value. It is different from parse-value function in that it will catch any
  parsing exceptions and returns nil when the value is invalid to parse for the given data type."
  [data-type value]
  (try
    (parse-value data-type value)
    (catch Exception _ nil)))

(defmulti gen-value
  "Converts the given value to a string for placement in XML."
  (fn [data-type value]
    data-type))

(defmethod gen-value :default
  [data-type value]
  (when-not (nil? value)
    (str value)))

(defmethod gen-value :time
  [data-type value]
  (when value
    (f/unparse (f/formatters :hour-minute-second-ms) value)))

(defmethod gen-value :date
  [data-type value]
  (when value
    (f/unparse (f/formatters :date) value)))
