(ns cmr.search.services.query-execution.facets.temporal-facets
  "Functions for generating the temporal facets within v2 granule facets.")

(defmulti parse-date
  "Returns the value from the date string that matches the provided interval.
  Example: (parse-date \"2017-01-01T00:00:00+0000\" :year) returns 2017."
  (fn [datetime interval]
    interval))

(defmethod parse-date :year
  [datetime interval]
  (second (re-find #"^(\d{4})-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\+\d+$" datetime)))

(defmethod parse-date :month
  [datetime interval]
  (second (re-find #"^\d{4}-(\d{2})-\d{2}T\d{2}:\d{2}:\d{2}\+\d+$" datetime)))

(defmethod parse-date :day
  [datetime interval]
  (second (re-find #"^\d{4}-\d{2}-(\d{2})T\d{2}:\d{2}:\d{2}\+\d+$" datetime)))

(defmethod parse-date :hour
  [datetime interval]
  (second (re-find #"^\d{4}-\d{2}-\d{2}T(\d{2}):\d{2}:\d{2}\+\d+$" datetime)))

; (defn validate-year
;   "Validates a given value is a valid year."
;   [year]
;   (let [parsed-year]
;     (try
;       (java.lang.Double. value)
;       nil
;       (catch NumberFormatException e
;         (msg/invalid-msg Double value)))))

(defn temporal-facet
  "Creates a temporal facet for the provided field."
  [field interval-granularity]
  (let [interval-granularity :year]
    {:date_histogram
     {:field field
      :interval interval-granularity}}))

(defn parse-temporal-buckets
  "Parses the Elasticsearch aggregations response to return a map of the value for the current
  interval and count of the number of documents for that interval."
  [buckets interval]
  (reverse
   (map (fn [bucket]
          (let [value (parse-date (:key_as_string bucket) interval)]
            {:title value
             :count (:doc_count bucket)}))
        buckets)))
