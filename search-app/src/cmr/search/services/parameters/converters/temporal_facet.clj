(ns cmr.search.services.parameters.converters.temporal-facet
  "Contains functions for converting temporal facets query parameters to conditions"
  (:require
   [clj-time.core :as clj-time]
   [clojure.string :as str]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.parameters.converters.nested-field :as nf]
   [cmr.common-app.services.search.params :as p]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.common.date-time-parser :as parser]))

(defn- temporal-facet-map->clj-time-interval
  "Returns the next temporal interval based on the values in the map."
  [temporal-facet-map]
  (let [all-keys (set (keys temporal-facet-map))]
    (if-not (contains? all-keys :year)
      nil
      (if-not (contains? all-keys :month)
        (clj-time/years 1)
        (if-not (contains? all-keys :day)
          (clj-time/months 1)
          (if-not (contains? all-keys :hour)
            (clj-time/days 1)
            (clj-time/hours 1)))))))

(defn- get-date-ranges
  "Returns a tuple of start date and end date based on the passed in time range map."
  [{:keys [year month day hour] :as temporal-facet-map}]
  (let [start-date-string (when year
                            (format "%s-%s-%sT%s:00:00.000Z"
                                    year (or month "01") (or day "01") (or hour "00")))
        start-date (when start-date-string (parser/parse-datetime start-date-string))
        interval (temporal-facet-map->clj-time-interval temporal-facet-map)
        end-date (when start-date
                   (clj-time/minus (clj-time/plus start-date interval) (clj-time/millis 1)))]
    [start-date end-date]))

(defn- parse-temporal-facet-condition
  "Converts a temporal facet parameter into a query model condition."
  [temporal-facet-map]
  (let [[start-date end-date] (get-date-ranges temporal-facet-map)]
    (qm/map->DateRangeCondition {:field :start-date
                                 :start-date start-date
                                 :end-date end-date})))

(defmethod p/parameter->condition :temporal-facet
  [context concept-type param temporal-facet options]
  (let [group-operation (p/group-operation param options :or)]
    (if (map? (first (vals temporal-facet)))
      ;; If multiple temporal facets are passed in like the following
      ;;  -> temporal_facet[0][year]=2009&temporal_facet[1][year]=2010
      ;; then this recurses back into this same function to handle each separately
      (gc/group-conds
        group-operation
        (map #(p/parameter->condition context concept-type param % options)
             (vals temporal-facet)))
      ;; Creates the temporal facet condition for a group of temporal facet fields and temporal-facet-maps.
      (parse-temporal-facet-condition temporal-facet))))
