(ns cmr.dev.env.manager.messaging.impl.pubsub
  (:require
    [clojure.core.async :as async]))

(defrecord PubSubMessenger
  [message-key
   message-val
   pub-channel
   publisher])

(def ^:private default-pubsub-key :topic)
(def ^:private default-pubsub-val :content)

(defn- publisher
  [pub-channel msg-key]
  (async/pub pub-channel msg-key))

(defn message
  [this topic content]
  {(:message-key this) topic
   (:message-val this) content})

(defn content
  [this msg]
  ((:message-val this) msg))

(defn publish
  [this topic content]
  (async/>!! (:pub-channel this)
             (message this topic content)))

(defn stop!
  [this]
  ;; XXX also `close!` all subscriber channles?
  (async/close! (:pub-channel this)))

(defn subscribe
  ([this topic subscriber-fn]
    (subscribe this topic subscriber-fn (async/chan)))
  ([this topic subscriber-fn sub-channel]
    (async/sub (:publisher this)
               topic
               sub-channel)
    (async/go-loop []
      (when-let [msg (async/<! sub-channel)]
        (subscriber-fn (content this msg))
        (recur)))))

(def behaviour
  {:content content
   :message message
   :publish publish
   :subscribe subscribe
   :stop! stop!})

(defn new-messenger
  ([]
    (new-messenger (async/chan)))
  ([pub-channel]
    (map->PubSubMessenger
      {:message-key default-pubsub-key
       :message-val default-pubsub-val
       :pub-channel pub-channel
       :publisher (publisher pub-channel default-pubsub-key)})))
