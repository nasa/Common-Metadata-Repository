(ns cmr.common.services.health-helper
  "This namespace provides function to timeout the execution of health check function
  and provides the timeout response when timeout occurs."
  (:require [clojail.core :as c]
            [cmr.common.config :as config :refer [defconfig]]))

(defconfig health-check-timeout-seconds
  "Timeout in seconds for health check operation."
  {:default 10 :type Long})

(defn get-health
  "Execute the health check function with timeout handling."
  ([function]
   (get-health function (* 1000 (health-check-timeout-seconds))))
  ([function timeout-ms]
   (try
     (c/thunk-timeout function timeout-ms)
     (catch java.util.concurrent.TimeoutException e
       {:ok? false
        :problem "health check operation timed out."}))))
