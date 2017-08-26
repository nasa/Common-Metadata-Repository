(ns cmr.client.common.util
  (:require
   [cmr.client.common.const :as const]
   #?(:clj [clojure.core.async :as async :refer [go go-loop]]
      :cljs [cljs.core.async :as async]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))

(defn parse-endpoint
  [endpoint endpoints]
  (if (string? endpoint)
    endpoint
    (case endpoint
      :prod (str const/host-prod (:service endpoints))
      :uat (str const/host-uat (:service endpoints))
      :sit (str const/host-sit (:service endpoints))
      :local (str const/host-local (:local endpoints)))))

(defn ^:export with-callback
  [chan callback]
  (go-loop []
    (if-let [response (async/<! chan)]
      (callback response)
      (recur))))
