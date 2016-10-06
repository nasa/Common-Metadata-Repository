(ns cmr.common.time-keeper
  "This namespace provides a function for retrieving the current time. This is used instead of
  clj-time.core/now directly so that tests can have programmatic control over the current time."
  (:require
   [clj-time.core :as t]
   [clj-time.coerce :as coerce]
   [cmr.common.log :refer (debug info warn error)]))

(def time-override
  "Contains the current time to return if it is overriden."
  (atom nil))

(defn set-time-override!
  "Sets the override time to the given time given. This should only be used for testing."
  [t]
  (reset! time-override t))

(defn freeze-time!
  "Sets the override time to the current real time. This 'freezes' the time so that it won't advance
  until the advance time is called or the time is 'unfrozen' by calling clear-current-time!."
  []
  (set-time-override! (t/now)))

(defn advance-time!
  "Increases the time override by a number of seconds"
  [num-secs]
  (swap! time-override t/plus (t/seconds num-secs)))

(defn clear-current-time!
  "Clears the current time if one was set."
  []
  (reset! time-override nil))

(defn freeze-resume-time-fixture
  "This is a clojure.test fixture that will freeze time then clear any time override at the end
  of the test."
  [f]
  (try
    (freeze-time!)
    (f)
    (finally
      (clear-current-time!))))

(defmacro with-frozen-time
  "Freezes time around the body and then clears it."
  [& body]
  `(try
     (freeze-time!)
     (do ~@body)
     (finally
       (clear-current-time!))))

(defn now
  "Returns the current time"
  []
  (or @time-override (t/now)))

(defn now-ms
  "Returns the current time in milliseconds"
  []
  (coerce/to-long (now)))
