(ns cmr.search.services.provider-service
  "Functions for searching operations on providers. All functions return
  the underlying Metadata DB API clj-http response which can be used
  as a Ring response."
  (:require
   [cmr.common.util :as util]
   [cmr.transmit.metadata-db :as mdb]))

(defn read-provider
  "Read a provider."
  [context provider-id]
  (util/time-execution (mdb/read-provider context provider-id)))

(defn get-providers-raw
  "Get a list of provider ids in raw http response."
  [context]
  (util/time-execution (mdb/get-all-providers context)))
