(ns cmr.umm.temporal-coverage
  "Provides functions for temporal coverage construction"
  (:require [cmr.umm.collection :as c]))

;; default temporal date times map
(def temporal-date-times {:range-date-times []
                          :single-date-times []
                          :periodic-date-times []})

(defn validate-temporal-map
  "Validates the given temporal map, throws Exception if more than one of the temporal datetimes is provided"
  [temporal-map]
  (let [provided-key-set (into #{} (keys temporal-map))
        temporal-key-set (into #{} (keys temporal-date-times))
        provided-temporal (clojure.set/intersection provided-key-set temporal-key-set)]
    ;; It's a toss-up if we want to check if the provided temporal datetimes values is nil or empty, and handles that automatically
    ;; for now, we only check the provided keys
    (when (> (count provided-temporal) 1)
      (throw (Exception. "Only one of range-date-times, single-date-times and periodic-date-times can be provided.")))))

(defn temporal-coverage
  "Constructs a temporal coverage record with the given map, verifies only one of the temporal datetimes can exist."
  [temporal-map]
  (validate-temporal-map temporal-map)
  (c/map->TemporalCoverage (merge temporal-date-times temporal-map)))
