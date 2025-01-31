(ns cmr.message-queue.topic.topic-protocol
  "Defines the topic protocol.")

(defprotocol Topic
  "Functions for working with a message topic"

  (subscribe
    [this subscription]
    "Subscribes to the given topic.")
  
  (unsubscribe
   [this subscription]
   "Unsubscribes to the given topic.")

  (publish
    [this message message-attributes subject]
    "Publishes a message on the topic. Returns true if the message was 
    successful. Otherwise returns false.")

  (health
    [this]
    "Checks to see if the topic is up and functioning properly. Returns a map with the
    following keys/values:
    :ok? - set to true if the queue is operational and false if not.
    :problem (only present when :ok? is false) - a string indicating the problem."))
