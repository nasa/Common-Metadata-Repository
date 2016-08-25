(ns cmr.umm-spec.date-util
  "Useful UMM date values and functions."
  (:require [clj-time.format :as f]
            [cmr.common.date-time-parser :as p]
            [cmr.common.xml.parse :refer :all]
            [cmr.umm-spec.models.umm-common-models :as cmn]))

(def default-date-value "1970-01-01T00:00:00")

(def parsed-default-date
  (p/parse-datetime default-date-value))


(defn with-default
  "Returns x if not nil, or else the default date placeholder value."
  [x]
  (or x default-date-value))

(defn without-default
  "Returns x if it is not the default date value string."
  [x]
  (when (not= x default-date-value)
    x))

(defn or-default-date
  "Returns x if not nil, or else the default date placeholder date value"
  [x]
  (or x (f/parse (f/formatters :date-hour-minute-second) default-date-value)))

(defn parse-date-type-from-xml
  "Get the date (yyyy-MM-dd) at the location in the doc and parse it into a UMM DateType"
  [doc date-location type]
  (let [date (value-of doc date-location)]
    (when (some? date)
      (cmn/map->DateType {:Date (f/parse date)
                          :Type type}))))

(defn latest-date-of-type
  "Returns :Date value of the most recent UMM DateType map in date-coll with the given type."
  [date-coll date-type]
  (->> date-coll
       (filter #(= date-type (:Type %)))
       (map :Date)
       sort
       last))

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
