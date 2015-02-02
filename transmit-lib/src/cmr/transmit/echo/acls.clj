(ns cmr.transmit.echo.acls
  "Contains functions for retrieving ACLs from the echo-rest api."
  (:require [cmr.transmit.echo.rest :as r]
            [cmr.transmit.echo.conversion :as c]
            [cmr.transmit.echo.providers :as echo-providers]
            [cmr.common.util :as util]))

(defn- convert-provider-guid-to-id-in-acl
  "Change all provider-guid references to provider-id for the given ACL. This simplifies working
  with ACLs since provider ids are commonly used throughout the code."
  [provider-guid-id-map acl]
  (let [converter (fn [identity-map]
                    (some-> identity-map
                            (assoc :provider-id (provider-guid-id-map (:provider-guid identity-map)))
                            (dissoc :provider-guid)))]
    (-> acl
        (update-in [:catalog-item-identity] converter)
        (update-in [:provider-object-identity] converter)
        util/remove-nil-keys)))

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
       200 (mapv (comp (partial convert-provider-guid-to-id-in-acl provider-guid-id-map)
                       c/echo-acl->cmr-acl)
                 acls)
       (r/unexpected-status-error! status body)))))