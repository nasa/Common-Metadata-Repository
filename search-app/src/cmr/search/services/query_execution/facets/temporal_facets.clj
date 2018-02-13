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

(defn- time-intervals->next-interval
  "Returns the next interval based on the values in the map."
  [time-intervals]
  ; (println "My time intervals are" time-intervals)
  (if-not (contains? (set time-intervals) :year)
    :year
    :month))

(defn- get-subfields
  "Returns the set of subfields within all of the provided temporal_facet params."
  [temporal-facet-params]
  ; (println "temporal-facet-params" temporal-facet-params)
  (let [field-regex (re-pattern "temporal_facet\\[\\d+\\]\\[(.*)\\]")]
    (keep #(second (re-matches field-regex %)) temporal-facet-params)))

(defn query-params->time-interval
  "Returns the time interval to request in temporal facets based on the query parameters."
  [query-params]
  (let [field-reg-ex (re-pattern "temporal_facet.*")
        temporal-facet-params (keep (fn [[k v]]
                                      (when (re-matches field-reg-ex k) k))
                                    query-params)]
    ; (println "Subfields are:" (get-subfields temporal-facet-params))
    (time-intervals->next-interval (map keyword (get-subfields temporal-facet-params)))))

(defn temporal-facet
  "Creates a temporal facet for the provided field."
  [query-params]
  (let [interval-granularity (query-params->time-interval query-params)]
    ; (println "Interval granularity is:" interval-granularity)
    {:date_histogram
     {:field :start-date-doc-values
      :interval interval-granularity}}))
