(ns cmr.system-int-test.search.acls.collection-rolling-temporal-acl-search-test
  "Tests searching for collections with rolling temporal ACLs in place."
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.common.services.messages :as msg]
            [cmr.common.util :refer [are2] :as util]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.atom :as da]
            [cmr.system-int-test.data2.opendata :as od]
            [cmr.mock-echo.client.echo-util :as e]
            [clj-time.core :as t]
            [cmr.common.test.time-util :as tu]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]))


;; Change this to test with granules as well.

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"}
                                             {:grant-all-search? false})
                       (dev-sys-util/freeze-resume-time-fixture)]))

(comment
  (dev-sys-util/reset)
  (ingest/create-provider {:provider-guid "provguid1" :provider-id "PROV1"})
  )

(def now-n
  "The N value for the current time. Uses N values for date times as describd in
  cmr.common.test.time-util."
  9)

(defn- grant-rolling-temporal
  "Grants the group with the given guid a rolling temporal ACL with the given mask between dates
  represented by start-n and end-n. Computes duration and end values based on the constant now-n
  var."
  [group-id mask start-n end-n]
  (let [rolling-temporal {:duration (tu/millis-between-n start-n end-n)
                          :end (tu/millis-between-n end-n now-n)
                          :mask mask
                          :temporal-field :acquisition}
        collection-identifier (e/coll-id nil nil rolling-temporal)]
    (e/grant-group (s/context) group-id (e/coll-catalog-item-id "provguid1" collection-identifier))))

;; TODO include test where now is at a different time which changes the relative times of collections

(deftest collection-search-with-rolling-temporal-acls-test
  ;; Helper functions
  (let [coll-num (atom 0)
        single-date-coll (fn [n]
                           (d/ingest
                             "PROV1"
                             (dc/collection {:entry-title (str "coll" (swap! coll-num inc))
                                             :single-date-time (tu/n->date-time-string n)})))
        range-date-coll (fn [begin end]
                          (d/ingest
                            "PROV1"
                            (dc/collection {:entry-title (str "coll" (swap! coll-num inc))
                                            :beginning-date-time (tu/n->date-time-string begin)
                                            :ending-date-time (tu/n->date-time-string end)})))]
    ;; Set current time
    (dev-sys-util/freeze-time! (tu/n->date-time-string now-n))

    (grant-rolling-temporal "group-guid1" :intersects 0 5)
    (grant-rolling-temporal "group-guid2" :intersects 5 9)
    (grant-rolling-temporal "group-guid3" :disjoint 3 5)
    (grant-rolling-temporal "group-guid4" :contains 3 7)

    ;; Create collections
    (let [coll1 (single-date-coll 1)
          coll2 (range-date-coll 2 3)
          coll3 (single-date-coll 4)
          coll4 (range-date-coll 5 6)
          coll5 (range-date-coll 3 nil) ;; no end date
          coll6 (single-date-coll 8)
          coll7 (single-date-coll 9)

          ;; User tokens
          ;; Each user is associated with one of the groups above.
          user1 (e/login (s/context) "user1" ["group-guid1"])
          user2 (e/login (s/context) "user2" ["group-guid2"])
          user3 (e/login (s/context) "user3" ["group-guid3"])
          user4 (e/login (s/context) "user4" ["group-guid4"])]
      (index/wait-until-indexed)

      (testing "Parameter searching ACL enforcement"
        (are2 [token items]
             (d/refs-match? items (search/find-refs :collection (when token {:token token})))


             "Guests find nothing"
             nil []

             "group1"
             user1 [coll1 coll2 coll3 coll4 coll5]

             "group2"
             user2 [coll4 coll5 coll6 coll7]

             "group3"
             user3 [coll1 coll6 coll7]

             "group4"
             user4 [coll3 coll4]))


      )))

