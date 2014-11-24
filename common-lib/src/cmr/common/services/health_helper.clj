(ns cmr.common.services.health-helper
  "This namespace provides function to timeout the execution of health check function
  and provides the timeout response when timeout occurs."
  (:require [clojail.core :as c]))

(def health-check-timeout-ms
  "Timeout in milliseconds for health check operation, default to 10s."
  10000)

(defn get-health
  "Execute the health check function with timeout handling."
  ([function]
   (get-health function health-check-timeout-ms))
  ([function timeout]
   (try
     (c/thunk-timeout function timeout)
     (catch java.util.concurrent.TimeoutException e
       {:ok? false
        :problem "health check operation timed out."}))))
