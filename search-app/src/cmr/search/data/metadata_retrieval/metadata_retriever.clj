(ns cmr.search.data.metadata-retrieval.metadata-retriever
  "TODO"
  (require [cmr.metadata-db.services.concept-service :as metadata-db]))

(defprotocol MetadataRetriever
  "TODO"

  (get-latest-formatted-concept-revisions
   [this context concept-ids dialect options]
   "TODO doc this including arguments")

  (get-formatted-concept-revisions
   [this context concept-tuples dialect options]
   "TODO doc this including arguments"))


(defrecord MetadataDbMetadataRetriever
  []
  MetadataRetriever
  (get-latest-formatted-concept-revisions
   [_ context concept-ids dialect options])

  (get-formatted-concept-revisions
   [_ context concept-tuples dialect options]))
