(ns cmr.common.date-time-parser
  "Contains helper to parse datetime string to clj-time"
  (:require
   [clj-time.format :as time-format]
   [cmr.common.services.messages :as msg])
  (:import org.joda.time.IllegalFieldValueException))

(def datetime-regex->formatter
  "A map of regular expressions matching a date time to the formatter to use"
  {#"^\d\d\d\d-\d\d-\d\d$" (time-format/formatters :date)
   #"^\d\d\d\d$" (time-format/formatters :year)
   #"^[^T]+T[^.]+\.\d+(?:(?:[+-]\d\d:\d\d)|Z)$" (time-format/formatters :date-time)
   #"^[^T]+T[^.]+(?:(?:[+-]\d\d:\d\d)|Z)$" (time-format/formatters :date-time-no-ms)
   #"^[^T]+T[^.]+\.\d+$" (time-format/formatters :date-hour-minute-second-ms)
   #"^[^T]+T[^.]+$" (time-format/formatters :date-hour-minute-second)})

(def time-regex->formatter
  "A map of regular expressions matching a time to the formatter to use"
  {#"^[^.]+\.\d+(?:(?:[+-]\d\d:\d\d)|Z)$" (time-format/formatters :time)
   #"^[^.]+(?:(?:[+-]\d\d:\d\d)|Z)$" (time-format/formatters :time-no-ms)
   #"^[^.]+\.\d+$" (time-format/formatters :hour-minute-second-ms)
   #"^[^.]+$" (time-format/formatters :hour-minute-second)})

(defn find-formatter
  "Finds a given format from a map of regular expressions to formatters to use. Uses the regular
  expression to check if the datetime matches."
  [datetime date-type regex-formatter-map]
  (let [date-format (->> regex-formatter-map
                         (filter (fn [[regex _formatter]]
                                   (re-matches regex datetime)))
                         first
                         second)]
    (when-not date-format
      (msg/data-error :invalid-data msg/invalid-msg date-type datetime))
    date-format))

(defn- make-parser
  "Creates a date parser function"
  [date-type regex-formatter-map]
  (fn [value]
    (try
      (time-format/parse (find-formatter value date-type regex-formatter-map) value)
      (catch IllegalFieldValueException e
        (msg/data-error :invalid-data msg/invalid-msg date-type value (.getMessage e)))
      (catch IllegalArgumentException e
        (msg/data-error :invalid-data msg/invalid-msg date-type value (.getMessage e))))))

(defn- truncate-ms
  "Some dates from providers can contain additional fractional seconds more accurate than a single
  millisecond. This detects if they're present and truncates them so the date can be parsed."
  [^String value]
  (if-let [[_ fractional-secs] (re-matches #"^.*\d\.(\d\d\d\d+)" value)]
    (let [extra-length (- (count fractional-secs) 3)]
      (.substring value 0 (- (count value) extra-length)))
    value))

(defn parse-datetime
  "Parses date times of one of the formats as specified in datetime-regex->formatter"
  [value]
  (let [parser-fn (make-parser :datetime datetime-regex->formatter)]
    (parser-fn (truncate-ms value))))

(def parse-time
  "Parses times of one of the formats as specified in time-regex->formatter"
  (make-parser :time time-regex->formatter))

(defn try-parse-datetime
  "Returns datetime or date parsed from string s if possible, otherwise returns nil."
  [s]
  (try
    (parse-datetime s)
    (catch Exception _
      nil)))

(defn clj-time->date-time-str
  "Returns the string representation for the given joda datetime"
  [dt]
  (time-format/unparse (time-format/formatters :date-time) dt))
