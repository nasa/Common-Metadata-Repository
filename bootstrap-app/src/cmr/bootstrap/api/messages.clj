(ns cmr.bootstrap.api.messages
  "Utility functions for the bootstrap API."
  (:require
   [clojure.string :as string]
   [cmr.bootstrap.api.util :as api-util]))

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
    (format "Processing provider %s for bulk indexing from start index %s"
            provider-id start-index)))

(defn index-collection
  [params result collection-id]
  (if (api-util/synchronous? params)
    result
    (format "Processing collection %s for bulk indexing." collection-id)))

(defn index-variables
  "Message to return when indexing variables."
  [params provider-id result]
  (if (api-util/synchronous? params)
    result
    (if provider-id
      (format "Processing variables for provider %s for bulk indexing." provider-id)
      "Processing all variables for bulk indexing.")))

(defn index-services
  "Message to return when indexing services."
  [params provider-id result]
  (if (api-util/synchronous? params)
    result
    (if provider-id
      (format "Processing services for provider %s for bulk indexing." provider-id)
      "Processing all services for bulk indexing.")))

(defn index-tools
  "Message to return when indexing tools."
  [params provider-id result]
  (if (api-util/synchronous? params)
    result
    (if provider-id
      (format "Processing tools for provider %s for bulk indexing." provider-id)
      "Processing all tools for bulk indexing.")))

(defn index-subscriptions
  "Message to return when indexing subscriptions."
  [params provider-id result]
  (if (api-util/synchronous? params)
    result
    (if provider-id
      (format "Processing subscriptions for provider %s for bulk indexing." provider-id)
      "Processing all subscriptions for bulk indexing.")))

(defn index-generics
  "Message to return when indexing generic documents of type concept-type."
  [params concept-type provider-id result]
  (if (api-util/synchronous? params)
    result
    (if provider-id
      (format "Processing generic documents of type %s for provider %s for bulk indexing." concept-type provider-id)
      (format "Processing all generic documents of type %s for bulk indexing." concept-type))))

(defn data-later-than-date-time
  [params result date-time]
  (if (api-util/synchronous? params)
    (:message result)
    (format "Processing data after %s for bulk indexing" date-time)))

(defn invalid-datetime
  [date-time]
  (str date-time " is not a valid date-time."))

(defn system-concepts
  [params result]
  (if (api-util/synchronous? params)
    (format "Processed %s system concepts for bulk indexing." result)
    (str "Processing system concepts for bulk indexing.")))

(defn index-concepts-by-id
  [params result]
  (if (api-util/synchronous? params)
    (format "Processed %s concepts for bulk indexing." result)
    (str "Processing concepts for bulk indexing.")))

(defn delete-concepts-by-id
  [params result]
  (if (api-util/synchronous? params)
    (format "Processed %s conccepts for bulk deletion from indexes." result)
    (str "Processing concepts for bulk deletion from indexes.")))

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
