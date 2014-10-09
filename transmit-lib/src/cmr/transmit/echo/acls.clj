(ns cmr.transmit.echo.acls
  "Contains functions for retrieving ACLs from the echo-rest api."
  (:require [cmr.transmit.echo.rest :as r]
            [cmr.transmit.echo.conversion :as c]
            [cmr.transmit.echo.providers :as echo-providers]))

(defn- set-acl-provider-id
  "Sets the provider-id in the acl to replace the provider guid"
  [provider-guid-id-map acl]
  (update-in acl
             [:catalog-item-identity]
             (fn [cii]
               (some-> cii
                       (assoc :provider-id (provider-guid-id-map (:provider-guid cii)))
                       (dissoc :provider-guid)))))

(defn get-acls-by-type
  "Fetches ACLs from ECHO by object identity type. Valid values are PROVIDER_OBJECT, SYSTEM_OBJECT,
  SINGLE_INSTANCE_OBJECT, and CATALOG_ITEM as strings."
  [context type]
  (let [provider-guid-id-map (echo-providers/get-provider-guid-id-map context)
        [status acls body] (r/rest-get context "/acls" {:query-params {:object_identity_type type
                                                                       :reference false}})]
    (case status
      200 (mapv (comp (partial set-acl-provider-id provider-guid-id-map)
                      c/echo-acl->cmr-acl)
                acls)
      (r/unexpected-status-error! status body))))


