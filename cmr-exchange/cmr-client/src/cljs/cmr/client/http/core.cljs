(ns cmr.client.http.core
  "A ClojureScript HTTP client API for use by the CMR service clients."
  (:require
   [cljs.core.async :as async]
   [cmr.client.common.util :as util]
   [cmr.client.http.impl :as http :refer [->HTTPClientData HTTPClientData]]
   [cmr.client.http.protocol :refer [HTTPClientAPI]])
  (:refer-clojure :exclude [get])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [cmr.client.common.util :refer [import-vars]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Protocols &tc.   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(import-vars
  [cmr.client.http.protocol
    get
    head
    put
    post
    delete
    copy
    move
    patch
    options])

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
      (http/head this url options)))
  (put
    ([this url]
      (put this url {}))
    ([this url data]
      (put this url data {}))
    ([this url data options]
      (http/put this url data options)))
  (post
    ([this url]
      (post this url {}))
    ([this url data]
      (post this url data {}))
    ([this url data options]
      (http/post this url data options)))
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
  "HTTP client constructor."
  (util/create-http-client-constructor
    #'cmr.client.http.core/create-client
    ->HTTPClientData))
