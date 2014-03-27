(ns cmr.metadata-db.services.concept-services
  "Sevices to support the business logic of the metadata db."
  (:require [cmr.metadata-db.data :as data]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.system-trace.core :refer [deftracefn]]))

(defn- context->db
  [context]
  (-> context :system :db))

(deftracefn get-concept
  "Get a concept by concept-id."
  [context concept-id revision-id]
  (data/get-concept (context->db context) concept-id revision-id))

(deftracefn get-concepts
  "Get multiple concepts by concept-id and revision-id."
  [context concept-id-revision-id-tuples]
  (vec (data/get-concepts (context->db context) concept-id-revision-id-tuples)))

(deftracefn save-concept
  "Store a concept record and return the revision."
  [context concept]
  (data/save-concept (context->db context) concept))

(deftracefn delete-concept
  "Add a tombstone record to mark a concept as deleted and return the revision-id of the tombstone."
  [context concept-id revision-id]
  (data/delete-concept (context->db context) concept-id revision-id))

(deftracefn reset
  "Delete all concepts from the concept store."
  [context]
  (data/reset (context->db context)))

(deftracefn get-concept-id
  "Get a concept id for a given concept."
  [context concept-type provider-id native-id]
  (data/get-concept-id (context->db context) concept-type provider-id native-id))
