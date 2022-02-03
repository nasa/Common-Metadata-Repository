(ns cmr.ingest.services.granule-bulk-update-service-test
  (:require
   [clojure.test :refer :all]
   [cmr.ingest.services.granule-bulk-update-service :as service]))

(deftest granule-bulk-update-chunk-size-test
  (is (and (int? (service/granule-bulk-update-chunk-size))
           (pos? (service/granule-bulk-update-chunk-size)))))
