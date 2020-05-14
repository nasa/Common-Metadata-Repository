(ns cmr.ingest.services.ingest-service
  (:require
    [cmr.ingest.services.ingest-service.collection]
    [cmr.ingest.services.ingest-service.granule]
    [cmr.ingest.services.ingest-service.service]
    [cmr.ingest.services.ingest-service.subscription]
    [cmr.ingest.services.ingest-service.tool]
    [cmr.ingest.services.ingest-service.util]
    [cmr.ingest.services.ingest-service.variable]
    [potemkin :refer [import-vars]]))

;; Preserve the original API in this namespace:
(import-vars
  [cmr.ingest.services.ingest-service.util
   ;; Public utility functions
   delete-concept
   fix-ingest-concept-format
   health
   reset]
  [cmr.ingest.services.ingest-service.collection
   ;; Public collection functions
   add-extra-fields-for-collection
   save-collection
   validate-and-prepare-collection]
  [cmr.ingest.services.ingest-service.granule
   ;; Public granule functions
   save-granule
   validate-granule
   validate-granule-with-parent-collection]
  [cmr.ingest.services.ingest-service.service
   ;; Public service functions
   save-service]
  [cmr.ingest.services.ingest-service.tool
   ;; Public tool functions
   save-tool]
  [cmr.ingest.services.ingest-service.subscription
   ;; Public subscription functions
   save-subscription]
  [cmr.ingest.services.ingest-service.variable
   ;; Public variable functions
   save-variable])
