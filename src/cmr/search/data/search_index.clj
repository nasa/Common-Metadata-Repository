(ns cmr.search.data.search-index
  "Defines protocols for interacting with a search index")

(defprotocol SearchIndex
  "Defines operations for searching for concepts"
  (execute-query
    [idx query]
    "Executes a query to find concepts. Returns concept id, native id, and revision id."))