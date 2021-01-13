(ns cmr.system-int-test.search.acls.temporal-collection-granule-test
  "Tests searching for collections and granules with temporal ACLs in place."
  (:require
   [clj-time.core :as t]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.common.services.messages :as msg]
   [cmr.common.test.time-util :as tu]
   [cmr.common.util :refer [are2 are3] :as util]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.atom :as da]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.granule-counts :as gran-counts]
   [cmr.system-int-test.data2.opendata :as od]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.transmit.config :as tc]
   [cmr.transmit.echo.conversion :as echo-conversion]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"}
                                             {:grant-all-search? false})
                       (dev-sys-util/freeze-resume-time-fixture)]))

(def now-n
  "The N value for the current time. Uses N values for date times as describd in
  cmr.common.test.time-util."
  9)

(defn- grant-temporal
  "Grants the group with the given guid a temporal ACL with the given mask between dates
  represented by start-n and end-n. Computes duration and end values based on the constant now-n
  var."
  [concept-type group-id mask start-n end-n]
  (let [temporal-filter {:start-date (tu/n->date-time start-n)
                         :stop-date (tu/n->date-time end-n)
                         :mask mask}
        catalog-item-identifier (if (= concept-type :collection)
                                  (assoc (e/coll-catalog-item-id "PROV1" (e/coll-id nil nil temporal-filter))
                                         ;; Setting granule applicable to true so we can test
                                         ;; application of collection temporal filters to granules
                                         ;; within that collection.
                                         :granule_applicable true)

                                  (e/gran-catalog-item-id
                                    "PROV1" nil (e/gran-id nil temporal-filter)))]
    (e/grant-group (s/context) group-id catalog-item-identifier)))

(defn atom-results->title-set
  "Returns the title element from each atom result item"
  [results]
  (->> results :results :entries (map :title) set))

(deftest collection-search-with-temporal-acls-test
  (let [group1-concept-id (e/get-or-create-group (s/context) "group1")
        group2-concept-id (e/get-or-create-group (s/context) "group2")
        group3-concept-id (e/get-or-create-group (s/context) "group3")
        group4-concept-id (e/get-or-create-group (s/context) "group4")
        coll-num (atom 0)
        single-date-coll (fn [n metadata-format]
                           (d/ingest
                            "PROV1"
                            (dc/collection {:entry-title (str "coll" (swap! coll-num inc))
                                            :single-date-time (tu/n->date-time-string n)})
                            {:format metadata-format}))
        range-date-coll (fn [begin end metadata-format]
                          (d/ingest
                           "PROV1"
                           (dc/collection-dif {:entry-title (str "coll" (swap! coll-num inc))
                                               :version-id "V1"
                                               :beginning-date-time (tu/n->date-time-string begin)
                                               :ending-date-time (tu/n->date-time-string end)})
                           {:format metadata-format}))]
    ;; Set current time
    (dev-sys-util/freeze-time! (tu/n->date-time-string now-n))

    (grant-temporal :collection group1-concept-id "intersect" 0 5)
    (grant-temporal :collection group2-concept-id "intersect" 5 9)
    (grant-temporal :collection group3-concept-id "disjoint" 3 5)
    (grant-temporal :collection group4-concept-id "contains" 3 7)

    ;; Create collections
    (let [coll1 (single-date-coll 1 :echo10)
          gran1 (d/ingest "PROV1" (dg/granule coll1))
          coll2 (range-date-coll 2 3 :dif)
          gran2 (d/ingest "PROV1" (dg/granule coll2))
          coll3 (single-date-coll 4 :iso19115)
          gran3 (d/ingest "PROV1" (dg/granule coll3))
          coll4 (range-date-coll 5 6 :iso19115)
          gran4 (d/ingest "PROV1" (dg/granule coll4))
          coll5 (range-date-coll 3 nil :iso-smap) ;; no end date
          gran5 (d/ingest "PROV1" (dg/granule coll5))
          coll6 (single-date-coll 8 :iso-smap)
          gran6 (d/ingest "PROV1" (dg/granule coll6))
          coll7 (single-date-coll 9 :echo10)
          gran7 (d/ingest "PROV1" (dg/granule coll7))
          all-coll-concept-ids (map :concept-id [coll1 coll2 coll3 coll4 coll5 coll6 coll7])
          all-gran-concept-ids (map :concept-id [gran1 gran2 gran3 gran4 gran5 gran6 gran7])

          ;; User tokens
          ;; Each user is associated with one of the groups above.
          user1 (e/login (s/context) "user1" [group1-concept-id])
          user2 (e/login (s/context) "user2" [group2-concept-id])
          user3 (e/login (s/context) "user3" [group3-concept-id])
          user4 (e/login (s/context) "user4" [group4-concept-id])

          ;; Create sets of collections visible for each group
          group1-colls [coll1 coll2 coll3 coll4 coll5]
          group2-colls [coll4 coll5 coll6 coll7]
          group3-colls [coll1 coll6 coll7]
          group4-colls [coll3 coll4]
          ;; Create sets of granules visible for each group
          group1-granules [gran1 gran2 gran3 gran4 gran5]
          group2-granules [gran4 gran5 gran6 gran7]
          group3-granules [gran1 gran6 gran7]
          group4-granules [gran3 gran4]]
      (ingest/reindex-collection-permitted-groups (tc/echo-system-token))
      (index/wait-until-indexed)

      (testing "Collection temporal as applied to granule searches"
        (are2 [token items]
          (d/refs-match? items (search/find-refs :granule (when token {:token token})))
          "Guests find nothing"
          nil []
          "group1" user1 group1-granules
          "group2" user2 group2-granules
          "group3" user3 group3-granules
          "group4" user4 group4-granules))

      (testing "Parameter searching ACL enforcement"
        (are2 [token items]
          (d/refs-match? items (search/find-refs :collection (when token {:token token})))
          "Guests find nothing"
          nil []
          "group1" user1 group1-colls
          "group2" user2 group2-colls
          "group3" user3 group3-colls
          "group4" user4 group4-colls))

      (testing "Granule ACL Enforcement by concept id"
        (are3 [token grans colls]
          (let [concept-ids all-gran-concept-ids
                gran-atom (da/granules->expected-atom
                           grans colls
                           (str "granules.atom?"
                                (when token (str "token=" token "&"))
                                "page_size=100&concept_id="
                                (str/join "&concept_id=" concept-ids)))]
            (is (= gran-atom (:results (search/find-concepts-atom
                                        :granule (util/remove-nil-keys
                                                  {:token token
                                                   :page-size 100
                                                   :concept-id concept-ids}))))))
          "Guests find nothing" nil [] []
          "group1" user1 group1-granules group1-colls
          "group2" user2 group2-granules group2-colls
          "group3" user3 group3-granules group3-colls
          "group4" user4 group4-granules group4-colls))

      (testing "Collection ATOM ACL Enforcement by concept id"
        (are2 [token colls]
          (= (set (map :entry-title colls))
             (atom-results->title-set
              (search/find-concepts-atom
               :collection (util/remove-nil-keys
                            {:token token
                             :page-size 100
                             :concept-id all-coll-concept-ids}))))
          "Guests find nothing" nil []
          "group1" user1 group1-colls
          "group2" user2 group2-colls
          "group3" user3 group3-colls
          "group4" user4 group4-colls))

      (testing "Collection JSON ACL Enforcement by concept id"
        (are2 [token colls]
          (= (set (map :entry-title colls))
             (atom-results->title-set
              (search/find-concepts-json
               :collection (util/remove-nil-keys
                            {:token token
                             :page-size 100
                             :concept-id all-coll-concept-ids}))))
          "Guests find nothing" nil []
          "group1" user1 group1-colls
          "group2" user2 group2-colls
          "group3" user3 group3-colls
          "group4" user4 group4-colls))

      (testing "Collection OpenData ACL Enforcement by concept id"
        (let [open-data-results (search/find-concepts-opendata :collection {:token user1
                                                                            :page-size 100
                                                                            :concept-id all-coll-concept-ids})]
          (is (= (set (map :entry-title group1-colls))
                 (set (map :title (get-in open-data-results [:results :dataset])))))))

      (testing "Direct transformer retrieval acl enforcement"
        (is (= (set (map :concept-id group1-colls))
               (set (map :concept-id (:items (search/find-metadata
                                              :collection :dif
                                              {:token user1
                                               :page-size 100
                                               :concept-id all-coll-concept-ids}))))))))))

(deftest granule-search-with-temporal-acls-test
  (let [group1-concept-id (e/get-or-create-group (s/context) "group1")
        group2-concept-id (e/get-or-create-group (s/context) "group2")
        group3-concept-id (e/get-or-create-group (s/context) "group3")
        group4-concept-id (e/get-or-create-group (s/context) "group4")
        collection (d/ingest "PROV1" (dc/collection {:beginning-date-time (tu/n->date-time-string 0)}))
        gran-num (atom 0)
        single-date-gran (fn [n metadata-format]
                           (d/ingest
                             "PROV1"
                             (dg/granule collection
                                         {:granule-ur (str "gran" (swap! gran-num inc))
                                          :single-date-time (tu/n->date-time-string n)})
                             {:format metadata-format}))
        range-date-gran (fn [begin end metadata-format]
                          (d/ingest
                            "PROV1"
                            (dg/granule collection
                                        {:granule-ur (str "gran" (swap! gran-num inc))
                                         :beginning-date-time (tu/n->date-time-string begin)
                                         :ending-date-time (tu/n->date-time-string end)})
                            {:format metadata-format}))]
    ;; Set current time
    (dev-sys-util/freeze-time! (tu/n->date-time-string now-n))

    ;; Users have access to the collection
    (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV1"))
    (grant-temporal :granule group1-concept-id "intersect" 0 5)
    (grant-temporal :granule group2-concept-id "intersect" 5 9)
    (grant-temporal :granule group3-concept-id "disjoint" 3 5)
    (grant-temporal :granule group4-concept-id "contains" 3 7)

    ;; Create granules
    (let [gran1 (single-date-gran 1 :echo10)
          gran2 (range-date-gran 2 3 :iso-smap)
          gran3 (single-date-gran 4 :echo10)
          gran4 (range-date-gran 5 6 :iso-smap)
          gran5 (range-date-gran 3 nil :echo10) ;; no end date
          gran6 (single-date-gran 8 :echo10)
          gran7 (single-date-gran 9 :iso-smap)
          all-grans [gran1 gran2 gran3 gran4 gran5 gran6 gran7]

          ;; User tokens
          ;; Each user is associated with one of the groups above.
          user1 (e/login (s/context) "user1" [group1-concept-id])
          user2 (e/login (s/context) "user2" [group2-concept-id])
          user3 (e/login (s/context) "user3" [group3-concept-id])
          user4 (e/login (s/context) "user4" [group4-concept-id])

          ;; Create sets of granules visible for each group
          group1-granules [gran1 gran2 gran3 gran4 gran5]
          group2-granules [gran4 gran5 gran6 gran7]
          group3-granules [gran1 gran6 gran7]
          group4-granules [gran3 gran4]]
      (ingest/reindex-collection-permitted-groups (tc/echo-system-token))
      (index/wait-until-indexed)

      (testing "Parameter searching ACL enforcement"
        (are2 [token items]
              (d/refs-match? items (search/find-refs :granule (when token {:token token})))
              "Guests find nothing" nil []
              "group1" user1 group1-granules
              "group2" user2 group2-granules
              "group3" user3 group3-granules
              "group4" user4 group4-granules))

      (testing "ATOM ACL Enforcement by concept id"
        (are2 [token items]
              (let [concept-ids (map :concept-id all-grans)
                    expected-urs (set (map :granule-ur items))]
                (is (= expected-urs
                       (atom-results->title-set (search/find-concepts-atom
                                                  :granule (util/remove-nil-keys
                                                             {:token token
                                                              :page-size 100
                                                              :concept-id concept-ids}))))))
              "Guests find nothing" nil []
              "group1" user1 group1-granules
              "group2" user2 group2-granules
              "group3" user3 group3-granules
              "group4" user4 group4-granules))

      (testing "JSON ACL Enforcement by concept id"
        (are2 [token items]
              (let [concept-ids (map :concept-id all-grans)
                    expected-urs (set (map :granule-ur items))]
                (is (= expected-urs
                       (atom-results->title-set (search/find-concepts-json
                                                  :granule (util/remove-nil-keys
                                                             {:token token
                                                              :page-size 100
                                                              :concept-id concept-ids}))))))
              "Guests find nothing" nil []
              "group1" user1 group1-granules
              "group2" user2 group2-granules
              "group3" user3 group3-granules
              "group4" user4 group4-granules))

      (testing "CSV ACL Enforcement by concept id"
        (are2 [token items]
              (let [concept-ids (map :concept-id all-grans)]
                (= (set (map :granule-ur items))
                   (set (search/csv-response->granule-urs
                          (search/find-concepts-csv
                            :granule
                            (util/remove-nil-keys
                              {:token token
                               :page-size 100
                               :concept-id concept-ids}))))))
              "Guests find nothing" nil []
              "group1" user1 group1-granules
              "group2" user2 group2-granules
              "group3" user3 group3-granules
              "group4" user4 group4-granules))

      (testing "Direct transformer retrieval acl enforcement"
        (d/assert-metadata-results-match
          :echo10 group1-granules
          (search/find-metadata :granule :echo10 {:token user1
                                                  :page-size 100
                                                  :concept-id (map :concept-id all-grans)})))

      (testing "granule counts acl enforcement"
        (let [refs-result (search/find-refs :collection {:token user1
                                                         :include-granule-counts true})]
          (is (gran-counts/granule-counts-match? :xml {collection 5} refs-result)))))))
