(ns cmr.client.search.impl
  "This namespace defines the implementation of the CMR search client
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

(defrecord CMRSearchClientData [
  endpoint
  token
  options
  http-client])

(defn create-variable-association
  ([this concept-id collection-data]
    (create-variable-association this concept-id collection-data {}))
  ([this concept-id collection-data http-options]
    (create-variable-association
     this concept-id collection-data {} http-options))
  ([this concept-id collection-data query-params http-options]
    (-> this
        :http-client
        (http/post (base/get-url this
                                 (format "/variables/%s/associations"
                                         concept-id))
                   collection-data
                   (http-util/merge-header http-options
                                           (base/get-token-header this))))))

(defn get-collections
  "See protocol defintion for docstring."
  ([this]
   (get-collections this {} {}))
  ([this http-options]
   (get-collections this {} http-options))
  ([this query-params http-options]
   (-> this
       :http-client
       (http/get (base/get-url this "/collections")
                 (-> query-params
                     (http-util/query+options http-options)
                     (http-util/merge-header (base/get-token-header this)))))))

(defn get-concept
  "See protocol defintion for docstring."
  ([this concept-id]
    (get-concept this concept-id {}))
  ([this concept-id http-options]
   (-> this
       :http-client
       (http/get (base/get-url this (str "/concepts/" concept-id))
                 http-options)))
  ([this concept-id revision-id http-options]
   (-> this
       :http-client
       (http/get (base/get-url this (str "/concepts/"
                                         concept-id
                                         "/"
                                         revision-id))
                 (http-util/merge-header http-options
                                         (base/get-token-header this))))))

(defn get-granules
  "See protocol defintion for docstring."
  ([this http-options]
   (get-granules this {} http-options))
  ([this query-params http-options]
   (-> this
       :http-client
       (http/get (base/get-url this "/granules")
                 (http-util/query+options query-params http-options)))))

(defn get-humanizers
  "See protocol defintion for docstring."
  ([this]
   (get-humanizers this {}))
  ([this http-options]
   (-> this
       :http-client
       (http/get (base/get-url this "/humanizers")
                 http-options))))

(defn get-tag
  "See protocol defintion for docstring."
  ([this tag-id http-options]
   (get-tag this tag-id {} http-options))
  ([this tag-id query-params http-options]
   (-> this
       :http-client
       (http/get (base/get-url this (str "/tag/" tag-id))
                 (http-util/query+options query-params http-options)))))

(defn get-tags
  "See protocol defintion for docstring."
  ([this http-options]
   (get-tags this {} http-options))
  ([this query-params http-options]
   (-> this
       :http-client
       (http/get (base/get-url this "/tags")
                 (http-util/query+options query-params http-options)))))

(defn get-tiles
  "See protocol defintion for docstring."
  ([this http-options]
   (get-tiles this {} http-options))
  ([this query-params http-options]
   (-> this
       :http-client
       (http/get (base/get-url this "/tiles")
                 (http-util/query+options query-params http-options)))))

(defn get-variables
  "See protocol defintion for docstring."
  ([this]
   (get-variables this {}))
  ([this http-options]
   (get-variables this {} http-options))
  ([this query-params http-options]
   (-> this
       :http-client
       (http/get (base/get-url this "/variables")
                 (-> query-params
                     (http-util/query+options http-options)
                     (http-util/merge-header (base/get-token-header this)))))))

#?(:clj
(def client-behaviour
  "A map of method names to implementations.

  Intended for use by the `extend` protocol function."
  {:create-variable-association create-variable-association
   :get-collections get-collections
   :get-concept get-concept
   :get-granules get-granules
   :get-humanizers get-humanizers
   :get-tag get-tag
   :get-tags get-tags
   :get-tiles get-tiles
   :get-variables get-variables}))
