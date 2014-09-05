(ns cmr.search.services.acls.acl-helper
  "Contains functions for dealing with acls"
  (:require [cmr.transmit.echo.tokens :as echo-tokens]
            [cmr.acl.acl-cache :as ac]
            [cmr.common.cache :as cache]))

(defn context->sids
  "Returns the security identifiers (group guids and :guest or :registered) of the user identified
  by the token in the context."
  [context]
  (let [{:keys [token]} context
        token-sid-cache (get-in context [:system :caches :token-sid])]
    (if token
      (cache/cache-lookup token-sid-cache token #(echo-tokens/get-current-sids context token))
      [:guest])))

(defn- ace-matches-sid?
  "Returns true if the ACE is applicable to the SID."
  [sid ace]
  (or
    (= sid (:user-type ace))
    (= sid (:group-guid ace))))

(defn- acl-matches-sids-and-permission?
  "Returns true if the acl is applicable to any of the sids."
  [sids permission acl]
  (some (fn [sid]
          (some (fn [ace]
                  (and (ace-matches-sid? sid ace)
                       (some #(= % permission) (:permissions ace))))
                (:aces acl)))
        sids))

(defn get-acls-applicable-to-token
  "Retrieves the ACLs that are applicable to the current user."
  [context]
  (let [acls (ac/get-acls context)
        sids (context->sids context)]
    (filter (partial acl-matches-sids-and-permission? sids :read) acls)))

(defmulti extract-access-value
  "Extracts access value (aka. restriction flag) from the concept."
  (fn [concept]
    (:format concept)))

(defmethod extract-access-value "application/echo10+xml"
  [concept]
  (when-let [[_ restriction-flag-str] (re-matches #"(?s).*<RestrictionFlag>(.+)</RestrictionFlag>.*"
                                                  (:metadata concept))]
    (Double. ^String restriction-flag-str)))

(defmethod extract-access-value "application/dif+xml"
  [concept]
  ;; DIF doesn't support restriction flag yet.
  nil)