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
  "Message to return when indexing"
  [msg]
  (str msg/bulk-index-prefix-general msg))

(defn bulk-index-queue-msg
  "Message to return when indexing all providers from the SQS queue, not from the channels."
  [msg]
  (str bulk-index-prefix-queue msg))

(defn bulk-index-channel-load-msg
  "Message to return when indexing all providers from the core.async channels, during channel load."
  [msg]
  (str bulk-index-prefix-channel-load msg))

(defn bulk-index-channel-read-msg
  "Message to return when indexing all providers from the core.async channels, not from the SQS queue."
  [msg]
  (str bulk-index-prefix-channel-read msg))

;; ***************************************************************************80
;; called in data/bulk_index.clj

(defn index-provider-data-later-than-date-time
  [date-time provider-id]
  (bulk-index-queue-msg
   (format "Indexing concepts with revision-date later than [%s] for provider [%s] started."
           date-time
           provider-id)))

(defn index-provider-data-later-than-date-time-sys-concepts
  [num-indexed]
  (bulk-index-queue-msg (format "Indexed %d system concepts." num-indexed)))

(defn index-provider-data-later-than-date-post
  [provider-concept-count]
  (bulk-index-queue-msg (format "Indexed %d provider concepts." provider-concept-count)))

(defn index-provider-data-later-than-date-time-completed
  [date-time provider-id]
  (bulk-index-queue-msg
   (format "Indexing concepts with revision-date later than [%s] for provider [%s] completed."
           date-time
           provider-id)))

(defn index-provider-data-later-than-date-time-failed
  [date-time provider-id]
  (bulk-index-queue-msg
   (format "Indexing concepts with revision-date later than [%s] for provider [%s] failed!"
           date-time
           provider-id)))

(defn index-concepts-by-provider-start
 [concept-type provider-id]
  (bulk-index-queue-msg (format "Indexing %ss for provider %s" concept-type provider-id)))

(defn index-concepts-by-provider-completed
  [concept-type provider-id num-concepts]
  (bulk-index-queue-msg
   (format "Indexed %d %s(s) for provider %s." num-concepts concept-type provider-id)))

(defn index-all-concepts-start
  [concept-type]
  (bulk-index-queue-msg (format "Indexing all %ss" concept-type)))

(defn index-all-concepts-complete
  [concept-type]
  (bulk-index-queue-msg (format "Indexing of all %ss completed." concept-type)))

(defn fetch-and-index-new-concepts-batches-before
  [provider concept-type params]
  (bulk-index-queue-msg
   (format "About to fetch %s concepts in batches for [%s] with options %s."
           concept-type
           provider
           (pr-str params))))

(defn fetch-and-index-new-concepts-batches-after
  [provider concept-type count-of-items]
  (bulk-index-queue-msg
   (format "Finished finding all (%d) %s concepts for provider [%s] in db for fetch and index new concepts."
           count-of-items
           concept-type
           provider)))

;; ***************************************************************************80
;; called in bootstrap_service.clj

(defn index-all-providers-start
  []
  ;; added the word started to the log message, check splunk reports
  (bulk-index-channel-load-msg "Indexing all providers started."))

(defn index-all-providers-loop
  [provider-id]
  (bulk-index-channel-load-msg
   (format "Processing provider [%s] for bulk indexing" provider-id)))

(defn index-all-providers-complete
  []
  (bulk-index-channel-load-msg "Indexing of all providers scheduled/completed."))

;; ***************************************************************************80
;; called in fingerprint.clj

(defn fingerprint-updating
  [provider]
    (bulk-index-queue-msg (format "Updating fingerprints for provider [%s]"  provider)))

(defn fingerprint-variables
  [variable-count provider]
  (bulk-index-queue-msg
   (format "Updated fingerprints of %d variable(s) for provider %s" variable-count provider)))

(defn fingerprint-complete
  [provider]
  (bulk-index-queue-msg (format "Updating fingerprints for provider %s completed." provider)))

(defn fingerprint-all-updating
  []
  (bulk-index-queue-msg "Updating fingerprints for all variables."))

(defn fingerprint-all-complete
  []
  (bulk-index-queue-msg "Updating fingerprints for all variables completed."))

;; ***************************************************************************80
;; data/message_queue.clj

(defn publish-bootstrap-provider-event
  [message]
  (bulk-index-queue-msg (format "Publishing bootstrap message: %s" message)))

;; ***************************************************************************80
;; async.clj

(defn async-not-implemented
  [action]
  (bulk-index-channel-load-msg (format "Async Dispatcher does not support %s action." action)))

(defn async-migrate-provider
  [provider-id]
  (bulk-index-channel-load-msg (format "Adding provider %s to provider channel" provider-id)))

(defn async-migrate-collection
  [provider-id collection-id]
  (bulk-index-channel-load-msg
   (format "Adding collection %s for provider %s to collection channel" collection-id provider-id)))

(defn async-index-provider
  [provider-id start-index]
  ;; Added start index to log message, check splunk reports
  (bulk-index-channel-load-msg
   (format "Adding provider %s to index channel starting at %d" provider-id start-index)))

(defn async-index-collection
  [collection-id provider-id]
   ;; Added provider-id to log message, check splunk reports
  (bulk-index-channel-load-msg
   (format "Adding collection %s to collection index channel to provider %s"
           collection-id
           provider-id)))

(defn async-index-system-concepts
  [start-index]
  (bulk-index-channel-load-msg
   ;; added start index to log message, check splunk reports
   (format "Adding bulk index request to system concepts channel with start-index %s."
           start-index)))

(defn async-index-concepts-by-id
  [provider-id concept-type concept-ids]
  (bulk-index-channel-load-msg
   ;; added concept details to log message, check splunk reports
   (format "Adding bulk index request to concept-id channel for provider %s, concept-type %s, concept-ids %s..."
           provider-id
           concept-type
           (pr-str (take 10 concept-ids)))))

(defn async-migrate-index
  [source-index target-index elastic-name]
  (bulk-index-channel-load-msg
   ;; added elastic name, check splunk reports
   (format "Migrating from index [%s] to index [%s] in [%s]"
           source-index
           target-index
           elastic-name)))

(defn async-delete-concepts-from-index-by-id
  [provider-id concept-type concept-ids]
  ;; added concept details to log message, check splunk reports
  (bulk-index-channel-load-msg
   (format "Adding bulk delete request to concept-id channel. %s %s %s..."
           provider-id
           concept-type
           (pr-str (take 10 concept-ids)))))

(defn async-bootstrap-virtual-products
  [provider-id entry-title]
  ;; added provider id and entry title to log message, check splunk reports
  (bulk-index-channel-load-msg
   (format "Adding message to virtual products channel. %s %s" provider-id entry-title)))
