(ns hxgm30.event.pubsub.core
  (:require
    [clojure.core.async :as async]
    [hxgm30.event.message :as message]
    [hxgm30.event.pubsub.impl.core-async :as core-async]
    [hxgm30.event.topic :as topic]
    [taoensso.timbre :as log])
  (:import
    [clojure.lang Keyword]
    [hxgm30.event.pubsub.impl.core_async PubSub]))

(defprotocol PubSubAPI
  "The API for Dragon pubsub messenging."
  (get-topic [this]
    "Get the topic with which the messenger was instantiated.")
  (get-chan [this]
    "Get the core.async channel associated with the publisher.")
  (get-pub [this]
    "Get the core.async pub associated with the publisher.")
  (get-sub [this tag]
    "Create and return a subscriber channel for a given tag (event-type).")
  (delete [this]
    "Delete the publisher."))

(extend PubSub
        PubSubAPI
        core-async/behaviour)

(defn create-pubsub
  [^Keyword type topic]
  (case type
    :core-async (core-async/create-pubsub topic)))

(defn create-dataflow-pubsub
  [^Keyword type]
  (case type
    :core-async (core-async/create-dataflow-pubsub)))
