(ns cmr.search.services.provider-service
  "Functions for searching operations on providers. All functions return
  the underlying Metadata DB API clj-http response which can be used
  as a Ring response."
  (:require
   [cmr.transmit.metadata-db :as mdb]
   [cmr.common.util :as util]))

(defn read-provider
  "Read a provider."
  [context provider-id]
  (def mycontext context)
  (mdb/read-provider context provider-id))

(defn read-providers
  "Read all of the providers"
  [context]
  (mdb/read-providers context))

(defn get-providers-raw
  "Get a list of provider ids in raw http response."
  [context]
  (mdb/get-all-providers context))

(defn get-providers
  "Get a list of provider ids"
  [context]
  (println "🚀 Being called from the search services ep")
  (mdb/get-providers context))
