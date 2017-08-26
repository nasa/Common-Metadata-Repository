(ns cmr.client.ingest.core
 (:require
  [cmr.client.common.const :as const]
  [cmr.client.common.util :as util]
  [cmr.client.http.core :as http]
  [cmr.client.ingest.impl :as impl])
 (:import (cmr.client.ingest.impl CMRIngestClientData CMRIngestClientOptions)))

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
   (let [endpoint (util/get-default-endpoint options :ingest)
         client-options (impl/->CMRIngestClientOptions
                         (:return-body? options))]
     (impl/->CMRIngestClientData (util/parse-endpoint endpoint :ingest)
                                 client-options
                                 (http/create-client client-options
                                                     http-options)))))

(comment
  (def client (ingest/create-client {:endpoint :local :return-body? true}))
  (ingest/get-providers client))
