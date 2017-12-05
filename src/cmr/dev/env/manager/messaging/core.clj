(ns cmr.dev.env.manager.messaging.core
  (:require
    [cmr.dev.env.manager.messaging.impl.pubsub :as pubsub])
  (:import
    (cmr.dev.env.manager.messaging.impl.pubsub PubSubMessenger)))

(defprotocol Messenger
  (content [this msg])
  (message [this topic content])
  (publish [this topic content])
  (subscribe [this topic sub-fn] [this topic sub-fn sub-chan]))

(extend PubSubMessenger
        Messenger
        pubsub/behaviour)

(defn new-messenger
  [msgr-type]
  (case msgr-type
    :pubsub (pubsub/new-messenger)))
