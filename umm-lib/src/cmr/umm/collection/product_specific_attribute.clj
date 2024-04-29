(ns cmr.umm.collection.product-specific-attribute
  (:require
   [camel-snake-kebab.core :as csk]
   [clj-time.format :as f]
   [clojure.string :as string]
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
       (filter (fn [[regex _formatter]]
                 (re-matches regex datetime)))
       first
       second))

(defn parse-value
  "Parses a value based on the data type given"
  [data-type value]
  (when value
    (case data-type
      :int (Long. value)
      :float (Double. value)
      :boolean (case value
                 ("true" "1") true
                 ("0" "false") false
                 (errors/internal-error! (format "Unexpected boolean value [%s]" value)))
      :datetime (f/parse (find-formatter value datetime-regex->formatter) value)
      :time (f/parse (find-formatter value time-regex->formatter) value)
      :date (let [value (string/replace value "Z" "")]
              (f/parse (f/formatters :date) value))
      (str value))))

(defn safe-parse-value
  "Returns the parsed value. It is different from parse-value function in that it will catch any
  parsing exceptions and returns nil when the value is invalid to parse for the given data type."
  [data-type value]
  (try
    (parse-value data-type value)
    (catch Exception _ nil)))

(defn gen-value
  "Converts the given value to a string for placement in XML."
  [data-type value]
  (when (some? value)
    (case data-type
      :time (f/unparse (f/formatters :hour-minute-second-ms) value)
      :date (f/unparse (f/formatters :date) value)
      (str value))))
