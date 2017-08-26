(ns cmr.client.common.const)

(def host-prod "https://cmr.earthdata.nasa.gov")
(def host-uat "https://cmr.uat.earthdata.nasa.gov")
(def host-sit "https://cmr.sit.earthdata.nasa.gov")
(def host-local "http://localhost")

(def endpoints {
  :ingest {
    :service "/ingest"
    :local ":3002"}})
