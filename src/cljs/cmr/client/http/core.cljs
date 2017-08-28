(ns cmr.client.http.core
  (:require
   [cljs.core.async :as async]
   [cmr.client.common.util :as util]
   [cmr.client.http.impl :as http :refer [->HTTPClientData HTTPClientData]])
  (:refer-clojure :exclude [get])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend-type HTTPClientData
  HTTPClientAPI
  (get
    ([this url]
     (get this url {}))
    ([this url options]
     (http/get this url options)))
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constrcutor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:export create-client
  (util/create-http-client-constructor
    #'cmr.client.http.core/create-client
    ->HTTPClientData))
