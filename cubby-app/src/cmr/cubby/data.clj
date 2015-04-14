(ns cmr.cubby.data)

(defprotocol PersistentCacheStore
  "Describes a persistent cache store that allows persisting values associated with keys."
  (get-keys
    [this]
    "Gets a sequence of keys stored in the cache.")
  (get-value
    [this key-name]
    "Returns the value associated with a key")
  (set-value
    [this key-name value]
    "Associates a value with the specified key")
  (delete-value
    [this key-name]
    "Dissociates any value with the given key")
  (reset
    [this]
    "Resets the cache store removing all saved data."))