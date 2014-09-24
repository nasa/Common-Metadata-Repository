(ns cmr.common.time-keeper
  "This namespace provides a function for retrieving the current time. This is used instead of
  clj-time.core/now directly so that tests can have programmatic control over the current time."
  (:require [clj-time.core :as t]
            [cmr.common.log :refer (debug info warn error)]))

(def time-override
  "Contains the current time to return if it is overriden."
  (atom nil))

(defn set-current-time!
  "Sets the current time. This should only be used for testing."
  [t]
  (warn "Current time set to" t)
  (reset! time-override t))

(defn freeze-time!
  "Sets the current time to whatever the real time is now."
  []
  (set-current-time! (t/now)))

(defn advance-time!
  "Increases the time override by a number of seconds"
  [num-secs]
  (swap! time-override t/plus (t/seconds num-secs))
  (warn "Current time set to" @time-override))

(defn clear-current-time!
  "Clears the current time if one was set."
  []
  (reset! time-override nil))

(defn freeze-resume-time-fixture
  "This is a clojure.test fixture that will freeze time then clear any time override at the end
  of the test."
  []
  (fn [f]
    (try
      (freeze-time!)
      (f)
      (finally
        (clear-current-time!)))))

(defn now
  "Returns the current time"
  []
  (or @time-override (t/now)))