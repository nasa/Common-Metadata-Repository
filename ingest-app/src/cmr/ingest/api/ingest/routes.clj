(ns cmr.ingest.api.ingest.routes
  "Defines the HTTP URL routes for validating and ingesting concepts."
  (:require
   [cmr.ingest.api.ingest.core :refer [set-default-error-format]]
   [cmr.ingest.api.ingest.bulk :as bulk]
   [cmr.ingest.api.ingest.collections :as collections]
   [cmr.ingest.api.ingest.granules :as granules]
   [cmr.ingest.api.ingest.variables :as variables]
   [compojure.core :refer :all]))

(def provider-routes
  "Defines the routes for provider ingest, validation, and deletion
  operations."
  (set-default-error-format
    :xml
    (context "/providers/:provider-id" [provider-id]

      (context ["/validate/collection/:native-id" :native-id #".*$"] [native-id]
        (POST "/"
              request
              (collections/validate-collection provider-id native-id request)))
      (context ["/collections/:native-id" :native-id #".*$"] [native-id]
        (PUT "/"
             request
             (collections/ingest-collection provider-id native-id request))
        (DELETE "/"
                request
                (collections/delete-collection provider-id native-id request)))

      (context ["/validate/granule/:native-id" :native-id #".*$"] [native-id]
        (POST "/"
              request
              (granules/validate-granule provider-id native-id request)))

      (context ["/granules/:native-id" :native-id #".*$"] [native-id]
        (PUT "/"
             request
             (granules/ingest-granule provider-id native-id request))
        (DELETE "/"
                request
                (granules/delete-granule provider-id native-id request)))

      (context "/bulk-update/collections" []
        (POST "/"
              request
              (bulk/bulk-update-collections provider-id request))
        (GET "/status" ; Gets all tasks for provider
             request
             (bulk/get-provider-tasks provider-id request))
        (GET "/status/:task-id"
             [task-id :as request]
             (bulk/get-provider-task-status provider-id task-id request))))))

(def variable-routes
  "Defines the routes for UMM variable ingest, validation, and deletion
  operations."
  (context "/variables" []
    (POST "/"
          {:keys [request-context headers body]}
          (variables/create-variable request-context headers body))
    (context "/:variable-key" [variable-key]
      (PUT "/"
           {:keys [request-context headers body]}
           (variables/update-variable
            request-context headers body variable-key)))))

(def ingest-routes
  "Combined ingest routes."
  (routes provider-routes
          variable-routes))
