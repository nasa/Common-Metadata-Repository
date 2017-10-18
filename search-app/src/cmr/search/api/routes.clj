(ns cmr.search.api.routes
  (:require
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common.cache :as cache]
   [cmr.search.api.community-usage-metrics :as metrics-api]
   [cmr.search.api.concepts-lookup :as concepts-lookup-api]
   [cmr.search.api.concepts-search :as concepts-search-api]
   [cmr.search.api.humanizer :as humanizers-api]
   [cmr.search.api.keyword :as keyword-api]
   [cmr.search.api.providers :as providers-api]
   [cmr.search.api.services :as services-api]
   [cmr.search.api.tags :as tags-api]
   [cmr.search.api.variables :as variables-api]
   [cmr.search.data.metadata-retrieval.metadata-cache :as metadata-cache]
   [cmr.search.services.health-service :as hs]
   [compojure.core :refer :all])
  (:require
   ;; These must be required here to make multimethod implementations available.
   ;; XXX This is not a good pattern for large software systems; we need to
   ;;     find a different way to accomplish this goal ... possibly use protocols
   ;;     instead.
   [cmr.search.data.elastic-results-to-query-results]
   ;;
   ;; Result handlers - required here to avoid circular dependency in query service
   [cmr.search.results-handlers.atom-json-results-handler]
   [cmr.search.results-handlers.atom-results-handler]
   [cmr.search.results-handlers.csv-results-handler]
   [cmr.search.results-handlers.kml-results-handler]
   [cmr.search.results-handlers.metadata-results-handler]
   [cmr.search.results-handlers.opendata-results-handler]
   [cmr.search.results-handlers.reference-results-handler]
   [cmr.search.results-handlers.tags-json-results-handler]
   [cmr.search.results-handlers.timeline-results-handler]
   [cmr.search.results-handlers.umm-json-results-handler]
   [cmr.search.results-handlers.variables-json-results-handler]
   [cmr.search.results-handlers.variables-umm-json-results-handler]
   ;;
   ;; ACL support - required here to avoid circular dependencies
   [cmr.search.services.acls.collection-acls]
   [cmr.search.services.acls.granule-acls]))

(defn build-routes [system]
  (let [relative-root-url (get-in system [:public-conf :relative-root-url])]
    (routes
      (context relative-root-url []
        ;; Add routes for tagging
        tags-api/tag-api-routes

        ;; Add routes for variable association
        variables-api/variable-api-routes

        ;; Add routes for service association
        services-api/service-api-routes

        ;; Add routes for humanizers
        humanizers-api/humanizers-routes

        ;; Add routes for community usage metrics
        metrics-api/community-usage-metrics-routes

        ;; Add route(s) for the concepts lookup endpoint
        concepts-lookup-api/concepts-routes

        ;; Find concepts
        concepts-search-api/search-routes

        ;; Granule timeline
        concepts-search-api/granule-timeline-routes

        ;; Deleted concepts
        concepts-search-api/find-deleted-concepts-routes

        ;; AQL search - xml
        concepts-search-api/aql-search-routes

        ;; Provider holdings
        providers-api/holdings-routes

        ;; Resets the application back to it's initial state.
        (POST "/reset"
          {ctx :request-context}
          (acl/verify-ingest-management-permission ctx)
          (cache/reset-caches ctx)
          {:status 204})

        ;; Add routes for retrieving GCMD keywords
        keyword-api/keyword-api-routes

        ;; Add routes for managing jobs
        (common-routes/job-api-routes
         (routes
           (POST "/refresh-collection-metadata-cache"
             {ctx :request-context}
             (acl/verify-ingest-management-permission ctx :update)
             (metadata-cache/refresh-cache ctx)
             {:status 200})))

        ;; Add routes for accessing caches
        common-routes/cache-api-routes

        ;; Add routes for checking health of the application
        (common-health/health-api-routes hs/health)

        ;; Add routes for enabling/disabling application
        (common-enabled/write-enabled-api-routes
         #(acl/verify-ingest-management-permission % :update))

        ;; Add routes for searching tiles
        concepts-search-api/tiles-routes))))
