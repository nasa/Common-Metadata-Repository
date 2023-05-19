(ns cmr.search.services.provider-service
  "Functions for searching operations on providers. All functions return
  the underlying Metadata DB API clj-http response which can be used
  as a Ring response."
  (:require
   [cmr.common.services.errors :as errors]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.common.util :as util]))

(defn- successful?
  "Returns true if the mdb response was successful."
  [response]
  (<= 200 (:status response) 299))

;; todo we should update this is to read from elastic-search
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
  (util/tee (mdb/get-providers-raw context)))

(defn get-providers
  "Get a list of provider ids"
  [context]
  (println "🚀 Being called from the search services ep")
  (mdb/get-providers context))
