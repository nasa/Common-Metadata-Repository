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
