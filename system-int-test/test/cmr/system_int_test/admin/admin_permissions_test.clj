(ns cmr.system-int-test.admin.admin-permissions-test
  "Verifies the correct administrative permissions are enforced admin only apis"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.echo-util :as e]
            [cmr.system-int-test.utils.url-helper :as url]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"} false))

(deftest ingest-management-permission-test
  (do
    ;; Grant admin-group-guid admin permission
    (e/grant-group-admin "admin-read-group-guid" :read)
    (e/grant-group-admin "admin-update-group-guid" :update)
    (e/grant-group-admin "admin-read-update-group-guid" :read :update))
    ;; Grant provider admin permission, but not system permission
    (e/grant-group-provider-admin "prov-admin-group-guid" "provguid1" :read :update :delete)


  (let [guest-token (e/login-guest)
        user-token (e/login "user1" ["group-guid2" "group-guid3"])
        admin-read-token (e/login "admin" ["admin-read-group-guid" "group-guid3"])
        admin-update-token (e/login "admin" ["admin-update-group-guid" "group-guid3"])
        admin-read-update-token (e/login "admin" ["admin-read-update-group-guid" "group-guid3"])
        prov-admin-token (e/login "prov-admin" ["prov-admin-group-guid" "group-guid3"])
]

    (are [url]
         (and
           (not (e/has-action-permission? url :post prov-admin-token))
           (not (e/has-action-permission? url :post guest-token))
           (not (e/has-action-permission? url :post user-token))
           (not (e/has-action-permission? url :post admin-read-token))
           (e/has-action-permission? url :post admin-update-token)
           (e/has-action-permission? url :post admin-read-update-token))

         (url/search-clear-cache-url)
         (url/search-reset-url)
         (url/indexer-clear-cache-url)
         (url/indexer-reset-url)
         (url/mdb-reset-url)
         (url/index-set-reset-url))))
