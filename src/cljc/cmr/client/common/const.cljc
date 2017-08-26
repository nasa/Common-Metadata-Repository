(ns cmr.client.common.const)

(def hosts {
  :prod "https://cmr.earthdata.nasa.gov"
  :uat "https://cmr.uat.earthdata.nasa.gov"
  :sit "https://cmr.sit.earthdata.nasa.gov"
  :local "http://localhost"})

(def deployment-type
  {:prod :service
   :uat :service
   :sit :service
   :local :local})

(def endpoints {
  :access-control {
    :service "/access-control"
    :local ":3011"}
  :ingest {
    :service "/ingest"
    :local ":3002"}
  :search {
    :service "/search"
    :local ":3003"}})

(def default-endpoints {
  :access-control (str (:prod hosts) (get-in endpoints [:access-control :service]))
  :ingest (str (:prod hosts) (get-in endpoints [:ingest :service]))
  :search (str (:prod hosts) (get-in endpoints [:search :service]))})
