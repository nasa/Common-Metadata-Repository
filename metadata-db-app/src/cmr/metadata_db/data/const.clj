(ns cmr.metadata-db.data.const
  "This namespace is used to hold constants that are used by different areas
  of the metadata-db, preventing implementations from calling into each
  other.")

(def EXPIRED_CONCEPTS_BATCH_SIZE
  "The batch size to retrieve expired concepts"
  5000)

(def INITIAL_CONCEPT_NUM
  "The number to use as the numeric value for the first concept. Chosen to be larger than the current
  largest sequence in Catalog REST in operations which is 1005488460 as of this writing."
  1200000000)
