(ns cmr.client.graph.impl
  "This namespace defines the implementation of the CMR graph client
  protocols.

  Note that the implementation includes the definitions of the data records
  used for storing client-specific state."
 (:require
   [cmr.client.http.util :as http-util]
   [cmr.client.http.core :as http]
   #?(:clj [cmr.client.base :as base]
      :cljs [cmr.client.base.impl :as base])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord CMRGraphClientData [
  endpoint
  token
  options
  http-client])

(defn get-movie
  "See protocol defintion for docstring."
  ([this query-str]
   (get-movie this query-str {}))
  ([this query-str http-options]
   (-> this
       :http-client
       (http/get (base/get-url this (str "/demo/movie/search?q=" query-str))
                 (http-util/merge-header
                   http-options (base/get-token-header this))))))

(defn get-collection-url-relation
  "See protocol defintion for docstring."
  ([this concept-id]
   (get-collection-url-relation this concept-id {}))
  ([this concept-id http-options]
   (-> this
       :http-client
       (http/get
        (base/get-url this
                      (str "/relationships/related-urls/collections/"
                           concept-id))
        (http-util/merge-header http-options (base/get-token-header this))))))

#?(:clj
(def client-behaviour
  "A map of method names to implementations.

  Intended for use by the `extend` protocol function."
  {;; Demo Graph API
   :get-movie get-movie
   ;; Actual CMR Graph API
   :get-collection-url-relation get-collection-url-relation}))
