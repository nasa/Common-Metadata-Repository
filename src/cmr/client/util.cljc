(ns cmr.client.util
  (:require [cmr.client.const :as const]))

(defn parse-endpoint
  [endpoint endpoints]
  (if (string? endpoint)
    endpoint
    (case endpoint
      :prod (str const/host-prod (:service endpoints))
      :uat (str const/host-uat (:service endpoints))
      :sit (str const/host-sit (:service endpoints))
      :local (str const/local (:local endpoints)))))
