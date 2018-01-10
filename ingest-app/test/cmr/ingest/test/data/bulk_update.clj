(ns cmr.ingest.test.data.bulk-update
  "This tests functions in bulk update data"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as u :refer [are3]]
    [cmr.ingest.data.bulk-update :as bulk-update]))

(deftest generate-task-status-message
  (are3 [failed-collections skipped-collections total-collections message]
    (is (= message
          (bulk-update/generate-task-status-message failed-collections skipped-collections total-collections)))

    "Completed successfully"
    0 0 10 "All collection updates completed successfully."

    "failures only"
    10 0 10 "Task completed with 10 FAILED out of 10 total collection update(s)."

    "skips only"
    0 10 10 "Task completed with 10 SKIPPED out of 10 total collection update(s)."

    "failures and skips"
    5 5 10 "Task completed with 5 FAILED and 5 SKIPPED out of 10 total collection update(s)."    

    "failures, skips and updates"
    5 2 10 "Task completed with 5 FAILED, 2 SKIPPED and 3 UPDATED out of 10 total collection update(s)."

    "skips and updates"
    0 2 10 "Task completed with 2 SKIPPED and 8 UPDATED out of 10 total collection update(s)."

    "failures and updates"
    2 0 10 "Task completed with 2 FAILED and 8 UPDATED out of 10 total collection update(s)."))
