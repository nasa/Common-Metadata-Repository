(ns cmr.ingest.services.temporal-validation
  "Provides functions to validate the temporal attributes of a collection during its update"
  (:require [cmr.common.util :as util]
            [clj-time.core :as t]
            [cmr.umm.start-end-date :as sed]))

(defn- covers-temporal-range
  "Checks if the temporal range defined by the first pair of arguments covers the temporal range
   defined by the second pair of arguments."
  [start-date-time1 end-date-time1 start-date-time2 end-date-time2]
   (and
     (or (nil? start-date-time1)
         (and start-date-time2
              (t/before? start-date-time1 start-date-time2)))
     (or (nil? end-date-time1)
         (and end-date-time2
              (t/after? end-date-time1 end-date-time2)))))

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
        error-strs (if before? ["earlier" "start"] ["later" "end"])
        error-msg (apply format (str "Found granules %s than collection %s date [%s].")
                         (conj error-strs date-time))]
    {:params params
     :error-msg error-msg}))

(defn out-of-range-temporal-searches
  "Returns the search parameters for identifying granules which fall outside
  the temporal range defined for the collection"
  [concept-id concept prev-concept]
  (let [start-date-time1 (sed/start-date :collection (:temporal concept))
        end-date-time1 (sed/end-date :collection (:temporal concept))
        start-date-time2 (sed/start-date :collection (:temporal prev-concept))
        end-date-time2 (sed/end-date :collection (:temporal prev-concept))]
    (when-not (covers-temporal-range start-date-time1 end-date-time1
                                     start-date-time2 end-date-time2)
      (remove nil?
              [(when start-date-time1 (out-of-bounds-params concept-id start-date-time1 true))
               (when end-date-time1 (out-of-bounds-params concept-id end-date-time1 false))]))))