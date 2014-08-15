(ns cmr.system-int-test.utils.echo-util
  "Contains helper functions for working with the echo mock"
  (:require [cmr.transmit.echo.mock :as mock]
            [cmr.transmit.echo.tokens :as tokens]
            [cmr.transmit.config :as config]))

(def context-atom
  "Cached context"
  (atom nil))

(defn- context
  "Returns a context to pass to the mock transmit api"
  []
  (when-not @context-atom
    (reset! context-atom {:system (config/system-with-connections {} [:echo-rest])}))
  @context-atom)


(defn reset
  "Resets the mock echo."
  []
  (mock/reset (context)))

(defn create-providers
  "Creates the providers in the mock echo."
  [provider-guid-id-map]
  (mock/create-providers (context) provider-guid-id-map))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Token related

(defn login-guest
  "Logs in as a guest and returns the token"
  []
  (tokens/login-guest (context)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ACL related

(defn coll-catalog-item-id
  "Creates a collection applicable catalog item identity"
  ([provider-guid]
   (coll-catalog-item-id provider-guid []))
  ([provider-guid entry-titles]
   (coll-catalog-item-id provider-guid entry-titles nil))
  ([provider-guid entry-titles access-value-filter]
   (merge-with
     merge
     {:provider-guid provider-guid
      :collection-applicable true}
     (when (seq entry-titles)
       {:collection-identifier {:entry-titles entry-titles}})
     (when access-value-filter
       {:collection-identifier {:access-value access-value-filter}}))))

(defn grant
  "Creates an ACL in mock echo with the id, access control entries, and catalog item identity"
  [aces catalog-item-identity]
  (let [acl {:aces aces
             :catalog-item-identity catalog-item-identity}]
    (mock/create-acl (context) acl)))

(def guest-ace
  "TODO"
  {:permissions [:read]
   :user-type :guest})

(def registered-user-ace
  "TODO"
  {:permissions [:read]
   :user-type :registered})

(defn group-ace
  "TODO"
  [group-guid]
  {:permissions [:read]
   :group-guid group-guid})

(defn grant-guest
  "TODO"
  [catalog-item-identity]
  (grant [guest-ace] catalog-item-identity))

(defn grant-registered-users
  "TODO"
  [catalog-item-identity]
  (grant [registered-user-ace] catalog-item-identity))

(defn grant-group
  "TODO"
  [group-guid catalog-item-identity]
  (grant [(group-ace group-guid)] catalog-item-identity))


