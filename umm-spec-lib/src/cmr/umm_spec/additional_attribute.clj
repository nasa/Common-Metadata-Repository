 (ns cmr.umm-spec.additional-attribute
  "Defines helper functions for parsing additional attributes."
  (:require [cmr.common.xml.parse :refer :all]
            [cmr.common.xml.simple-xpath :refer [select text]]
            [cmr.common.services.errors :as errors]
            [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [clj-time.format :as f]))

(defn parse-data-type
  "Parses the string data type from the XML into the uppercase UMM data type."
  [data-type]
  (when data-type
    (str/upper-case data-type)))

(defn gen-data-type
  "Generates the string data type for errors from the keyword data type."
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

(defmethod parse-value "INT"
  [data-type ^String value]
  (when value (Long. value)))

(defmethod parse-value "FLOAT"
  [data-type ^String value]
  (when value (Double. value)))

(defmethod parse-value "BOOLEAN"
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

(defmethod parse-value "DATETIME"
  [data-type value]
  (when value
    (f/parse (find-formatter value datetime-regex->formatter) value)))

(defmethod parse-value "TIME"
  [data-type value]
  (when value
    (f/parse (find-formatter value time-regex->formatter) value)))

(defmethod parse-value "DATE"
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

(defn- add-parsed-value-for
  "Parses the value at the source field (if parseable) and associates it with the additional attribute
   using the given destination field."
  [aa source-field dest-field]
  (if-let [v (safe-parse-value (:DataType aa) (get aa source-field))]
    (assoc aa dest-field v)
    aa))

(defn attribute-with-parsed-value
  "Adds a parsed-value keyword to the additional attribute based on the data type and value in the map."
  [aa]
  (-> aa
      (add-parsed-value-for :Value ::parsed-value)
      (add-parsed-value-for :ParameterRangeBegin ::parsed-parameter-range-begin)
      (add-parsed-value-for :ParameterRangeEnd ::parsed-parameter-range-end)))

(defn add-parsed-values
  "Adds additional attribute parsed values to the additional attributes of the UMM record."
  [umm-c]
  (update umm-c :AdditionalAttributes #(mapv attribute-with-parsed-value %)))

(defmulti gen-value
  "Converts the given value to a string for error messages."
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

(defmethod gen-value "TIME" 
  [data-type value]
  (when value
    (f/unparse (f/formatters :hour-minute-second-ms) value)))

(defmethod gen-value :date
  [data-type value]
  (when value
    (f/unparse (f/formatters :date) value)))

(defmethod gen-value "DATE" 
  [data-type value]
  (when value
    (f/unparse (f/formatters :date) value)))
