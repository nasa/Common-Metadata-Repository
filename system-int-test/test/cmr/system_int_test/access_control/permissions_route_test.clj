(ns cmr.system-int-test.access-control.permissions-route-test
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as u]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.url-helper :as url]))

(deftest permission-get-and-post-request-test
  (let [save-basic-collection (fn [short-name]
                                  (u/save-collection {:entry-title (str short-name " entry title")
                                                      :short-name short-name
                                                      :native-id short-name
                                                      :provider-id "PROV1"}))
        coll1 (save-basic-collection "coll1")
        coll2 (save-basic-collection "coll2")
        coll3 (save-basic-collection "coll3")
        coll4 (save-basic-collection "coll4")
        permissions-url (url/access-control-permissions-url)
        post-data-body (str "{ \"user_type\" : \"guest\", \"concept_id\" : ["
                         "\"" coll1 "\", "
                         "\"" coll2 "\", "
                         "\"" coll3 "\", "
                         "\"" coll4 "\""
                         "] }")]
    (testing "permissions endpoint allows post request"
      (let [response (client/post permissions-url
                       {:basic-auth ["user" "pass"]
                        :body post-data-body
                        :connection-manager (system/conn-mgr)
                        :content-type "application/json"})
            body (get response :body)]

        (is (string/includes? body coll1))
        (is (string/includes? body coll2))
        (is (string/includes? body coll3))
        (is (string/includes? body coll4))))))
