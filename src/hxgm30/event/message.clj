(ns hxgm30.event.message
  (:require
    [hxgm30.event.topic :as topic]))

(defn new-event
  [topic tag data]
  {topic tag
   :data data})

(defn new-dataflow-event
  [tag data]
  (new-event topic/dataflow-events tag data))

(defn new-world-event
  [tag data]
  (new-event topic/world-events tag data))

(defn get-payload
  [msg]
  (:data msg))

(defn get-route
  [msg]
  (dissoc msg :data))
