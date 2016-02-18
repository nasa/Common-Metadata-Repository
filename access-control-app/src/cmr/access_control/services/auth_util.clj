(ns cmr.access-control.services.auth-util
  (:require [cmr.common.services.errors :as errors]
            [cmr.acl.core :as acl]))

(defn verify-can-create-system-group
  "Throws a service error if the context user cannot create a system-level group."
  [context]
  (when-not (acl/get-permitting-acls context :system-object "GROUP" :create)
    (errors/throw-service-error
      :unauthorized
      "You do not have permission to create system-level access control groups.")))

(defn verify-can-create-provider-group
  "Throws a service error if the context user cannot create a group under provider-id."
  [context provider-id]
  (let [acls (acl/get-permitting-acls context :provider-object "GROUP" :create)]
    (when-not (some #(= provider-id (-> % :provider-object-identity :provider-id))
                    acls)
      (errors/throw-service-error
        :unauthorized
        (str "You do not have permission to create access control groups under provider ["
             provider-id
             "].")))))
