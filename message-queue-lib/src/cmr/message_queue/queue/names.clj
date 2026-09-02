(ns cmr.message-queue.queue.names
  "Shared normalization for AWS messaging resource names."
  (:require
   [clojure.string :as string]
   [cmr.message-queue.config :as config]))

(defn normalize-queue-name
  "Normalizes a CMR queue or topic name for AWS."
  [queue-name]
  (let [prefix (str "gsfc-eosdis-cmr-" (config/app-environment))
        prefix-regex (re-pattern (str "^(" prefix "-)*"))]
    (-> queue-name
        (string/replace "." "_")
        (string/replace "cmr_" "")
        (string/replace prefix-regex (str prefix "-")))))
