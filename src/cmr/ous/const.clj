(ns cmr.ous.const
  (:require
   [cmr.metadata.proxy.const :as const]))

;; XXX We should move these to configuration; this would mean that anything
;;     that requires these values would need access to the 'config' component
;;     thus also requiring that the calling function has access to the system
;;     component ...
(def client-id "cmr-ous")
(def user-agent
  "CMR Service-Bridge/1.0 (+https://github.com/cmr-exchange/cmr-ous-plugin)")

(def default-lon-lo -180.0)
(def default-lon-hi 180.0)
(def default-lat-lo -90.0)
(def default-lat-hi 90.0)

(def default-lon-abs-hi const/default-lon-abs-hi)
(def default-lat-abs-hi const/default-lat-abs-hi)

(def default-lon-abs-lo 0.0)
(def default-lat-abs-lo 0.0)

(def default-lat-lon-resolution 1)
