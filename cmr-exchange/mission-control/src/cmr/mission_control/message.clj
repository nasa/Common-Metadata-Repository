(ns cmr.mission-control.message
  (:require
    [cmr.mission-control.topic :as topic]))

(defn new-event
  [topic tag data]
  {topic tag
   :data data})

(defn new-dataflow-event
  [tag data]
  (new-event topic/dataflow-events tag data))

(defn new-notifications-event
  [tag data]
  (new-event topic/notifications-events tag data))

(defn get-payload
  [msg]
  (:data msg))

(defn get-route
  [msg]
  (dissoc msg :data))
