(ns cmr.virtual-product.config
  "Defines configuration for virtual product app."
  (:require
   [clojure.string :as str]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.mime-types :as mt]
   [cmr.message-queue.config :as mq-conf]
   [cmr.umm.umm-granule :as umm-g])
  (:import
   (java.util.regex Pattern)))

(defconfig virtual-products-enabled
  "Enables the updates of virtual products. If this is false every ingest event will be ignored
  by the virtual product application. If it is true ingest events will be processed and applied to
  the equivalent virtual products."
  {:default true
   :type Boolean})

(defconfig virtual-product-queue-name
  "The queue containing ingest events for the virtual product app."
  {:default "cmr_virtual_product.queue"})

(defconfig ingest-exchange-name
  "The ingest exchange to which ingest event messages are published."
  {:default "cmr_ingest.exchange"})

(defconfig queue-listener-count
  "Number of worker threads to use for the queue listener"
  {:default 5
   :type Long})

(defn queue-config
  "Returns the rabbit mq configuration for the virtual-product application."
  []
  (assoc (mq-conf/default-config)
         :queues [(virtual-product-queue-name)]
         :exchanges [(ingest-exchange-name)]
         :queues-to-exchanges {(virtual-product-queue-name)
                               [(ingest-exchange-name)]}))

(defconfig virtual-product-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

(defconfig virtual-product-provider-aliases
  "For each provider-id for which a virtual product is configured, define a set of provider-ids
  which have the same virtual product configuration as the original."
  {:default {"LPDAAC_ECS"  #{}
             "GES_DISC" #{}}
   :type :edn})

(defconfig disabled-virtual-product-source-collections
  "A list of entry-titles of source collections whose virtual products processing is disabled.
  We add this configuration to have the ability to turn on virtual products processing per collection.
  Note: This configuration does not affect bootstrapping of virtual products, only the forward processing."
  {:default #{}
   :type :edn})
