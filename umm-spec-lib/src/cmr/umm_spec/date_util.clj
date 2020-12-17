(ns cmr.umm-spec.date-util
  "Useful UMM date values and functions."
  (:require
   [clj-time.format :as f]
   [clj-time.core :as time-core]
   [clojure.string :as str]
   [cmr.common.date-time-parser :as p]
   [cmr.common.time-keeper :as time-keeper]
   [cmr.common.xml.parse :refer :all]
   [cmr.umm-spec.models.umm-common-models :as cmn]))

(def default-date-value "1970-01-01T00:00:00")

(def parsed-default-date
  (p/parse-datetime default-date-value))

(defn with-default
  "Returns x if not nil, or else the default date placeholder value."
  ([x]
   (or x default-date-value))
  ([x sanitize?]
   (if sanitize?
     (or x default-date-value)
     x)))

(defn with-current
  "Returns x if not nil, or else the current-date-time placeholder value."
  ([x]
   (or x (time-keeper/now))))

(defn without-default
  "Returns x if it is not the default date value string."
  [x]
  (when (not= x default-date-value)
    x))

(defn use-default-when-not-provided
  "Returns default date value string if x = 'Not provided'"
  [x util-not-provided]
  (if (= x util-not-provided)
    default-date-value
    x))

(defn with-default-date
  "Returns x if not nil, or else the default date placeholder date value"
  [x]
  (or x (f/parse (f/formatters :date-hour-minute-second) default-date-value)))

(defn parse-date-type-from-xml
  "Get the date at the location in the doc and parse it into a UMM DateType"
  [doc date-location type]
  (when-let [date (value-of doc date-location)]
    (cmn/map->DateType {:Date (f/parse (str/trim date))
                        :Type type})))

(defn latest-date-of-type
  "Returns :Date value of the most recent UMM DateType map in date-coll with the given type."
  [date-coll date-type]
  (->> date-coll
       (filter #(= date-type (:Type %)))
       (map :Date)
       sort
       last))

(defn earliest-date-of-type
  "Returns :Date value of the least recent UMM DateType map in date-coll with the given type."
  [date-coll date-type]
  (->> date-coll
       (filter #(= date-type (:Type %)))
       (map :Date)
       sort
       first))

(defn sanitize-and-parse-date
  "If sanitize? is enabled make corrections in the date string then parse. If the date string
  cannot be parsed, the f/parse function will return nil. Return the original date instead
  of nil to get error messages for invalid dates. Parse and unparse the date so the date string
  can be processed."
  [date sanitize?]
  (if sanitize?
    (if-let [parsed-date (some-> date
                                 (str/replace "/" "-")
                                 f/parse)]
      (p/clj-time->date-time-str parsed-date)
      date)
    date))

(defn valid-date?
  "When date is valid, return true, otherwise return nil"
  [date]
  (let [date (f/parse date)]
    (try
      (time-core/equal? date
                     date)
      (catch
        Exception e nil))))

(defn is-in-past?
  "Parse date and return true if date is in the past, false if not or date is nil"
  [date]
  (if date
    (time-core/before? date (time-keeper/now))
    false))

(defn is-in-future?
  "Parse date and return true if date is in the future, false if not or date is nil"
  [date]
  (if date
    (time-core/after? date (time-keeper/now))
    false))

(defn update-metadata-dates
  "Update the Date to current date, for a given Type: date-type, in MetadataDates of a umm record"
  [umm date-type]
  (let [new-metadata-dates (concat (remove #(= date-type (:Type %)) (:MetadataDates umm))
                                   [{:Date (time-keeper/now) :Type date-type}])]
    (assoc umm :MetadataDates new-metadata-dates)))

(defn- data-date-getter
  [date-type]
  (fn [c]
    (-> c :DataDates (latest-date-of-type date-type))))

(defn- metadata-date-getter
  [date-type]
  (fn [c]
    (-> c :MetadataDates (latest-date-of-type date-type))))

(def data-create-date (data-date-getter "CREATE"))

(def data-update-date (data-date-getter "UPDATE"))

(def data-review-date (data-date-getter "REVIEW"))

(def data-delete-date (data-date-getter "DELETE"))

(def metadata-create-date (metadata-date-getter "CREATE"))

(def metadata-update-date (metadata-date-getter "UPDATE"))

(def metadata-review-date (metadata-date-getter "REVIEW"))

(def metadata-delete-date (metadata-date-getter "DELETE"))
