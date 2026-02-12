(ns cmr.bootstrap.api.messages-bulk-index
  "Utility functions for the bootstrap API focusing on the messages returned in the bulk index
   process. This file exists because messages.clj uses api-util/synchronous? and is used in to many
   places and causes a Cyclic load dependency which causes a Cyclic load dependency."
  (:require
   [cmr.common.services.messages :as msg]))

(def bulk-index-prefix-queue
  "Prefix for all bulk index logs when responding to the sqs queue."
  (str msg/bulk-index-prefix-general "SQS Queue > "))

(def bulk-index-prefix-channel-load
  "Prefix for all bulk index logs that take place when the channel is loading the message."
  (str msg/bulk-index-prefix-general "Channel Load > "))

(def bulk-index-prefix-channel-read
  "Prefix for all bulk index logs that take place after the channel has read the message."
  (str msg/bulk-index-prefix-general "Channel Read > "))

;; ***************************************************************************80
;; helpers

(defn bulk-index-msg-general
  "General Message to return when indexing, will format message according to this logging standard."
  [msg]
  (str msg/bulk-index-prefix-general msg))

;; ***************************************************************************80
;; called in data/bulk_index.clj

;; ***********************************40
;; channel read messages from handle-bulk-index-requests

(defn index-granules-for-collection-start
  [collection-id]
  (format "%s Indexing granule data for collection [%s]." bulk-index-prefix-channel-read collection-id))

(defn index-granules-for-collection-indexed
  [num-granules provider-id collection-id]
  (format "%s Indexed %d granule(s) for provider [%s] collection [%s]."
          bulk-index-prefix-channel-read
          num-granules
          provider-id
          collection-id))

(defn index-system-concepts
  [total]
  (format "%s Indexed %d system concepts." bulk-index-prefix-channel-read total))

(defn delete-concepts-by-id
  [total]
  (format "%s Deleted %d concepts." bulk-index-prefix-channel-read total))

(defn index-concepts-by-id
  [total]
  (format "%s Indexed %d concepts." bulk-index-prefix-channel-read total))

(defn migrate-index-start
  [source-index target-index elastic-name]
  (format "%s Migrating from index [%s] to index [%s] in es cluster [%s]"
          bulk-index-prefix-channel-read
          source-index
          target-index
          elastic-name))

(defn migrate-index-error
  [source-index target-index reason]
  (format "%s Migration from [%s] to [%s] failed: %s"
          bulk-index-prefix-channel-read
          source-index
          target-index
          reason))

;; ***********************************40
;; queue functions

(defn index-provider-data-later-than-date-time
  [date-time provider-id]
  (format "%s Indexing concepts with revision-date later than [%s] for provider [%s] started."
          bulk-index-prefix-queue
          date-time
          provider-id))

(defn index-provider-data-later-than-date-time-sys-concepts
  [num-indexed]
  (format "%s Indexed %d system concepts." bulk-index-prefix-queue num-indexed))

(defn index-provider-data-later-than-date-post
  [provider-concept-count]
  (format "%s Indexed %d provider concepts." bulk-index-prefix-queue provider-concept-count))

(defn index-provider-data-later-than-date-time-completed
  [date-time provider-id]
  (format "%s Indexing concepts with revision-date later than [%s] for provider [%s] completed."
          bulk-index-prefix-queue
          date-time
          provider-id))

(defn index-provider-data-later-than-date-time-failed
  [date-time provider-id reason]
  (format
   "%s Indexing concepts with revision-date later than [%s] for provider [%s] completed but failed because << %s >>!"
   bulk-index-prefix-queue
   date-time
   provider-id
   reason))

(defn index-concepts-by-provider-start
  [concept-type provider-id]
  (format "%s Indexing %ss for provider %s" bulk-index-prefix-queue concept-type provider-id))

(defn index-concepts-by-provider-completed
  [concept-type provider-id num-concepts]
  (format "%s Indexed %d %s(s) for provider %s."
          bulk-index-prefix-queue
          num-concepts
          concept-type
          provider-id))

(defn index-all-concepts-start
  [concept-type]
  (format "%s Indexing all %ss" bulk-index-prefix-queue concept-type))

(defn index-all-concepts-complete
  [concept-type]
  (format "%s Indexing of all %ss completed." bulk-index-prefix-queue concept-type))

(defn fetch-and-index-new-concepts-batches-before
  [provider concept-type params]
  (format "%s About to fetch %s concepts in batches for [%s] with options %s."
          bulk-index-prefix-queue
          concept-type
          provider
          (pr-str params)))

(defn fetch-and-index-new-concepts-batches-after
  [provider concept-type count-of-items]
  (format
   "%s Finished finding all (%d) %s concepts for provider [%s] in db for fetch and index new concepts."
   bulk-index-prefix-queue
   count-of-items
   concept-type
   provider))

;; ***************************************************************************80
;; called in bootstrap_service.clj

(defn index-all-providers-start
  []
  ;; added the word started to the log message, check splunk reports
  (format "%s Indexing all providers started." bulk-index-prefix-queue))

(defn index-all-providers-loop
  [provider-id]
   (format "%s Processing provider [%s] for bulk indexing" bulk-index-prefix-queue provider-id))

(defn index-all-providers-complete
  []
  (format "%s Indexing of all providers scheduled/completed." bulk-index-prefix-queue))

;; ***************************************************************************80
;; called in fingerprint.clj

(defn fingerprint-updating
  [provider]
  (format "%s Updating fingerprints for provider [%s]." bulk-index-prefix-queue provider))

(defn fingerprint-variables
  [variable-count provider]
  (format "%s Updated fingerprints of %d variable(s) for provider %s."
          bulk-index-prefix-queue
          variable-count
          provider))

(defn fingerprint-complete
  [provider]
   (format "%s Updating fingerprints for provider %s completed." bulk-index-prefix-queue provider))

(defn fingerprint-all-updating
  []
  (format "%s Updating fingerprints for all variables." bulk-index-prefix-queue))

(defn fingerprint-all-complete
  []
  (format "%s Updating fingerprints for all variables completed." bulk-index-prefix-queue))

;; ***************************************************************************80
;; data/message_queue.clj

(defn publish-bootstrap-provider-event
  [message]
  (format "%sPublishing bootstrap message: %s" bulk-index-prefix-queue message))

;; ***************************************************************************80
;; impl/message_queue.clj

(defn index-variables-start
  []
  (format "%s Publishing events to index all variables." bulk-index-prefix-queue))

(defn index-variables-done
  []
  (format "%s Publishing events to index all variables completed." bulk-index-prefix-queue))

(defn index-services-start
  []
  (format "%s Publishing events to index all services." bulk-index-prefix-queue))

(defn index-services-done
  []
  (format "%s Publishing events to index all services completed." bulk-index-prefix-queue))

(defn index-tools-start
  []
  (format "%s Publishing events to index all tools." bulk-index-prefix-queue))

(defn index-tools-done
  []
  (format "%s Publishing events to index all tools completed." bulk-index-prefix-queue))

(defn index-subscriptions-start
  []
  (format "%s Publishing events to index all subscriptions." bulk-index-prefix-queue))

(defn index-subscriptions-done
  []
  (format "%s Publishing events to index all subscriptions completed." bulk-index-prefix-queue))

(defn index-generics-start
  [concept-type]
  (format "%s Publishing events to index all generic documents of type %s."
          bulk-index-prefix-queue
          concept-type))

(defn index-generics-done
  [concept-type]
  (format "%s Completed publishing events to index all generic documents of type %s."
          bulk-index-prefix-queue
          concept-type))

(defn index-generics-with-provider-start
  [concept-type provider-id]
  (format "%s Publishing events to index all generic documents of type %s for provider %s"
          bulk-index-prefix-queue
          concept-type
          provider-id))

(defn index-generics-with-provider-done
  [concept-type provider-id]
  (format "%s Completed publishing events to index all generic documents of type %s for provider %s"
          bulk-index-prefix-queue
          concept-type
          provider-id))

(defn index-data-later-than-date-time-start
  []
  (format "%s Publishing events to index all concepts after a given date time."
          bulk-index-prefix-queue))

(defn index-data-later-than-date-time-done
  []
  (format "%s Publishing events to index all concepts after a given date time completed."
          bulk-index-prefix-queue))

(defn subscribe-to-events-start
  [subscription-number]
  (format "%s Subscribing [%d] to bootstrap queue for message handling."
          bulk-index-prefix-queue
          subscription-number))

(defn subscribe-to-events-done
  [subscription-number]
  (format "%s Subscription [%d] to bootstrap queue has finished."
          bulk-index-prefix-queue
          subscription-number))

;; ***************************************************************************80
;; async.clj - puts items into the channels

(defn async-not-implemented
  [action]
  (format "%s Async Dispatcher does not support %s action." bulk-index-prefix-channel-load action))

(defn async-migrate-provider
  [provider-id]
  (format "%s Adding provider %s to provider channel." bulk-index-prefix-channel-load provider-id))

(defn async-migrate-collection
  [provider-id collection-id]
  (format "%s Adding collection %s for provider %s to collection channel."
          bulk-index-prefix-channel-load
          collection-id
          provider-id))

(defn async-index-provider
  [provider-id start-index]
  ;; Added start index to log message, check splunk reports
  (format "%s Adding provider %s to index channel starting at %d."
          bulk-index-prefix-channel-load provider-id start-index))

(defn async-index-collection
  [collection-id provider-id]
  ;; Added provider-id to log message, check splunk reports
  (format "%s Adding collection %s to collection index channel to provider %s."
          bulk-index-prefix-channel-load
          collection-id
          provider-id))

(defn async-index-system-concepts
  [start-index]
  ;; added start index to log message, check splunk reports
  (format "%s Adding bulk index request to system concepts channel with start-index %s."
          bulk-index-prefix-channel-load
          start-index))

(defn async-index-concepts-by-id
  [provider-id concept-type concept-ids]
  ;; added concept details to log message, check splunk reports
  (format "%s Adding bulk index request to concept-id channel for provider %s, concept-type %s, concept-ids %s..."
          bulk-index-prefix-channel-load
          provider-id
          concept-type
          (pr-str (take 10 concept-ids))))

(defn async-migrate-index
  [source-index target-index elastic-name]
  ;; added elastic name, check splunk reports
  (format "%s Migrating from index [%s] to index [%s] in [%s]."
          bulk-index-prefix-channel-load
          source-index
          target-index
          elastic-name))

(defn async-delete-concepts-from-index-by-id
  [provider-id concept-type concept-ids]
  ;; added concept details to log message, check splunk reports
  (format "%s Adding bulk delete request to concept-id channel. %s %s %s..."
          bulk-index-prefix-channel-load
          provider-id
          concept-type
          (pr-str (take 10 concept-ids))))

(defn async-bootstrap-virtual-products
  [provider-id entry-title]
  ;; added provider id and entry title to log message, check splunk reports
  (format "%s Adding message to virtual products channel. %s %s."
          bulk-index-prefix-channel-load
          provider-id
          entry-title))
