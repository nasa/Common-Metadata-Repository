(ns cmr.opendap.const)

;; XXX We should move these to configuration; this would mean that anything
;;     that requires these values would need access to the 'config' component
;;     thus also requiring that the calling function has access to the system
;;     component ...
(def default-api-version "v2")
(def default-dotted-version (str "." default-api-version))
(def default-content-type "json")
(def vendor "cmr-opendap")
(def client-id (format "%s-service" vendor))
(def user-agent
  "CMR OPeNDAP Service/1.0 (+https://github.com/cmr-exchange/cmr-opendap)")
(def datafile-link-rel "http://esipfed.org/ns/fedsearch/1.1/data#")
(def cmr-max-pagesize 2000)


