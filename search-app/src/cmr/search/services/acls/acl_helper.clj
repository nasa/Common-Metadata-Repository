(ns cmr.search.services.acls.acl-helper
  "Contains functions for dealing with acls"
  (:require
   [cmr.acl.acl-fetcher :as af]
   [cmr.acl.core :as acl]
   [cmr.common.util :as util]))

(defn get-acls-applicable-to-token
  "Retrieves the catalog item ACLs that are applicable to the current user."
  [context]
  (let [acls (af/get-acls context [:catalog-item])
        sids (util/lazy-get context :sids)]
    (filter (partial acl/acl-matches-sids-and-permission? sids :read) acls)))

(defn get-sm-acls-applicable-to-token
  "Retrieves the SUBSCRIPTION_MANAGEMENT ACLs that are applicable to the current user.
  i.e. grant read permission to the current user."
  [context]
  (let [acls (af/get-acls context [:provider-object])
        ;; only get SUBSCRIPTION_MANAGEMENT ACLS
        esm-acls (filter #(= "SUBSCRIPTION_MANAGEMENT" (get-in % [:provider-identity :target])) acls)
        sids (util/lazy-get context :sids)]
    (filter (partial acl/acl-matches-sids-and-permission? sids :read) esm-acls)))

(defn has-system-read-permission?
  "Returns true if the current user has system Ingest Management read permission
  which is needed to see all collection subscriptions."
  [context]
  (acl/has-ingest-management-permission? context :read :system-object nil))
