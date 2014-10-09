(ns cmr.system-int-test.admin.admin-permissions-test
  "Verifies the correct administrative permissions are enforced admin only apis"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.echo-util :as e]
            [cmr.system-int-test.utils.url-helper :as url]
            [clj-http.client :as client]))

(use-fixtures :each (ingest/reset-fixture))

(defn has-action-permission?
  "Attempts to perform the given action using the url and method with the token. Returns true
  if the action was successful."
  [url method token]
  (let [response (client/request {:url url
                                  :method method
                                  :query-params {:token token}
                                  :connection-manager (url/conn-mgr)
                                  :throw-exceptions false})
        status (:status response)]

    ;; Make sure the status returned is success or 401
    (when (or (and (>= status 300)
                   (not= status 401))
              (< status 200))
      (throw (Exception. (str "Unexpected status " status " response:" (:body response)))))
    (not= status 401)))

(deftest ingest-management-permission-test
  (do
    ;; Grant admin-group-guid admin permission
    (e/grant-group-admin "admin-read-group-guid" :read)
    (e/grant-group-admin "admin-update-group-guid" :update)
    (e/grant-group-admin "admin-read-update-group-guid" :read :update))

  (let [guest-token (e/login-guest)
        user-token (e/login "user1" ["group-guid2" "group-guid3"])
        admin-read-token (e/login "admin" ["admin-read-group-guid" "group-guid3"])
        admin-update-token (e/login "admin" ["admin-update-group-guid" "group-guid3"])
        admin-read-update-token (e/login "admin" ["admin-read-update-group-guid" "group-guid3"])]

    (are [url]
         (and
           (not (has-action-permission? url :post guest-token))
           (not (has-action-permission? url :post user-token))
           (not (has-action-permission? url :post admin-read-token))
           (has-action-permission? url :post admin-update-token)
           (has-action-permission? url :post admin-read-update-token))

         (url/search-clear-cache-url)
         (url/search-reset-url)
         (url/indexer-clear-cache-url)
         (url/indexer-reset-url)
         (url/mdb-reset-url)
         (url/index-set-reset-url))))



