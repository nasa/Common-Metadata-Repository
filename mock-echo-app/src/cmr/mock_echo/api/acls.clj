(ns cmr.mock-echo.api.acls
  "Defines the HTTP URL routes for acls"
  (:require [compojure.core :refer :all]
            [cheshire.core :as json]
            [cmr.mock-echo.data.acl-db :as acl-db]
            [cmr.mock-echo.api.api-helpers :as ah]
            [cmr.common.services.errors :as svc-errors]
            [cmr.mock-echo.data.provider-db :as p-db]
            [clojure.string :as str]))

(defn create-acl
  [context body]
  (let [acl (json/decode body true)]
    (acl-db/create-acl context (json/decode body true))))

(def object-identity-type->acl-field
  {"CATALOG_ITEM" :catalog_item_identity
   "SYSTEM_OBJECT" :system_object_identity
   "PROVIDER_OBJECT" :provider_object_identity
   "SINGLE_INSTANCE_OBJECT" :single_instance_object_identity})

(defn- get-acls-having-fields-and-provider
  "Fetches acls from the acl db that have object identity type fields with the specified provider-id"
  [context acl-fields provider-id]
  (when-let [provider-guid (p-db/provider-id->provider-guid context provider-id)]
    ;; Filter acls applicable to this provider guid
    (filter (fn [acl]
              (some #(= (get-in acl [:acl % :provider_guid]) provider-guid)
                    acl-fields)
              (acl-db/get-acls context)))))

(defn- get-acls-having-fields
  "Gets acls having at least one of the given fields populated."
  [context acl-fields]
  (filter (fn [acl]
            (some #(get-in acl [:acl %]) acl-fields))
          (acl-db/get-acls context)))

(defn get-acls
  [context params]
  (when-not (:object_identity_type params)
    (svc-errors/throw-service-error
      :bad-request
      "Mock ECHO currently only supports retrieving ACLS by object identity type"))
  (when-not (= "false" (:reference params))
    (svc-errors/throw-service-error
      :bad-request
      "Mock ECHO does not currently support retrieving acls as references"))

  (let [acl-fields (map object-identity-type->acl-field (str/split (:object_identity_type params) #","))
        provider-id (:provider_id params)]
    (if provider-id
      (get-acls-having-fields-and-provider context acl-fields provider-id)
      (get-acls-having-fields context acl-fields))))

(defn delete-acl
  [context guid]
  (acl-db/delete-acl context guid))

(defn build-routes [system]
  (routes
    (context "/acls" []
      ;; Creates one ACL. Returns the created acl with a guid
      (POST "/" {params :params context :request-context body :body}
        (ah/status-created (create-acl context (slurp body))))

      ;; Mimics echo-rest retrieval of acls
      (GET "/" {context :request-context params :params headers :headers}
        (ah/require-sys-admin-token headers)
        (ah/status-ok (get-acls context params)))

      (context "/:guid" [guid]
        (DELETE "/" {context :request-context}
          (delete-acl context guid)
          {:status 200})))))

