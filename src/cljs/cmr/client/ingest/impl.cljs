(ns cmr.client.ingest.impl
  (:require
   [cmr.client.base.impl :as base]
   [cmr.client.http :as http])
  (:refer-clojure :exclude [get]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord CMRIngestClientData [
  endpoint
  options
  http-client])

(defn get-providers
  [this]
  (-> this
      :http-client
      (http/get (base/get-url this "/providers"))))


