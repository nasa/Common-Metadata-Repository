(ns cmr.bootstrap.api.messages
  "Utility functions for the bootstrap API."
  (:require
   [cmr.bootstrap.api.util :as api-util]
   [cmr.bootstrap.api.messages-bulk-index :as msg]))

(defn required-params
  [& args]
  (format "The parameters %s are required" (vec args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Bulk index messages
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn index-provider
  [params result provider-id start-index]
  (if (api-util/synchronous? params)
    result
    (format "%s Processing provider %s for bulk indexing from start index %s"
            msg/bulk-index-msg-general provider-id start-index)))

(defn index-collection
  [params result collection-id]
  (if (api-util/synchronous? params)
    result
    (format "%s Processing collection %s for bulk indexing." msg/bulk-index-msg-general collection-id)))

(defn index-variables
  "Message to return when indexing variables."
  [params provider-id result]
  (if (api-util/synchronous? params)
    result
    (if provider-id
      (format "%s Processing variables for provider %s for bulk indexing." msg/bulk-index-msg-general provider-id)
      (format "%s Processing all variables for bulk indexing." msg/bulk-index-msg-general))))
(defn index-services
  "Message to return when indexing services."
  [params provider-id result]
  (if (api-util/synchronous? params)
    result
    (if provider-id
      (format "%s Processing services for provider %s for bulk indexing." msg/bulk-index-msg-general provider-id)
      (format "%s Processing all services for bulk indexing." msg/bulk-index-msg-general))))

(defn index-tools
  "Message to return when indexing tools."
  [params provider-id result]
  (if (api-util/synchronous? params)
    result
    (if provider-id
      (format "%s Processing tools for provider %s for bulk indexing." msg/bulk-index-msg-general provider-id)
      (format "%s Processing all tools for bulk indexing." msg/bulk-index-msg-general))))
(defn index-subscriptions
  "Message to return when indexing subscriptions."
  [params provider-id result]
  (if (api-util/synchronous? params)
    result
    (if provider-id
      (format "%s Processing subscriptions for provider %s for bulk indexing." msg/bulk-index-msg-general provider-id)
      (format "%s Processing all subscriptions for bulk indexing." msg/bulk-index-msg-general))))

(defn index-generics
  "Message to return when indexing generic documents of type concept-type."
  [params concept-type provider-id result]
  (if (api-util/synchronous? params)
    result
    (if provider-id
      (format "%s Processing generic documents of type %s for provider %s for bulk indexing." msg/bulk-index-msg-general concept-type provider-id)
      (format "%s Processing all generic documents of type %s for bulk indexing." msg/bulk-index-msg-general concept-type))))
(defn data-later-than-date-time
  [params result date-time]
  (if (api-util/synchronous? params)
    (:message result)
    (format "%s Processing data after %s for bulk indexing" msg/bulk-index-msg-general date-time)))

(defn invalid-datetime
  [date-time]
  (str date-time " is not a valid date-time."))

(defn system-concepts
  [params result]
  (if (api-util/synchronous? params)
    (format "%s Processed %s system concepts for bulk indexing." msg/bulk-index-msg-general result)
    (format "%s Processing system concepts for bulk indexing." msg/bulk-index-msg-general)))

(defn index-concepts-by-id
  [params result]
  (if (api-util/synchronous? params)
    (format "%s Processed %s concepts for bulk indexing." msg/bulk-index-msg-general result)
    (format "%s Processing concepts for bulk indexing." msg/bulk-index-msg-general)))

(defn delete-concepts-by-id
  [params result]
  (if (api-util/synchronous? params)
    (format "%s Processed %s concepts for bulk deletion from indexes." msg/bulk-index-msg-general result)
    (format "%s Processing concepts for bulk deletion from indexes." msg/bulk-index-msg-general)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Bulk migration messages
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn migration-collection
  [collection-id provider-id]
  (format "Processing collection %s for provider %s"
          collection-id provider-id))

(defn migration-provider
  [provider-id]
  (str "Processing provider " provider-id))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Virtual product messages
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn no-virtual-product-config
  [provider-id entry-title]
  (format (str "No virtual product configuration found for provider [%s] "
               "and entry-title [%s]")
          provider-id entry-title))

(defn bootstrapping-virtual-products
  ([]
   "Bootstrapping virtual products")
  ([provider-id entry-title]
   (format "%s for provider [%s] entry-title [%s]"
           (bootstrapping-virtual-products) provider-id entry-title)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Virtual product messages
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn rebalancing-started
  [concept-id]
  (str "Rebalancing started for collection " concept-id))

(defn rebalancing-completed
  [concept-id]
  (str "Rebalancing completed for collection " concept-id))

(defn resharding-started
  [index]
  (str "Resharding started for index " index))

(defn resharding-completed
  [index]
  (str "Resharding completed for index " index))
