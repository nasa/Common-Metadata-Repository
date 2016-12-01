(ns cmr.search.services.acls.acl-helper
  "Contains functions for dealing with acls"
  (:require [cmr.acl.acl-fetcher :as af]
            [cmr.acl.core :as acl]
            [cmr.common.cache :as cache]
            [cmr.common.log :refer (info)]
            [cmr.search.data.sids-retriever :as sids-retriever]))

(def token-sid-cache-name
  :token-sid)

(defn context->sids
  "Wraps the existing context->sids but with caching"
  [context]
  (let [{:keys [token]} context]
    (let [sids (cache/get-value (cache/context->cache context token-sid-cache-name)
                                token
                                #(or (sids-retriever/get-sids context (:token context))
                                     (acl/context->sids context)))
          token-guid (:guid sids)
          sids (:sids sids)]
      (when token-guid (info (format "Client token GUID: [%s]" token-guid)))
      sids)))

(defn get-acls-applicable-to-token
  "Retrieves the ACLs that are applicable to the current user."
  [context]
  (let [acls (af/get-acls context [:catalog-item])
        sids (context->sids context)]
    (filter (partial acl/acl-matches-sids-and-permission? sids :read) acls)))
