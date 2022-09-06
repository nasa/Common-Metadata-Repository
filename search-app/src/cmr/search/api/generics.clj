(ns cmr.search.api.generics
  "Defines the API for associating/dissociating generic concepts in the CMR."
  (:require
   [cmr.search.api.generic-association :as generic-association]
   [compojure.core :refer :all]))

(def generic-api-routes
  (context "/associate" []
    ;; generic associations routes
    (context "/:concept-id" [concept-id]
      (context "/:revision-id" [revision-id]
          ;; Associate a generic concept with a list of concepts
          (POST "/" {:keys [request-context headers body]}
            (generic-association/associate-concept-to-concepts
             request-context headers (slurp body) concept-id revision-id))
          ;; Dissociate a generic concept from a list of concepts 
          (DELETE "/" {:keys [request-context headers body]}
            (generic-association/dissociate-concept-from-concepts
             request-context headers (slurp body) concept-id revision-id)))
      ;; Associate a generic concept with a list of concepts
      (POST "/" {:keys [request-context headers body]}
        (generic-association/associate-concept-to-concepts
         request-context headers (slurp body) concept-id nil))
      ;; Dissociate a generic concept from a list of concepts
      (DELETE "/" {:keys [request-context headers body]}
        (generic-association/dissociate-concept-from-concepts
         request-context headers (slurp body) concept-id nil)))))

