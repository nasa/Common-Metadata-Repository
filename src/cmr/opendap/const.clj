(ns cmr.opendap.const)

;; XXX We should move these to configuration; this would mean that anything
;;     that requires these values would need access to the 'config' component
;;     thus also requiring that the calling function has access to the system
;;     component ...
(def client-id "cmr-opendap-service")
(def user-agent
  "CMR OPeNDAP Service/1.0 (+https://github.com/cmr-exchange/cmr-opendap)")
;; XXX The following is used as a criteria for extracing data files from
;;     granule metadata. This may change once CMR-4912 is addressed.
(def datafile-link-rel "http://esipfed.org/ns/fedsearch/1.1/data#")

(def default-lon-lo -180.0)
(def default-lon-hi 180.0)
(def default-lat-lo -90.0)
(def default-lat-hi 90.0)

(def default-lon-abs-lo 0.0)
(def default-lon-abs-hi 360.0)
(def default-lat-abs-lo 0.0)
(def default-lat-abs-hi 180.0)
