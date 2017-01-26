(ns cmr.message-queue.test.queue.sqs
  (:require [clojure.test :refer :all]
            [cmr.common.test.test-util :refer [with-env-vars]]
            [cmr.common.util :as util :refer [are3]]
            [cmr.message-queue.queue.sqs :as sqs]))

(defn normalize-queue-name
  "Function to call private normalize-queue-name function."
  [queue-name]
  (#'sqs/normalize-queue-name queue-name))

(comment
  (normalize-queue-name "abc")
  (#'sqs/normalize-queue-name "abc"))


(deftest normalize-queue-name
  (testing "with-and-without-previx"
    (are3
      [environ queue-name norm-name]
      (with-env-vars {"CMR_APP_ENVIRONMENT" environ}
        (is (= norm-name (#'sqs/normalize-queue-name  queue-name))))

      "SIT with prefix already applied"
      "sit" "gsfc-eosdis-cmr-sit-abc123" "gsfc-eosdis-cmr-sit-abc123"

      "SIT without prefix already applied"
      "sit" "abc123" "gsfc-eosdis-cmr-sit-abc123"

      "UAT with prefix already applied"
      "uat" "gsfc-eosdis-cmr-uat-abc123" "gsfc-eosdis-cmr-uat-abc123"

      "UAT without prefix already applied"
      "uat" "abc123" "gsfc-eosdis-cmr-uat-abc123"

      "WL with prefix already applied"
      "wl" "gsfc-eosdis-cmr-wl-abc123" "gsfc-eosdis-cmr-wl-abc123"

      "WL without prefix already applied"
      "wl" "abc123" "gsfc-eosdis-cmr-wl-abc123"

      "OPS with prefix already applied"
      "ops" "gsfc-eosdis-cmr-ops-abc123" "gsfc-eosdis-cmr-ops-abc123"

      "OPS without prefix already applied"
      "ops" "abc123" "gsfc-eosdis-cmr-ops-abc123")))
