(ns cmr.client.search.core
 (:require
  [cmr.client.constants :as constants]
  [cmr.client.util :as util]
  [cmr.client.http.core :as http]))

(def endpoints
  {:service "/search"
   :local ":3003"})

(def default-endpoint (str constants/host-prod (:service endpoints)))

