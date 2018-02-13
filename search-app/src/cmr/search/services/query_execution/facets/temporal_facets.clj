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

(defn temporal-facet
  "Creates a temporal facet for the provided field."
  [interval-granularity]
  (let [interval-granularity :year]
    {:date_histogram
     {:field :start-date-doc-values
      :interval interval-granularity}}))
