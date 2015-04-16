(ns cmr.ingest.services.temporal-validation
  "Provides functions to validate the temporal attributes of a collection during its update"
  (:require [cmr.common.util :as util]
            [clj-time.core :as t]
            [cmr.umm.start-end-date :as sed]))

(defn- covers-temporal-range?
  "Checks if the temporal range defined by the first pair of arguments covers the temporal range
   defined by the second pair of arguments."
  [updated-start-time updated-end-time prev-start-time prev-end-time]
   (and
     (or (nil? updated-start-time)
         (and prev-start-time
              (not (t/after? updated-start-time prev-start-time))))
     (or (nil? updated-end-time)
         (and prev-end-time
              (not (t/before? updated-end-time prev-end-time))))))

(defn- out-of-bounds-params
  "Returns a map containing search parameters to determine granules for the collection with
  the given concept-id and falling before date-time if before? is true and after if before?
  is false. The output map also includes the error message which would be thrown if the
  query with the given parameter returns hits greater than 0."
  [concept-id date-time before?]
  (let [temporal-param (if before? (str \, date-time) (str date-time \,))
        params {"temporal[]" temporal-param
                :collection-concept-id concept-id
                "options[temporal][exclude_boundary]" true}
        error-msg (format (str "Found granules %s than collection %s date [%s].")
                          (if before? "earlier" "later")
                          (if before? "start" "end")
                          date-time)]
    {:params params
     :error-msg error-msg}))

(defn out-of-range-temporal-searches
  "Returns the search parameters for identifying granules which fall outside
  the temporal range defined for the collection"
  [concept-id concept prev-concept]
  (let [updated-start-time (sed/start-date :collection (:temporal concept))
        updated-end-time (sed/end-date :collection (:temporal concept))
        prev-start-time (sed/start-date :collection (:temporal prev-concept))
        prev-end-time (sed/end-date :collection (:temporal prev-concept))]
    (when-not (covers-temporal-range? updated-start-time updated-end-time
                                     prev-start-time prev-end-time)
      (remove nil?
              [(when updated-start-time (out-of-bounds-params concept-id updated-start-time true))
               (when updated-end-time (out-of-bounds-params concept-id updated-end-time false))]))))