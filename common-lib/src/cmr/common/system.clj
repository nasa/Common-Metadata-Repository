(ns cmr.common.system
  "Contains helper functions for application systems."
  (:require
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer (error reportf)]))

(defn instance-name
  "Creates a random instance name for the current application instance. This name
   can then be used in logs to distinguish a specific node from others in a human
   readable way"
  [app-name]
  (format "%s-%d" app-name (int (rand 1024))))

(defn stop
  "Stops the system using the given component order."
  [s component-order]
  (reportf "%s System stopping" (:instance-name s))
  (when (nil? (:instance-name s)) (reportf "stop dump: %s" (keys s)))
  (reduce (fn [system component-name]
            (update-in system [component-name]
                       #(when % (lifecycle/stop % system))))
          s
          (reverse component-order))
  (reportf "%s System stopped" (:instance-name s)))

(defn start-component
  "Start an individual system component.

  This function is only intended to be used by the over-arching system start
  function."
  [system-tracker component-name]
  (try
    (-> system-tracker
        (update-in [:system component-name]
                   #(when % (lifecycle/start % (:system system-tracker))))
        (update :started-components conj component-name))
    (catch Exception e
      (error (str "Exception occurred during startup. Shutting down started "
                  "components"))
      (stop (:system system-tracker) (:started-components system-tracker))
      (throw e))))

(defn start
  "Starts the system using the given component order."
  [s component-order]
  (reportf "%s System starting" (:instance-name s))
  (when (nil? (:instance-name s)) (reportf "start dump: %s" (keys s)))
  (try
    (->> component-order
         (reduce start-component {:system s :started-components []})
         :system)
    (catch Exception e
      (.printStackTrace e)
      (throw e))
    (finally
      (reportf "%s System started" (:instance-name s)))))

(defn start-fn
  "Creates a generic system start function that logs when the system is
  starting and started"
  [_system-name component-order]
  (fn [system]
    (start system component-order)))

(defn stop-fn
  "Creates a generic system stop function that logs when the system is stopping
  and stopped"
  [_system-name component-order]
  (fn [system]
    (stop system component-order)))
