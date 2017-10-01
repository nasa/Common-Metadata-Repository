(ns cmr-edsc-stubs.core
  (:require
   [cmr-edsc-stubs.data.cmrapis]
   [cmr-edsc-stubs.data.jdbc]
   [cmr-edsc-stubs.data.metadatadb]
   [potemkin :refer [import-vars]]))

(import-vars
  [cmr-edsc-stubs.data.jdbc
    ingest-ges-disc-airx3std-opendap-service]
  [cmr-edsc-stubs.data.metadatadb
    create-ges-disc-provider]
  [cmr-edsc-stubs.data.cmrapis
    ingest-ges-disc-airx3std-collection
    ingest-ges-disc-airx3std-variables
    associate-ch4-variables-with-ges-disc-airx3std-collection])

