(ns cmr.system-int-test.search.acls.collection-acl-search-test
  "Tests searching for collections with ACLs in place"
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [cmr.common.services.messages :as msg]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.echo-util :as e]))


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"} false))

;; TODO add test for searching with an invalid security token.

;; TODO add test of passing token through header

(deftest collection-search-with-acls-test
  ;; Grant permissions before creating data
  ;; Grant guests permission to coll1
  (e/grant-guest (e/coll-catalog-item-id "provguid1" ["coll1"]))
  ;; restriction flag acl grants matches coll4
  (e/grant-guest (e/coll-catalog-item-id "provguid1" ["coll4"] {:min-value 4 :max-value 6}))
  ;; all collections in prov2 granted to guests
  (e/grant-guest (e/coll-catalog-item-id "provguid2"))
  ;; grant registered users permission to coll2 and coll4
  (e/grant-registered-users (e/coll-catalog-item-id "provguid1" ["coll2" "coll4"]))
  ;; grant specific group permission to coll3 and coll6
  (e/grant-group "group-guid1" (e/coll-catalog-item-id "provguid1" ["coll3"]))
  (e/grant-group "group-guid2" (e/coll-catalog-item-id "provguid2" ["coll6"]))


  (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2"}))
        coll3 (d/ingest "PROV1" (dc/collection {:entry-title "coll3"}))
        coll4 (d/ingest "PROV1" (dc/collection {:entry-title "coll4"
                                                :access-value 5}))
        ;; no permission granted on coll5
        coll5 (d/ingest "PROV1" (dc/collection {:entry-title "coll5"}))

        ;; PROV2
        coll6 (d/ingest "PROV2" (dc/collection {:entry-title "coll6"}))
        coll7 (d/ingest "PROV2" (dc/collection {:entry-title "coll7"}))

        guest-token (e/login-guest)
        user1-token (e/login "user1")
        user2-token (e/login "user2" ["group-guid1"])
        user3-token (e/login "user3" ["group-guid1" "group-guid2"])]

    (index/refresh-elastic-index)

    (are [token items]
         (d/refs-match? items (search/find-refs :collection (when token {:token token})))

         ;; not logged in should be guest
         nil [coll1 coll4 coll6 coll7]

         ;; login and use guest token
         guest-token [coll1 coll4 coll6 coll7]

         ;; test searching as a user
         user1-token [coll2 coll4]

         ;; Test searching with users in groups
         user2-token [coll2 coll4 coll3]
         user3-token [coll2 coll4 coll3 coll6])))

;; TODO add test that ACLs can change and then we reindex the collections and we find the right data

;; TODO bulk indexing should also index permitted group ids

;; TODO add aql acl search test.

;; TODO add test of retrieving collections with ECHO10 data. This has to be manually implemented
;; with the collections as they are retrieved.