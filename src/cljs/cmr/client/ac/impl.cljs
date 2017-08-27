(ns cmr.client.ac.impl
 (:require
  [cmr.client.base.impl :as base]
  [cmr.client.http :as http]))

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
