(ns cmr.common-app.system
  "Contains helper functions for application systems."
  (require [cmr.common.lifecycle :as lifecycle]
           [cmr.common.log :as log :refer (debug info warn error)]))

(defn start
  "Starts the system using the given component order."
  [s component-order]
  (reduce (fn [system component-name]
            (update-in system [component-name]
                       #(when % (lifecycle/start % system))))
          s
          component-order))

(defn stop
  "Stops the system using the given component order."
  [s component-order]
  (reduce (fn [system component-name]
            (update-in system [component-name]
                       #(when % (lifecycle/stop % system))))
          s
          (reverse component-order)))


(defn start-fn
  "Creates a generic system start function that logs when the system is starting and started"
  [system-name component-order]
  (fn [s]
    (info (str system-name " System starting"))
    (try
      (start s component-order)
      (finally
        (info (str system-name " System started"))))))

(defn stop-fn
  "Creates a generic system stop function that logs when the system is stopping and stopped"
  [system-name component-order]
  (fn [s]
    (info (str system-name " System stopping"))
    (try
      (stop s component-order)
      (finally
        (info (str system-name " System stopped"))))))

