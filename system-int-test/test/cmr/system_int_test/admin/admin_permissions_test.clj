(ns cmr.system-int-test.admin.admin-permissions-test
  "Verifies the correct administrative permissions are enforced admin only apis"
  (:require
   [clj-http.client :as client]
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.url-helper :as url]
   [ring.util.codec :as codec]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"} {:grant-all-search? false
                                                                 :grant-all-ingest? false}))

(defn has-action-permission?
  "Attempts to perform the given action using the url and method with the token. Returns true
  if the action was successful."
  ([url method token]
   (has-action-permission? url method token nil))
  ([url method token body]
   (has-action-permission? url method token body {}))
  ([url method token body query-params]
   (has-action-permission? url method token body query-params {}))
  ([url method token body query-params options]
   (let [options (merge options {:accept "application/json"
                                 :content-type mt/form-url-encoded
                                 :body-encoding codec/form-encode
                                 :throw-exceptions false})
         response (client/request {:url url
                                   :method method
                                   :accept (options :accept)
                                   :content-type (options :content-type)
                                   :body ((options :body-encoding) body)
                                   :query-params (merge {:token token} query-params)
                                   :connection-manager (s/conn-mgr)
                                   :throw-exceptions (options :throw-exceptions)})
         status (:status response)]
      ;; Make sure the status returned is success or 401
     (is (some #{status} [200 201 204 401]))
     (not= status 401))))

(deftest ingest-management-permission-test
  (let [admin-read-update-group-concept-id (e/get-or-create-group (s/context) "admin-read-update-group")
        admin-read-group-concept-id (e/get-or-create-group (s/context) "admin-read-group")
        admin-update-group-concept-id (e/get-or-create-group (s/context) "admin-update-group")
        prov-admin-group-concept-id (e/get-or-create-group (s/context) "prov-admin-group")
        group2-concept-id (e/get-or-create-group (s/context) "group2")
        group3-concept-id (e/get-or-create-group (s/context) "group3")
        guest-token (e/login-guest (s/context))
        user-token (e/login (s/context) "user1" [group2-concept-id group3-concept-id])
        admin-read-token (e/login (s/context) "admin1" [admin-read-group-concept-id group3-concept-id])
        admin-update-token (e/login (s/context) "admin2" [admin-update-group-concept-id group3-concept-id])
        admin-read-update-token (e/login (s/context) "admin3" [admin-read-update-group-concept-id group3-concept-id])
        prov-admin-token (e/login (s/context) "prov-admin" [prov-admin-group-concept-id group3-concept-id])
        check-all-permissions (fn check-all-permissions-inline
                                ([url method]
                                 (check-all-permissions-inline url method {}))
                                ([url method body]
                                 (check-all-permissions-inline url method body {}))
                                ([url method body query-params]
                                 (check-all-permissions-inline url method body query-params {}))
                                ([url method body query-params options]
                                 (is
                                  (and
                                   (not (has-action-permission? url method prov-admin-token body query-params options))
                                   (not (has-action-permission? url method guest-token body query-params options))
                                   (not (has-action-permission? url method user-token body query-params options))
                                   (not (has-action-permission? url method admin-read-token body query-params options))
                                   (has-action-permission? url method admin-update-token body query-params options)
                                   (has-action-permission? url method admin-read-update-token body query-params options)))))]

    ;; Grant admin-group-guid admin permission
    (e/grant-group-admin (s/context) admin-read-group-concept-id :read)
    (e/grant-group-admin (s/context) admin-update-group-concept-id :update)
    (e/grant-group-admin (s/context) admin-read-update-group-concept-id :read :update)
    ;; Grant provider admin permission, but not system permission
    (e/grant-group-provider-admin (s/context) prov-admin-group-concept-id "PROV1" :read :update)

    (testing "Admin permissions test"
      (are3 [url methods]
        (check-all-permissions url methods)

        "search-clear-cache"
        (url/search-clear-cache-url) :post

        "search-reset"
        (url/search-reset-url) :post

        "indexer-clear-cache"
        (url/indexer-clear-cache-url) :post

        "indexer-reset"
        (url/indexer-reset-url) :post

        "enable-ingest-writes"
        (url/enable-ingest-writes-url) :post

        "disable-ingest-write"
        (url/disable-ingest-writes-url) :post

        "enable-search-writes"
        (url/enable-search-writes-url) :post

        "disable-search-writes"
        (url/disable-search-writes-url) :post

        "enable-access-control-writes"
        (url/enable-access-control-writes-url) :post

        "disable-access-control-writes"
        (url/disable-access-control-writes-url) :post

        "mdb-reset"
        (url/mdb-reset-url) :post

        "index-set-reset"
        (url/index-set-reset-url) :post

        "reindex-collection-permitted-groups"
        (url/reindex-collection-permitted-groups-url) :post

        "reindex-all-collections"
        (url/reindex-all-collections-url) :post

        "cleanup-expired-collections"
        (url/cleanup-expired-collections-url) :post

        "cleanup-granule-bulk-update-tasks"
        (url/cleanup-granule-bulk-update-task-url) :post

        "access-control-reindex-acls"
        (url/access-control-reindex-acls-url) :post

        "enable-email-subscription-processing"
        (url/enable-email-subscription-processing) :post

        "disable-email-subscription-processing"
        (url/disable-email-subscription-processing) :post))

    (testing "Admin permissions test with body"
      (check-all-permissions (url/email-subscription-processing)
                             :post
                             {}
                             {:revision-date-range "2000-01-01T10:00:00Z,2010-03-10T12:00:00Z"}))))
