(ns cmr.common.memory-db.connection
  "Contains a record definition that implements the Lifecycle protocol. This
  record is also intended to used for implementing ConceptStore, ConceptSearch,
  and ProviderStore protocols.

  This namespace was created to bring the MemoryStore in as close parity as
  possible to the OracleStore in cmr.oracle.connection."
  (:require
   [cmr.common.lifecycle :as lifecycle]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility and support functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init-concepts
  "Initialize the in-memory concepts tracker to be ordered for easy retrieval
  of most recently added concept."
  [opts]
  (reverse (sort-by :revision-id (:concepts-atom opts))))

(defn init-next-id
  [opts]
  "Initialize the provided next-id."
  (dec (:next-id-atom opts)))

(defn init-next-transaction-id
  "Initialize the next-transaction-id to the given amount or set it to a
  default of 0."
  [opts]
  (or (:next-transaction-id-atom opts) 0))

(defn init-providers
  "Initialize the providers lookup table to the given hash map or set it to a
  default of an empty hash map."
  [opts]
  (or (:providers-atom opts) {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; A stoage record for use in parity with cmr.oracle.connection
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; IMPORTANT! Note that, for thread safety in tests and in dev environments
;;            (the two places the MemoryStore is used), all data stored in the
;;            record's attributes must be atoms. The constructor does this
;;            automatically, but when using the record directly, it is the
;;            responsibility of the developer to ensure proper types.
(defrecord MemoryStore
  [;; A sequence of concepts stored in metadata db
   concepts-atom

   ;; The next id to use for generating a concept id.
   next-id-atom

   ;; The next global transaction id
   next-transaction-id-atom

   ;; A map of provider ids to providers that exist
   providers-atom])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CMR Component Implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn start
  [this system]
  this)

(defn stop
  [this system]
  this)

(def behaviour
  {:start start
   :stop stop})

(extend MemoryStore
        lifecycle/Lifecycle
        behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MemoryStore Constructor
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-db
  "Creates and returns an in-memory database.

  Note that this construtor sets all attributes as atoms, a requiremet when
  using the MemoryStore record."
  ([]
   (create-db []))
  ([opts]
   (map->MemoryStore
    {:concepts-atom (atom (init-concepts opts))
     :next-id-atom (atom (init-next-id opts))
     :next-transaction-id-atom (atom (init-next-transaction-id opts))
     :providers-atom (atom (init-providers opts))})))
