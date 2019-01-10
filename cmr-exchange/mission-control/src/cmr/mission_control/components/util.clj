(ns cmr.mission-control.components.util
  (:require
    [taoensso.timbre :as log]))

(defn component->system
  ""
  [component type]
  (if (contains? component type)
    component
    {type component}))

(defn pubsub-component->system
  [component]
  (component->system component :pubsub))
