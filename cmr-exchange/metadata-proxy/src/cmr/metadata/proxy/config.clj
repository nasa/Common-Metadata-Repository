(ns cmr.metadata.proxy.config
  (:require
   [clojure.string :as string]
   [cmr.exchange.common.config :as config]
   [taoensso.timbre :as log])
  (:import
    (clojure.lang Keyword)))

(def config-file "config/cmr-metadata-proxy/config.edn")

(def data #'config/data)

(defn service-keys
  "We need to special-case two-word services, as split by the environment and
  system property parser above."
  [^Keyword service]
  (case service
    :service-bridge [:service :bridge]
    [service]))

(defn service->base-url
  [service]
  (format "%s://%s:%s"
          (or (:protocol service) "https")
          (:host service)
          (or (:port service) "443")))

(defn service->url
  [service]
  (format "%s%s"
          (service->base-url service)
          (or (get-in service [:relative :root :url])
              (:context service)
              "/")))

(defn service->base-public-url
  [service]
  (let [protocol (or (get-in service [:public :protocol]) "https")
        host (get-in service [:public :host])]
    (if (= "https" protocol)
      (format "%s://%s" protocol host)
      (format "%s://%s:%s" protocol host (get-in service [:public :port])))))

(defn service->public-url
  [service]
  (format "%s%s"
          (service->base-public-url service)
          (or (get-in service [:relative :root :url])
              (:context service)
              "/")))
