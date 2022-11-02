(ns cmr.metadata.proxy.const)

;; XXX The following is used as a criteria for extracing data files from
;;     granule metadata. This may change once CMR-4912 is addressed.
;; XXX Update: CMR-4912 has been moved to DURT-153.
(def datafile-link-rel "http://esipfed.org/ns/fedsearch/1.1/data#")

(def default-lon-abs-hi 360.0)
(def default-lat-abs-hi 180.0)
