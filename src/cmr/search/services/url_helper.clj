(ns cmr.search.services.url-helper
  "Provides functions to get useful urls"
  (:require [cmr.transmit.config :as conf]))

(defn location-root
  "Returns the url root for reference location"
  []
  ;; TODO we will configure sparate (external) config parameters for this.
  ;; For now, just use the internal configuration.
  (let [{:keys [host port]} (get (conf/app-conn-info) :search)]
    (format "http://%s:%s/concepts/" host port)))