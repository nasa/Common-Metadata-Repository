(ns cmr.indexer.services.messages)

(defn bulk-indexing-error-msg
  "Creates a message saying bulk indexing failed with during a given batch."
  [batch-index response]
  (format "Bulk indexing failed while processing batch %d with repsonse %s"
          batch-index
          response))

(defn inconsistent-types-msg
  "Creates an error message indicating that not all the concepts in a batch have
  the same type."
  []
  "The concepts in the batch do not all have the same type.")

(defn inconsistent-index-names-msg
  "Creates an error message indicating that not all the concepts in a batch belong
  to the same index."
  []
  "The concepts in the batch do not all belong in the same index.")