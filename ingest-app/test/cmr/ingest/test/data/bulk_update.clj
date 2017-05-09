(ns cmr.ingest.test.data.bulk-update
  "This tests functions in bulk update data"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as u :refer [are3]]
    [cmr.ingest.data.bulk-update :as bulk-update]))

(deftest generate-task-status-message
  (are3 [failed-collections total-collections message]
    (is (= message
          (bulk-update/generate-task-status-message failed-collections total-collections)))

    "Completed successfully"
    0 10 "All collection updates completed successfully."

    "failures"
    5 10 "Task completed with 5 collection update failures out of 10"))
