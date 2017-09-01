(ns cmr.client.http.core
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
