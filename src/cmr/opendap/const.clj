(ns cmr.opendap.const)

;; XXX We should move these to configuration; this would mean that anything
;;     that requires these values would need access to the 'config' component
;;     thus also requiring that the calling function has access to the system
;;     component ...
(def client-id "cmr-service-bridge")
(def user-agent
  "CMR Service-Bridge Service/1.0 (+https://github.com/cmr-exchange/cmr-service-bridge)")
