(ns cmr.transmit.echo.acls
  "Contains functions for retrieving ACLs from the echo-rest api."
  (:require [cmr.transmit.echo.rest :as r]
            [cmr.transmit.echo.conversion :as c]
            [cmr.transmit.echo.providers :as echo-providers]))

(defn- convert-provider-guid-key-to-provider-id-key
  "Change all provider-guid references to provider-id."
  [provider-guid-id-map identity-type]
  (some-> identity-type
          (assoc :provider-id (provider-guid-id-map (:provider-guid identity-type)))
          (dissoc :provider-guid)))

(defn- set-acl-provider-id
  "Sets the provider-id in the acl to replace the provider guid."
  [provider-guid-id-map acl]
  (let [converter (partial convert-provider-guid-key-to-provider-id-key provider-guid-id-map)]
    (-> acl
        (update-in [:catalog-item-identity] converter)
        (update-in [:provider-object-identity] converter))))

(defn get-acls-by-type
  "Fetches ACLs from ECHO by object identity type. Valid values are PROVIDER_OBJECT, SYSTEM_OBJECT,
  SINGLE_INSTANCE_OBJECT, and CATALOG_ITEM as strings."
  ([context type]
   (get-acls-by-type context type nil))
  ([context type provider-id]
   (let [provider-guid-id-map (echo-providers/get-provider-guid-id-map context)
         [status acls body] (r/rest-get
                              context
                              "/acls"
                              {:query-params
                               (merge {:object_identity_type type
                                       :reference false}
                                      (when provider-id {:provider_id provider-id}))})]
     (case status
       200 (mapv (comp (partial set-acl-provider-id provider-guid-id-map)
                       c/echo-acl->cmr-acl)
                 acls)
       (r/unexpected-status-error! status body)))))