(ns cmr.client.util
  (:require
   [cmr.client.constants :as constants]
   #?(:clj [clojure.data.json :as json])))

(defn parse-endpoint
  [endpoint endpoints]
  (if (string? endpoint)
    endpoint
    (case endpoint
      :prod (str constants/host-prod (:service endpoints))
      :uat (str constants/host-uat (:service endpoints))
      :sit (str constants/host-sit (:service endpoints))
      :local (str constants/host-local (:local endpoints)))))

#?(:clj
(defn read-json-str
  [string-data]
  (json/read-str string-data :key-fn keyword)))

#?(:cljs
(defn read-json-str
  [string-data]
  (js->clj string-data)))
