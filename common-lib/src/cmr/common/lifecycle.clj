(ns cmr.common.lifecycle)

(defprotocol Lifecycle
  "Defines a component with a start and stop functions."
  (start
    [component system]
    "Begins operation of this component. Synchronous, does not return until the component is
    started. Returns an updated version of this component.")
  (stop
    [component system]
    "Ceases operation of this component. Synchronous, does not return until the component is
    stopped. Returns an updated version of this component."))

(extend-protocol Lifecycle
  ;; Based on code in Stuart Sierra Component
  ;; Make lifecycle work on everything by default.
  java.lang.Object
  (start
    [this system]
    this)
  (stop
    [this system]
    this)

  ;; Make it work on maps. Each value in the map will be started
  clojure.lang.IPersistentMap
  (start
    [this system]
    (reduce (fn [m k]
              (update-in m [k] #(when % (start % system))))
            this
            (keys this)))
  (stop
    [this system]
    (reduce (fn [m k]
              (update-in m [k] #(when % (stop % system))))
            this
            (keys this))))
