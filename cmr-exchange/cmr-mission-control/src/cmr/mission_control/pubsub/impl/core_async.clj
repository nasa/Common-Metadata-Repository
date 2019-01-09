(ns cmr.mission-control.pubsub.impl.core-async
  (:require
    [clojure.core.async :as async]
    [cmr.mission-control.topic :as topic]
    [taoensso.timbre :as log]))

(defrecord PubSub
  [topic chan pub])

(defn get-topic
  [this]
  (:topic this))

(defn get-chan
  [this]
  (:chan this))

(defn get-pub
  [this]
  (:pub this))

(defn get-sub
  [this tag]
  (let [sub-chan (async/chan 1)]
    (async/sub (get-pub this) tag sub-chan)
    #_sub-chan))

(defn delete
  [this]
  (-> this
      (get-chan)
      (async/close!))
  :deleted)

(def behaviour
  {:get-topic get-topic
   :get-chan get-chan
   :get-pub get-pub
   :get-sub get-sub
   :delete delete})

(defn create-pubsub
  "Constructor for a pub-sub messenger. Takes one parameter, a publisher topic,
  that is used to perform lookups.

  Since topics are keywords, the data sent on the publish channel needs to have
  the topic as one of the keys, or a subscriber will not receive the message."
  [topic]
  (log/debug "Creating pubsub manager ...")
  (log/trace "Using topic" topic)
  (let [channel (async/chan 1)]
    (map->PubSub
     {:topic topic
      :chan channel
      :pub (async/pub channel topic)})))

(defn create-dataflow-pubsub
  []
  (create-pubsub topic/dataflow-events))

(defn create-notifications-pubsub
  []
  (create-pubsub topic/notifications-events))
