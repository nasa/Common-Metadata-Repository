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


(use-fixtures :each (ingest/reset-fixture-new {"provguid1" "PROV1" "provguid2" "PROV2"}))

;; TODO uncomment this once we convert all the collection tests to handle acls.
#_(deftest collection-search-with-acls-test
  ;; Grant permissions before creating data
  ;; Grant guests permission to coll1
  (e/grant-guest (e/coll-catalog-item-id "provguid1" ["coll1"]))
  ;; restriction flag acl grants matches coll4
  (e/grant-guest (e/coll-catalog-item-id "provguid1" ["coll4"] {:min-value 4 :max-value 6}))
  ;; all collections in prov2 granted to guests
  (e/grant-guest (e/coll-catalog-item-id "provguid2"))


  ;; TODO
  ;; grant registered users permission to coll2
  ;; grant specific group permission to coll3

  (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2"}))
        coll3 (d/ingest "PROV1" (dc/collection {:entry-title "coll3"}))
        coll4 (d/ingest "PROV1" (dc/collection {:entry-title "coll4"
                                                :access-value 5}))
        ;; no permission granted on coll5
        coll5 (d/ingest "PROV1" (dc/collection {:entry-title "coll5"}))

        ;; PROV2
        coll6 (d/ingest "PROV2" (dc/collection {:entry-title "coll6"}))
        coll7 (d/ingest "PROV2" (dc/collection {:entry-title "coll7"}))]

    (index/refresh-elastic-index)

    (are [token items]
         (d/refs-match? items (search/find-refs :collection {:token token}))

         ;; not logged in should be guest
         nil [coll1 coll4 coll6 coll7]

         ;; TODO login and use guest token
         ; guest-token [coll1 coll4 coll6 coll7]


         ;; TODO
         ;; test searching as a user
         ;; test searching as a user in a group
         ;; test searching as a user in a group
         )))

;; TODO add test that ACLs can change and then we reindex the collections and we find the right data

;; TODO bulk indexing should also index permitted group ids

;; TODO add aql acl search test. AQL must be implemented in another method

;; TODO add test of retrieving collections with ECHO10 data. This has to be manually implemented
;; with the collections as they are retrieved.