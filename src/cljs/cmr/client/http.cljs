(ns cmr.client.http
  (:require
   [cljs-http.client :as http]
   [cljs.core.async :as async]
   [cmr.client.ingest.util :as util])
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
;;;   Protocols &tc.   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol HTTPClientAPI
  "An interface for ClojureScript HTTP clients."
  (^:export get [this url] [this url opts])
  (^:export head [this url] [this url opts])
  (^:export put [this url] [this url opts])
  (^:export post [this url] [this url opts])
  (^:export delete [this url] [this url opts])
  (^:export copy [this url] [this url opts])
  (^:export move [this url] [this url opts])
  (^:export patch [this url] [this url opts])
  (^:export options [this url] [this url opts]))

(defrecord HTTPClientData [
  parent-client-options
  http-options])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend-type HTTPClientData
  HTTPClientAPI
  (get
    ([this url]
     (get this url {}))
    ([this url options]
     (http/get url (make-http-options this options))))
  (head
    ([this url]
      (head this url {}))
    ([this url options]
      :not-implemented))
  (put
    ([this url]
      (put this url {}))
    ([this url options]
      :not-implemented))
  (post
    ([this url]
      (post this url {}))
    ([this url options]
      :not-implemented))
  (delete
    ([this url]
      (delete this url {}))
    ([this url options]
      :not-implemented))
  (copy
    ([this url]
      (copy this url {}))
    ([this url options]
      :not-implemented))
  (move
    ([this url]
      (move this url {}))
    ([this url options]
      :not-implemented))
  (patch
    ([this url]
      (patch this url {}))
    ([this url options]
      :not-implemented))
  (options
    ([this url]
      (options this url {}))
    ([this url options]
      :not-implemented)))

(defn ^:export create-client
  ([]
   (create-client {}))
  ([http-options]
   (create-client {} http-options))
  ([parent-client-options http-options]
   (->HTTPClientData parent-client-options
                     http-options)))
