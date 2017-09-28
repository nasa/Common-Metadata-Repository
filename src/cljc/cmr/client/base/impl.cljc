(ns cmr.client.base.impl
  "This namespace defines the implementation of the base CMR client protocols.

  Note that the implementation includes the definitions of the data records
  used for storing client-specific state.

  It is not expected that application developers who want to use the CMR client
  will ever use this namespace directly. It is indended for use by the three
  CMR service API clients."
 (:require
   [cmr.client.http.util :as http-util]
   [cmr.client.http.core :as http]
   #?(:clj [clj-http.conn-mgr :as conn-mgr])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord CMRClientData [
  endpoint
  token])

(defn get-url
  [this segment]
  (str (:endpoint this) segment))

(defn get-token
  [this]
  (:token this))

(defn get-token-header
  [this]
  (when-let [token (get-token this)]
    {"echo-token" token}))

#?(:clj
(def client-behaviour
  {:get-url get-url
   :get-token get-token
   :get-token-header get-token-header}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Client Options   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord CMRClientOptions [
  return-body?
  connection-manager])

#?(:clj
(defn create-options
  "A constructor for client options, selecting legal keys from the passed
  options map to instantiate the options record."
  [options]
  (->CMRClientOptions
    (:return-body? options)
    (conn-mgr/make-reusable-conn-manager
     ;; Use the same defaults that the `with-connection-pool` uses
     {:timeout 5
      :threads 4}))))

#?(:cljs
(defn create-options
  "A constructor for client options, selecting legal keys from the passed
  options map to instantiate the options record."
  [options]
  (let [options (if (object? options)
                 (js->clj options :keywordize-keys true)
                 options)]
    (->CMRClientOptions (:return-body? options) nil))))
