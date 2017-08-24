(ns cmr.client.ingest.core
 (:require
  [cmr.client.const :as const]
  [cmr.client.util :as util]
  [cmr.client.http.core :as http]
  #?(:clj [cmr.client.ingest.impl :as impl
                                  :refer [->CMRIngestClientData
                                          ->CMRIngestClientOptions]]
     :cljs [cmr.client.ingest.impl :as impl
                                   :refer :all
                                   :include-macros true]))
 #?(:clj
     (:import (cmr.client.ingest.impl CMRIngestClientData CMRIngestClientOptions))))

(def endpoints {
  :service "/ingest"
  :local ":3002"})

(def default-endpoint (str const/host-prod (:service endpoints)))

(defprotocol CMRIngestAPI
  (get-url [this segment])
  (get-providers [this]))

(extend CMRIngestClientData
        CMRIngestAPI
        impl/client-behaviour)

(defn create-client
  ([]
   (create-client {}))
  ([options]
   (create-client options {}))
  ([options http-options]
   (let [endpoint (or (:endpoint options) default-endpoint)
         client-options (->CMRIngestClientOptions (:return-body? options))]
     (->CMRIngestClientData
       (util/parse-endpoint endpoint endpoints)
       client-options
       (http/create-client client-options http-options)))))

(comment
  (def client (ingest/create-client {:endpoint :local :return-body? true}))
  (ingest/get-providers client))
