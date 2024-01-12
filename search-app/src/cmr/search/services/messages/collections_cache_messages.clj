(ns cmr.search.services.messages.collections-cache-messages
  "Contains messages for reporting responses from the collections-cache to the user")

(defn collection-not-found
  [concept-id]
  (format "Collection with id %s not found in cache."
          concept-id))

(defn collections-not-in-cache
  [cache-type]
  (format "Collections were not in hash cache by %s." cache-type))
