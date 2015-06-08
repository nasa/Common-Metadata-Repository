(ns cmr.virtual-product.config
  (:require [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.message-queue.config :as mq-conf]
            [clojure.string :as str]))

(defconfig virtual-product-queue-name
  "The queue containing ingest events for the virtual product app."
  {:default "cmr_virtual_product.queue"})

(defconfig ingest-exchange-name
  "The ingest exchange to which messages are published."
  {:default "cmr_ingest.exchange"})

(defconfig queue-listener-count
  "Number of worker threads to use for the queue listener"
  {:default 5
   :type Long})

(defn rabbit-mq-config
  "Returns the rabbit mq configuration for the virtual-product application."
  []
  (assoc (mq-conf/default-config)
         :queues [(virtual-product-queue-name)]
         :exchanges [(ingest-exchange-name)]
         :queues-to-exchanges {(virtual-product-queue-name)
                               (ingest-exchange-name)}))

(defconfig virtual-product-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

(def source-to-virtual-product-config
  "A map of source collection provider id and entry titles to virtual product configs"
  {["LPDAAC_ECS" "ASTER L1A Reconstructed Unprocessed Instrument Data V003"]
   {:source-short-name "AST_L1A"
    :virtual-collections [{:entry-title "ASTER On-Demand L2 Surface Emissivity"
                           :short-name "AST_05"}]}})

(def sample-source-granule-urs
  "This contains a map of source collection provider id and entry title tuples to sample granule
  urs. This is included both for testing and documentation of what the sample URs look like"
  {["LPDAAC_ECS" "ASTER L1A Reconstructed Unprocessed Instrument Data V003"]
   ["SC:AST_L1A.003:2006227720"
    "SC:AST_L1A.003:2006227722"
    "SC:AST_L1A.003:2006227710"]})

(defmulti generate-granule-ur
  "Generates a new granule ur for the virtual collection"
  (fn [provider-id source-short-name virtual-short-name granule-ur]
    [provider-id source-short-name]))

;; AST_L1A granule urs look like this "SC:AST_L1A.003:2006227720". We generate them by using the short
;; name of the virtual collection instead of the source one.
(defmethod generate-granule-ur ["LPDAAC_ECS" "AST_L1A"]
  [provider-id source-short-name virtual-short-name granule-ur]
  (str/replace granule-ur source-short-name virtual-short-name))