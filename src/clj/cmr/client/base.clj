(ns cmr.client.base
  "A base client for the CMR services.

  This client defines basic options, data, and methods that all clients will
  share in common.

  This Clojure namespace uses the generic protocol and implementation that is
  also shared by ClojureScript. Clojure-specific code is defined here."
 (:require
  [cmr.client.base.impl :as impl]
  [cmr.client.base.protocol :as api]
  [potemkin :refer [import-vars]])
 (:import
  (cmr.client.base.impl CMRClientData)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Protocols &tc.   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(import-vars
  [cmr.client.base.protocol
    get-url
    get-token
    get-token-header])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend CMRClientData
        api/CMRClientAPI
        impl/client-behaviour)

