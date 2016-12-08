(ns cmr.search.test.data.temporal-ranges-to-elastic
  "Contains test to verify the functions that get the temporal ranges for the elastic search
  temporal relevancy sort script"
 (:require
  [clj-time.coerce :as time-coerce]
  [clj-time.core :as time]
  [clojure.test :refer :all]
  [cmr.common.util :as util :refer [are3]]
  [cmr.search.data.temporal-ranges-to-elastic :as temporal-to-elastic]))

;; Temporal range input is a map of :start-date :end-date
;; Temporal ranges in elastic should have values for nil start or end dates, have the date
;; range calculated, and params should be in snake case

(deftest temporal-ranges-to-elastic
  (are3 [temporal-range elastic-temporal-range]
    (is (= elastic-temporal-range
           (temporal-to-elastic/temporal-range->elastic-param temporal-range)))

    "Both start and end date"
    {:start-date (time/date-time 2014 01 01)
     :end-date (time/date-time 2016 01 01)}
    {:range 63072000000
     :end_date (time-coerce/to-long (time/date-time 2016 01 01))
     :start_date (time-coerce/to-long (time/date-time 2014 01 01))}

    "Nil end date"
    {:start-date (time/date-time 2014 01 01)
     :end-date nil}
    {:range (- (time-coerce/to-long (time/today)) (time-coerce/to-long (time/date-time 2014 01 01)))
     :end_date (time-coerce/to-long (time/today))
     :start_date (time-coerce/to-long (time/date-time 2014 01 01))}

    "Nil start date"
    {:start-date nil
     :end-date (time/date-time 2016 01 01)}
    {:range 1451606400000
     :end_date (time-coerce/to-long (time/date-time 2016 01 01))
     :start_date (time-coerce/to-long (time/date-time 1970 01 01))}))
