(ns cmr.access-control.services.auth-util
  (:require [cmr.common.services.errors :as errors]
            [cmr.acl.core :as acl]))

(defn- get-access-control-group-acls
  ([context permission]
   (acl/get-permitting-acls context :system-object "GROUP" permission))
  ([context permission provider-id]
   (some #(= provider-id (-> % :provider-object-identity :provider-id))
         (acl/get-permitting-acls context :provider-object "GROUP" permission))))

(defn- verify-permission
  ([context permission]
   (when-not (get-access-control-group-acls context permission)
     (errors/throw-service-error
       :unauthorized
       (format "You do not have permission to %s system-level access control groups."
               (name permission)))))
  ([context permission provider-id]
   (when-not (get-access-control-group-acls context permission provider-id)
     (errors/throw-service-error
       :unauthorized
       (format "You do not have permission to %s access control groups for provider [%s]."
               (name permission)
               provider-id)))))

(defn- verify-group-permission
  [context permission {:keys [provider-id]}]
  (if (and provider-id (not= "CMR" provider-id))
    (verify-permission context permission provider-id)
    (verify-permission context permission)))

(defn verify-can-create-group
  "Throws a service error if the context user cannot create a group under provider-id."
  [context group]
  (verify-group-permission context :create group))

(defn verify-can-read-group
  "Throws a service error if the context user cannot read the access control group represented by
   the group map."
  [context group]
  (verify-group-permission context :read group))

(defn verify-can-delete-group
  "Throws a service error of context user cannot delete access control group represented by given
   group map."
  [context group]
  (verify-group-permission context :delete group))
