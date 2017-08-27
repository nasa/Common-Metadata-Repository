(ns cmr.client.ingest.impl
 (:require
  [cmr.client.http.core :as http]
  [clojure.core.async :as async]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Records &tc.   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord CMRIngestClientOptions [
  return-body?
  connection-manager])

(defrecord CMRIngestClientData [
  endpoint
  options
  http-client])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-url
  [this segment]
  (str (:endpoint this) segment))

; (defn- get-providers
;   [this]
;   (-> this
;       :http-client
;       (http/get (get-url this "/providers"))
;       (async/<!!)))

(defn- get-providers
  [this]
  (-> this
      :http-client
      (http/get (get-url this "/providers"))))

(def client-behaviour
  {:get-url get-url
   :get-providers get-providers})
