(ns cmr.client.ac.impl
 (:require
   [cmr.client.http.core :as http]
   #?(:clj [cmr.client.base :as base]
      :cljs [cmr.client.base.impl :as base])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord CMRAccessControlClientData [
  endpoint
  options
  http-client])

(defn get-acls
  ([this http-options]
   (get-acls this {} http-options))
  ([this query-params http-options]
   (-> this
       :http-client
       (http/get (base/get-url this "/acls")
                 (merge {:query-params query-params}
                        http-options)))))

(defn get-groups
  ([this http-options]
   (get-groups this {} http-options))
  ([this query-params http-options]
   (-> this
       :http-client
       (http/get (base/get-url this "/groups")
                 (merge {:query-params query-params}
                        http-options)))))

(defn get-health
  ([this]
   (get-health this {}))
  ([this http-options]
   (-> this
       :http-client
       (http/get (base/get-url this "/groups")
                 http-options))))

(defn get-permissions
  ([this http-options]
   (get-permissions this {} http-options))
  ([this query-params http-options]
   (-> this
       :http-client
       (http/get (base/get-url this "/permissions")
                 (merge {:query-params query-params}
                        http-options)))))

#?(:clj
(def client-behaviour
  {:get-acls get-acls
   :get-groups get-groups
   :get-health get-health
   :get-permissions get-permissions}))
