(ns cmr.client.ingest.impl
  "This namespace defines the implementation of the CMR ingest client
  protocols.

  Note that the implementation includes the definitions of the data records
  used for storing client-specific state."
  (:require
   [cmr.client.http.core :as http]
   #?(:clj [cmr.client.base :as base]
      :cljs [cmr.client.base.impl :as base])))

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

#?(:clj
(def client-behaviour
  "A map of method names to implementations.

  Intended for use by the `extend` protocol function."
  {:get-providers get-providers}))
