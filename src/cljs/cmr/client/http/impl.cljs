(ns cmr.client.http.impl
  (:require
   [cljs-http.client :as http]
   [cljs.core.async :as async]
   [cmr.client.common.util :as util])
  (:refer-clojure :exclude [get])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn make-channel
  [client]
  (if (get-in client [:parent-client-options :return-body?])
    (async/promise-chan (map :body))
    (async/promise-chan)))

(defn get-default-options
  [client]
  {:with-credentials? false})

(defn make-http-options
  [client call-options]
  (merge (get-default-options client)
         {:channel (make-channel client)}
         (:http-options client)
         call-options))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord HTTPClientData [
  parent-client-options
  http-options])

(defn get
  [this url options]
  (http/get url (make-http-options this options)))
