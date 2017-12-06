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

(defn batch-subscribe
  "The passed argument `subscribers` is a list of maps with each map having
  `:topic` and `:content` keys with corresponding values."
  [messenger subscribers]
  (for [subscriber subscribers]
    (subscribe messenger (:topic subscriber) (:content subscriber))))
