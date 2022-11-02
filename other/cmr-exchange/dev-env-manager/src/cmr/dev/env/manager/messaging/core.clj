(ns cmr.dev.env.manager.messaging.core
  (:require
    [cmr.dev.env.manager.messaging.impl.pubsub :as pubsub]
    [taoensso.timbre :as log])
  (:import
    (cmr.dev.env.manager.messaging.impl.pubsub PubSubMessenger)))

(defprotocol Messenger
  (content [this msg])
  (message [this topic content])
  (publish [this topic content])
  (stop! [this])
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
  `:topic` and `:fn` keys with corresponding values."
  [messenger subscribers]
  (doseq [subscriber subscribers]
    (log/debugf "Subscribing %s to %s ..." (:fn subscriber) (:topic subscriber))
    (subscribe messenger (:topic subscriber) (:fn subscriber))))
