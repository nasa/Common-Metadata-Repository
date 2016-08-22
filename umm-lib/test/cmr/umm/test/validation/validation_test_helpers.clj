(ns cmr.umm.test.validation.validation-test-helpers
  (:require [cmr.common.date-time-parser :as dtp]
            [cmr.umm.umm-collection :as c]))

(defn range-date-time
  "Returns a temporal range map given beginning and end date strings.

  Example: (range-date-time \"1999-12-30T19:00:00Z\" \"1999-12-30T19:00:01Z\")"
  [begin-date-time end-date-time]
  (let [begin-date-time (when begin-date-time (dtp/parse-datetime begin-date-time))
        end-date-time (when end-date-time (dtp/parse-datetime end-date-time))]
    (c/map->RangeDateTime
      {:beginning-date-time begin-date-time
       :ending-date-time end-date-time})))

(defn coll-with-range-date-times
  "Returns a collection with the given temporal ranges."
  ([range-date-times]
   (coll-with-range-date-times range-date-times nil))
  ([range-date-times ends-at-present?]
   c/map->UmmCollection
   {:temporal (c/map->Temporal {:range-date-times range-date-times
                                :ends-at-present-flag ends-at-present?})
    :entry-title "et"}))
