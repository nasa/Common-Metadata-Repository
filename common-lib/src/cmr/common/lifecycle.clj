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