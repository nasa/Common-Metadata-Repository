(ns cmr.client.search.core
 (:require
  [cmr.client.const :as const]
  [cmr.client.util :as util]
  [cmr.client.http.core :as http]))

(def endpoints
  {:service "/search"
   :local ":3003"})

(def default-endpoint (str const/host-prod (:service endpoints)))

