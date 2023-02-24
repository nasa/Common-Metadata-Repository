(ns cmr.efs.connection
  "Contains a record definition that implements the ConcpetStore and Lifecycle protocols
  backed by an EFS instance."
  (:require
   [clj-time.coerce :as cr]
   [clojure.java.jdbc :as j]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.date-time-parser :as p]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.common.services.health-helper :as hh]))

(defrecord EfsStore
           [;; The database spec.
            spec

   ;; The database pool of connections
            datasource]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; CMR Component Implementation
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  lifecycle/Lifecycle

  (start [this system]
    ())

  (stop [this system]
    ()))

(def create-connection
  "Returns a record for interfacing with EFS"
  []
  (->EfsStore nil nil))