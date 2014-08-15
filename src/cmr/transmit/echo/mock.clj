(ns cmr.transmit.echo.mock
  "Contains functions for communicating with the mock echo api that aren't normal echo-rest
  operations"
  (:require [cmr.transmit.echo.rest :as r]
            [cmr.transmit.echo.conversion :as c]))

(defn reset
  "Clears out all data in mock echo"
  [context]
  (r/rest-post context "/reset" nil))

(defn create-providers
  "Creates the providers in mock echo given a provider-guid-to-id-map"
  [context provider-guid-to-id-map]
  (let [providers (for [[guid provider-id] provider-guid-to-id-map]
                    {:provider {:id guid
                                :provider_id provider-id}})
        [status] (r/rest-post context "/providers" providers)]
    (when-not (= status 201)
      (r/unexpected-status-error! status nil))))

(defn create-acl
  "Creates an ACL in mock echo. Takes cmr style acls."
  [context acl]
  (let [[status] (r/rest-post context "/acls" (c/cmr-acl->echo-acl acl))]
    (when-not (= status 201)
      (r/unexpected-status-error! status nil))))