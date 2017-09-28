(ns cmr.client.ingest.impl
  "This namespace defines the implementation of the CMR ingest client
  protocols.

  Note that the implementation includes the definitions of the data records
  used for storing client-specific state."
  (:require
   [cmr.client.http.core :as http]
   [cmr.client.http.util :as http-util]
   #?(:clj [cmr.client.base :as base]
      :cljs [cmr.client.base.impl :as base])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord CMRIngestClientData [
  endpoint
  token
  options
  http-client])

(defn get-providers
  "See protocol defintion for docstring."
  [this]
  (-> this
      :http-client
      (http/get (base/get-url this "/providers"))))

(defn save-collection
  "Save a collection."
  ([this provider-id native-id metadata]
    (save-collection this provider-id native-id metadata {}))
  ([this provider-id native-id metadata options]
    (-> this
        :http-client
        (http/put (base/get-url this
                                (format "/providers/%s/collections/%s"
                                        provider-id
                                        native-id))
                  metadata
                  (http-util/merge-header options
                                          (base/get-token-header this))))))

(defn save-variable
  "Save a variable."
  ([this provider-id native-id metadata]
    (save-variable this provider-id native-id metadata {}))
  ([this provider-id native-id metadata options]
    (-> this
        :http-client
        (http/put (base/get-url this
                                (format "/providers/%s/variables/%s"
                                        provider-id
                                        native-id))
                  metadata
                  (http-util/merge-header options
                                          (base/get-token-header this))))))

#?(:clj
(def client-behaviour
  "A map of method names to implementations.

  Intended for use by the `extend` protocol function."
  {:get-providers get-providers
   :create-collection save-collection
   :update-collection save-collection
   :create-variable save-variable
   :update-variable save-variable}))
