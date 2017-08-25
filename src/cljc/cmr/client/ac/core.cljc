(ns cmr.client.ac.core
 (:require
  [cmr.client.constants :as constants]
  [cmr.client.util :as util]
  [cmr.client.http.core :as http]))

(def endpoints
  {:service "/access-control"
   :local ":3011"})

(def default-endpoint (str constants/host-prod (:service endpoints)))

