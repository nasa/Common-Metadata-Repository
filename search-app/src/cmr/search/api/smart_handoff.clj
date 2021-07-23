(ns cmr.search.api.smart-handoff
  "Defines the API for retrieving smart handoff schemas defined in CMR."
  (:require
   [cmr.search.services.smart-handoff-service :as smart-handoff-service]
   [compojure.core :refer :all]
   [compojure.route :as route]))

(def smart-handoff-routes
  (context ["/smart-handoff/:client"] [client]
    (GET "/" {:keys [request-context]}
      (smart-handoff-service/retrieve-schema request-context client))))
