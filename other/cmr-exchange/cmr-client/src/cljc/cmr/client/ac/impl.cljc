(ns cmr.client.ac.impl
  "This namespace defines the implementation of the CMR access control
  client protocols.

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

(defrecord CMRAccessControlClientData [
  endpoint
  token
  options
  http-client])

(defn get-acls
  "See protocol defintion for docstring."
  ([this http-options]
   (get-acls this {} http-options))
  ([this query-params http-options]
   (-> this
       :http-client
       (http/get (base/get-url this "/acls")
                 (http-util/query+options query-params http-options)))))

(defn get-groups
  "See protocol defintion for docstring."
  ([this http-options]
   (get-groups this {} http-options))
  ([this query-params http-options]
   (-> this
       :http-client
       (http/get (base/get-url this "/groups")
                 (http-util/query+options query-params http-options)))))

(defn get-health
  "See protocol defintion for docstring."
  ([this]
   (get-health this {}))
  ([this http-options]
   (-> this
       :http-client
       (http/get (base/get-url this "/groups")
                 http-options))))

(defn get-permissions
  "See protocol defintion for docstring."
  ([this http-options]
   (get-permissions this {} http-options))
  ([this query-params http-options]
   (-> this
       :http-client
       (http/get (base/get-url this "/permissions")
                 (http-util/query+options query-params http-options)))))

#?(:clj
(def client-behaviour
  "A map of method names to implementations.

  Intended for use by the `extend` protocol function."
  {:get-acls get-acls
   :get-groups get-groups
   :get-health get-health
   :get-permissions get-permissions}))
