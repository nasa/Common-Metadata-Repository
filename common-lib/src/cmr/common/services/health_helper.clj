(ns cmr.common.services.health-helper
  "This namespace provides function to timeout the execution of health check function
  and provides the timeout response when timeout occurs."
  (:require
   [cmr.common.config :as config :refer [defconfig]]
   [cmr.common.util :as util]))

(defconfig health-check-timeout-seconds
  "Timeout in seconds for health check operation."
  {:default 10 :type Long})

(defn get-health
  "Execute the health check function with timeout handling."
  ([function]
   (get-health function (* util/second-as-milliseconds (health-check-timeout-seconds))))
  ([function timeout-ms]
   (let [check (future (function))
         result (deref check timeout-ms ::timed-out)]
     (if (= ::timed-out result)
       ;; Not Thread.stop: an Error mid-request makes HttpClient 4.5 shut down the whole pool.
       (do (future-cancel check)
           {:ok? false
            :problem "health check operation timed out."})
       result))))
