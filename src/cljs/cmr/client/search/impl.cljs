(ns cmr.client.search.impl
 (:require
  [cmr.client.base.impl :as base]
  [cmr.client.http :as http]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord CMRSearchClientData [
  endpoint
  options
  http-client])

(defn get-collections
  ([this]
   (get-collections this {} {}))
  ([this http-options]
   (get-collections this {} http-options))
  ([this query-params http-options]
   (-> this
       :http-client
       (http/get (base/get-url this "/collections")
                 (merge {:query-params query-params}
                        http-options)))))

(defn get-concept
  ([this concept-id http-options]
   (-> this
       :http-client
       (http/get (base/get-url this (str "/concept/" concept-id))
                 http-options)))
  ([this concept-id revision-id http-options]
   (-> this
       :http-client
       (http/get (base/get-url this (str "/concept/"
                                         concept-id
                                         "/"
                                         revision-id))
                 http-options))))

(defn get-granules
  ([this http-options]
   (get-granules this {} http-options))
  ([this query-params http-options]
   (-> this
       :http-client
       (http/get (base/get-url this "/granules")
                 (merge {:query-params query-params}
                        http-options)))))

(defn get-humanizers
  ([this]
   (get-humanizers this {}))
  ([this http-options]
   (-> this
       :http-client
       (http/get (base/get-url this "/humanizers")
                 http-options))))

(defn get-tag
  ([this tag-id http-options]
   (get-tag this tag-id {} http-options))
  ([this tag-id query-params http-options]
   (-> this
       :http-client
       (http/get (base/get-url this ("/tag/" tag-id))
                 (merge {:query-params query-params}
                        http-options)))))

(defn get-tags
  ([this http-options]
   (get-tags this {} http-options))
  ([this query-params http-options]
   (-> this
       :http-client
       (http/get (base/get-url this "/tags")
                 (merge {:query-params query-params}
                        http-options)))))

(defn get-tiles
  ([this http-options]
   (get-tiles this {} http-options))
  ([this query-params http-options]
   (-> this
       :http-client
       (http/get (base/get-url this "/tiles")
                 (merge {:query-params query-params}
                        http-options)))))

(defn get-variables
  ([this http-options]
   (get-variables this {} http-options))
  ([this query-params http-options]
   (-> this
       :http-client
       (http/get (base/get-url this "/variables")
                 (merge {:query-params query-params}
                        http-options)))))
