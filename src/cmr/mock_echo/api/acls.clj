(ns cmr.mock-echo.api.acls
  "Defines the HTTP URL routes for acls"
  (:require [compojure.core :refer :all]
            [cheshire.core :as json]
            [cmr.mock-echo.data.acl-db :as acl-db]
            [cmr.mock-echo.api.api-helpers :as ah]
            [cmr.common.services.errors :as svc-errors]))

(defn create-acls
  [context body]
  (let [acls (json/decode body true)]
    (acl-db/create-acls context acls)))

(defn get-acls
  [context params]
  (when-not (= "CATALOG_ITEM" (:object_identity_type params))
    (svc-errors/throw-service-error
      :bad-request
      "Mock ECHO currently only supports retrieving ACLS with object identity type CATALOG_ITEM"))
  (when (:provider_id params)
    (svc-errors/throw-service-error
      :bad-request
      "Mock ECHO does not currently support retrieving acls by provider."))
  (when-not (= "false" (:reference params))
    (svc-errors/throw-service-error
      :bad-request
      "Mock ECHO does not currently support retrieving acls as references"))
  (acl-db/get-acls context))


(defn build-routes [system]
  (routes
    (context "/acls" []
      ;; Create a bunch of acls all at once
      ;; This is used for adding test data.
      (POST "/" {params :params context :request-context body :body}
        (create-acls context (slurp body))
        {:status 201})

      ;; Mimics echo-rest retrieval of acls
      (GET "/" {context :request-context params :params}
        (ah/status-ok (get-acls context params))))))


(comment

  (do
    (require '[cmr.transmit.echo.acls :as p])
    (require '[cmr.transmit.echo.mock :as m])

    (m/create-acls {:system user/system} {"guid1" "PROV1"
                                               "guid2" "PROV2"}))

  (get-acls {:system user/system})

  (p/get-acl-guid-id-map {:system user/system})




  )