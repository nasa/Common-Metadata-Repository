(ns cmr.client.ac.core
 (:require
  [cmr.client.const :as const]
  [cmr.client.util :as util]
  [cmr.client.http.core :as http]))

(def endpoints
  {:service "/access-control"
   :local ":3011"})

(def default-endpoint (str const/host-prod (:service endpoints)))

