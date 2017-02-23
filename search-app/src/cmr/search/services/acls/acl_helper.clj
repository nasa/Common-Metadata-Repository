(ns cmr.search.services.acls.acl-helper
  "Contains functions for dealing with acls"
  (:require
   [cmr.acl.acl-fetcher :as af]
   [cmr.acl.core :as acl]
   [cmr.common.util :as util]
   [cmr.search.api.request-context-user-augmenter :as context-augmenter]))

(defn get-acls-applicable-to-token
  "Retrieves the ACLs that are applicable to the current user."
  [context]
  (let [acls (af/get-acls context [:catalog-item])
        sids (or (util/lazy-get context :sids) (context-augmenter/context->sids context))]
    (filter (partial acl/acl-matches-sids-and-permission? sids :read) acls)))
