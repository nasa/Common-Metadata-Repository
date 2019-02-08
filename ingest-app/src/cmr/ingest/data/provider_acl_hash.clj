(ns cmr.ingest.data.provider-acl-hash
  "Stores and retrieves the hashes of the ACLs for a provider."
  (:require
   [clojure.edn :as edn]
   [clojure.java.jdbc :as j]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.util :refer [defn-timed] :as util]
   [cmr.oracle.connection]))

(defprotocol AclHashStore
  "Defines a protocol for storing the acl hashes as a string."
  (save-acl-hash [store acl-hash])
  (get-acl-hash [store]))

;; Extends the AclHashStore to the oracle store so it will work with oracle.
(extend-protocol AclHashStore
  cmr.oracle.connection.OracleStore

  (save-acl-hash
    [db acl-hash]
    (j/with-db-transaction
      [conn db]
      (debug "Deleting existing records from provider acl hash")
      (j/execute! conn ["delete from provider_acl_hash"])
      (debug "Inserting acl hash")
      (j/insert! conn "provider_acl_hash" ["acl_hashes"] [(util/string->gzip-bytes acl-hash)])
      (debug "Acl hash inserted. Completing transaction")))

  (get-acl-hash
    [db]
    (some-> (j/query db ["select acl_hashes from provider_acl_hash"])
            first
            :acl_hashes
            util/gzip-blob->string)))

(defn context->db
  [context]
  (get-in context [:system :db]))

(defn-timed get-provider-id-acl-hashes
  "Returns a map of provider ids to hash values."
  [context]
  (some-> context context->db get-acl-hash edn/read-string))

(defn-timed save-provider-id-acl-hashes
  "Saves the map of provider id acl hash values"
  [context provider-hashes]
  (save-acl-hash (context->db context) (pr-str provider-hashes)))

(comment
  (save-provider-id-acl-hashes {:system (get-in user/system [:apps :ingest])} {"a" 1 "b" 3})
  (get-provider-id-acl-hashes {:system (get-in user/system [:apps :ingest])}))
