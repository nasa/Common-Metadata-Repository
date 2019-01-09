(ns cmr.mission-control.pubsub.core
  (:require
    [clojure.core.async :as async]
    [cmr.mission-control.message :as message]
    [cmr.mission-control.pubsub.impl.core-async :as core-async]
    [cmr.mission-control.topic :as topic]
    [taoensso.timbre :as log])
  (:import
    [clojure.lang Keyword]
    [cmr.mission_control.pubsub.impl.core_async PubSub]))

(defprotocol PubSubAPI
  "The API for in-process CMR component pubsub messenging."
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
  (log/trace "Got pubsub type:" type)
  (log/trace "Got pubsub topic:" topic)
  (case type
    :core-async (core-async/create-pubsub topic)))

(defn create-dataflow-pubsub
  [^Keyword type]
  (log/trace "Got pubsub type:" type)
  (case type
    :core-async (core-async/create-dataflow-pubsub)))

(defn create-notifications-pubsub
  [^Keyword type]
  (log/trace "Got pubsub type:" type)
  (case type
    :core-async (core-async/create-notifications-pubsub)))
