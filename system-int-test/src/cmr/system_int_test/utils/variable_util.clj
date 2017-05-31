(ns cmr.system-int-test.utils.variable-util
  "This contains utilities for testing variable"
  (:require
    [clojure.string :as string]
    [cmr.acl.core :as acl]
    [clojure.test :refer [is]]
    [cmr.common.mime-types :as mt]
    [cmr.common.util :as util]
    [cmr.mock-echo.client.echo-util :as e]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.system :as s]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.metadata-db-util :as mdb]
    [cmr.system-int-test.utils.search-util :as search]
    [cmr.transmit.echo.tokens :as tokens]))

(defn- get-system-ingest-update-acls
  "Get a token's system ingest management update ACLs."
  [token]
  (-> (s/context)
      (assoc :token token)
      (acl/get-permitting-acls :system-object
                               e/ingest-management-acl
                               :update)))

(defn- grant-permitted?
  "Check if a given grant id is in the list of provided ACLs."
  [grant-id acls]
  (contains?
    (into
      #{}
      (map :guid acls))
    grant-id))

(defn- group-permitted?
  "Check if a given group id is in the list of provided ACLs."
  [group-id acls]
  (contains?
    (reduce
      #(into %1 (map :group-guid %2))
      #{}
      (map :aces acls))
    group-id))

(defn permitted?
  "Check if a the ACLs for the given token include the given grant and group IDs."
  [token grant-id group-id]
  (let [acls (get-system-ingest-update-acls token)]
    (and (grant-permitted? grant-id acls)
         (group-permitted? group-id acls))))

(defn not-permitted?
  [& args]
  (not (apply permitted? args)))
