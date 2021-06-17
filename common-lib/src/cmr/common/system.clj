(ns cmr.common.system
  "Contains helper functions for application systems."
  (:require
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer (debug info warn error)]))

(defn stop
  "Stops the system using the given component order."
  [s component-order]
  (reduce (fn [system component-name]
            (update-in system [component-name]
                       #(when % (lifecycle/stop % system))))
          s
          (reverse component-order)))

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
  (try
    (->> component-order
         (reduce start-component {:system s :started-components []})
         :system)
    (catch Exception e
      (.printStackTrace e)
      (throw e))))

(defn start-fn
  "Creates a generic system start function that logs when the system is
  starting and started"
  [system-name component-order]
  (fn [s]
    (info (str system-name " System starting"))
    (try
      (start s component-order)
      (finally
        (info (str system-name " System started"))))))

(defn stop-fn
  "Creates a generic system stop function that logs when the system is stopping
  and stopped"
  [system-name component-order]
  (fn [s]
    (info (str system-name " System stopping"))
    (try
      (stop s component-order)
      (finally
        (info (str system-name " System stopped"))))))
