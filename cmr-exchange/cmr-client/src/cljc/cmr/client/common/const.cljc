(ns cmr.client.common.const
  "Constants defined and/or used by the CMR client.")

(def hosts
  "A map of the supported CMR hosts, where the key is the target deployment
  environment."
  {:prod "https://cmr.earthdata.nasa.gov"
   :uat "https://cmr.uat.earthdata.nasa.gov"
   :sit "https://cmr.sit.earthdata.nasa.gov"
   :local "http://localhost"})

(def deployment-type
  "A map of the CMR deployment types where the key is the target deployment
  environment and the value is the deployment type."
  {:prod :service
   :uat :service
   :sit :service
   :local :local})

(def endpoints
  "A map of CMR service endpoints for each deployment type."
  {:access-control
    {:service "/access-control"
     :local ":3011"}
   :graph
     {:service "/graph"
      :local ":3012"}
   :ingest
    {:service "/ingest"
     :local ":3002"}
   :search
    {:service "/search"
     :local ":3003"}
   ;; Local-only endpoints
   :bootstrap {:local ":3006"}
   :indexer {:local ":3004"}
   :metadata-db {:local ":3001"}})

(def default-environment-type
  "Default deployment type for the CMR client."
  :local)

(def default-endpoints
  "A map of the CMR client's default local endpoints."
  {:access-control (str (default-environment-type hosts)
                        (get-in endpoints [:access-control :local]))
   :graph (str (default-environment-type hosts)
                (get-in endpoints [:graph :local]))
   :ingest (str (default-environment-type hosts)
                (get-in endpoints [:ingest :local]))
   :search (str (default-environment-type hosts)
                (get-in endpoints [:search :local]))})
