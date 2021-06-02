(ns cmr.search.api.tools
  "Defines the API for associating/dissociating tools with collections in the CMR."
  (:require
   [cmr.search.api.association :as association]
   [compojure.core :refer :all]
   [compojure.route :as route]))

(def tool-api-routes
  (context "/tools" []

    ;; Search for tools route is defined in routes.clj

    ;; tool associations routes
    (context "/:tool-concept-id" [tool-concept-id]
      (context "/associations" []

        ;; Associate a tool with a list of collections
        (POST "/" {:keys [request-context headers body]}
          (association/associate-concept-to-collections
           request-context headers (slurp body) :tool tool-concept-id))

        ;; Dissociate a tool from a list of collections
        (DELETE "/" {:keys [request-context headers body]}
          (association/dissociate-concept-from-collections
           request-context headers (slurp body) :tool tool-concept-id))))))
