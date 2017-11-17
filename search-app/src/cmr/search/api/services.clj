(ns cmr.search.api.services
  "Defines the API for associating/dissociating services with collections in the CMR."
  (:require
   [cmr.search.api.association :as association]
   [compojure.core :refer :all]
   [compojure.route :as route]))

(def service-api-routes
  (context "/services" []

    ;; Search for services route is defined in routes.clj

    ;; service associations routes
    (context "/:service-concept-id" [service-concept-id]
      (context "/associations" []

        ;; Associate a service with a list of collections
        (POST "/" {:keys [request-context headers body]}
          (association/associate-concept-to-collections
           request-context headers (slurp body) :service service-concept-id))

        ;; Dissociate a service from a list of collections
        (DELETE "/" {:keys [request-context headers body]}
          (association/dissociate-concept-from-collections
           request-context headers (slurp body) :service service-concept-id))))))
