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

(defn verify-can-create-group
  "Throws a service error if the context user cannot create a group under provider-id."
  [context group]
  (if-let [provider-id (:provider-id group)]
    (verify-permission context :create provider-id)
    (verify-permission context :create)))

(defn verify-can-read-group
  "Throws a service error if the context user cannot read the access control group represented by
   the group map."
  [context group]
  (if-let [provider-id (:provider-id group)]
    (verify-permission context :read provider-id)
    (verify-permission context :read)))
