(ns cmr.ingest.api.bulk-test
  "tests functions in bulk update api"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as u :refer [are3]]
    [cmr.ingest.api.bulk :as bulk]))

(deftest generate-status-progress-message
  (are3 [granule-statuses message]
    (is (= message (#'bulk/generate-status-progress-message granule-statuses)))

    "All updates complete"
    [{:granule-ur "GranUR1" :status "UPDATED"}
     {:granule-ur "GranUR2" :status "UPDATED"}]
    "Complete."

    "Some updates complete"
    [{:granule-ur "GranUR1" :status "PENDING"}
     {:granule-ur "GranUR2" :status "FAILED"}
     {:granule-ur "GranUR3" :status "UPDATED"}]
    "Of 3 total granules, 2 granules have been processed and 1 are still pending."

    "No updates complete"
    [{:granule-ur "GranUR1" :status "PENDING"}]
    "Of 1 total granules, 0 granules have been processed and 1 are still pending."))
