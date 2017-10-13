(ns cmr.search.api.variables
  "Defines the API for associating/dissociating variables with collections in the CMR."
  (:require
   [cmr.search.api.association :as association]
   [compojure.core :refer :all]
   [compojure.route :as route]))

(def variable-api-routes
  (context "/variables" []

    ;; Search for variables route is defined in routes.clj

    ;; variable associations routes
    (context "/:variable-concept-id" [variable-concept-id]
      (context "/associations" []

        ;; Associate a variable with a list of collections
        (POST "/" {:keys [request-context headers body]}
          (association/associate-concept-to-collections
           request-context headers (slurp body) :variable variable-concept-id))

        ;; Dissociate a variable from a list of collections
        (DELETE "/" {:keys [request-context headers body]}
          (association/dissociate-concept-from-collections
           request-context headers (slurp body) :variable variable-concept-id))))))
