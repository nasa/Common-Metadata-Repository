(ns cmr-edsc-stubs.core
  (:require
   [cmr-edsc-stubs.data.cmrapis]
   [cmr-edsc-stubs.data.fake-response]
   [cmr-edsc-stubs.data.jdbc]
   [cmr-edsc-stubs.data.metadatadb]
   [cmr.sample-data.const]
   [potemkin :refer [import-vars]]))

(import-vars
  [cmr-edsc-stubs.data.cmrapis
    ingest-ges-disc-airx3std-collection
    ingest-ges-disc-airx3std-variables
    associate-ch4-variables-with-ges-disc-airx3std-collection]
  [cmr-edsc-stubs.data.fake-response
    get-ges-disc-services-map
    get-ges-disc-variables-map
    get-umm-json-ges-disc-airx3std-collection
    get-umm-json-ges-disc-airx3std-variables
    handle-prototype-request]
  [cmr-edsc-stubs.data.jdbc
    ingest-ges-disc-airx3std-opendap-service]
  [cmr-edsc-stubs.data.metadatadb
    create-ges-disc-provider]
  [cmr.sample-data.const
    ges-disc-airx3std-collection-id])

