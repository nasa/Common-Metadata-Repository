(ns cmr.audit-simulator.api.routes
  "Route metadata for scanners. This simulator does not start an HTTP server by default."
  (:require
   [cmr.audit-simulator.metadata :as metadata]
   [cmr.audit-simulator.reliability :as reliability]))

(def routes
  [{:method :get
    :path "/audit-simulator/report"
    :handler :cmr.audit-simulator.runner/report
    :description "Aggregated simulated audit findings."}
   {:method :get
    :path "/audit-simulator/metadata/errors"
    :handler :cmr.audit-simulator.metadata/validation-report
    :description "Sample metadata validation errors and suggested fixes."}
   {:method :get
    :path "/audit-simulator/reliability/endpoints"
    :handler :cmr.audit-simulator.reliability/risky-endpoints
    :description "Synthetic p99, error-rate, and cache-hit findings."}])

(defn route-report []
  {:routes routes
   :metadata-errors (remove :valid? (metadata/validation-report))
   :risky-endpoints (reliability/risky-endpoints)})
