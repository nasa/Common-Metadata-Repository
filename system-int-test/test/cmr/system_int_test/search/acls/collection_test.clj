(ns cmr.system-int-test.search.acls.collection-test
  "Tests searching for collections with ACLs in place."
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.acl.acl-fetcher :as acl-fetcher]
   [cmr.common-app.test.side-api :as side]
   [cmr.common.services.messages :as msg]
   [cmr.common.util :as util]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.atom :as da]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.opendata :as od]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.transmit.access-control :as ac]
   [cmr.transmit.config :as tc]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"
                                              "provguid3" "PROV3" "provguid4" "PROV4"}
                                             {:grant-all-search? false})
                       (search/freeze-resume-time-fixture)]))

(comment
  (dev-sys-util/reset)
  (ingest/create-provider {:provider-guid "PROV1" :provider-id "PROV1"})
  (ingest/create-provider {:provider-guid "PROV2" :provider-id "PROV2"})
  (ingest/create-provider {:provider-guid "PROV3" :provider-id "PROV3"}))

(deftest invalid-security-token-test
  (is (= {:errors ["Token does not exist"], :status 401}
         (search/find-refs :collection {:token "ABC123"}))))

(deftest expired-security-token-test
  (is (= {:errors ["Token [expired-token] has expired."], :status 401}
         (search/find-refs :collection {:token "expired-token"}))))

(deftest collection-search-with-no-acls-test
  ;; system token can see all collections with no ACLs
  (let [guest-token (e/login-guest (s/context))
        c1-echo (d/ingest "PROV1"
                          (dc/collection {:entry-title "c1-echo" :access-value 1})
                          {:format :echo10})
        c1-dif (d/ingest "PROV1"
                         (dc/collection-dif {:entry-title "c1-dif" :access-value 1})
                         {:format :dif})
        c1-dif10 (d/ingest "PROV1"
                           (dc/collection-dif10 {:entry-title "c1-dif10" :access-value 1})
                           {:format :dif10})
        c1-iso (d/ingest "PROV1"
                         (dc/collection {:entry-title "c1-iso" :access-value 1})
                         {:format :iso19115})
        c1-smap (d/ingest "PROV1"
                          (dc/collection {:entry-title "c1-smap" :access-value 1})
                          {:format :iso-smap})]
    (index/wait-until-indexed)

    ;;;;system token sees everything
    (d/assert-refs-match [c1-echo c1-dif c1-dif10 c1-iso c1-smap]
                         (search/find-refs :collection {:token (tc/echo-system-token)}))
    ;;guest user sees nothing
    (d/assert-refs-match [] (search/find-refs :collection {:token guest-token}))))

(deftest collection-search-with-restriction-flag-acls-test
  (let [guest-token (e/login-guest (s/context))
        c1-echo (d/ingest "PROV1" (dc/collection {:entry-title "c1-echo"
                                                  :access-value 1})
                          {:format :echo10})
        c2-echo (d/ingest "PROV1" (dc/collection {:entry-title "c2-echo"
                                                  :access-value 0})
                          {:format :echo10})
        c1-dif (d/ingest "PROV1" (dc/collection-dif {:entry-title "c1-dif"
                                                     :access-value 1})
                         {:format :dif})
        c2-dif (d/ingest "PROV1" (dc/collection-dif {:entry-title "c2-dif"
                                                     :access-value 0})
                         {:format :dif})
        c1-dif10 (d/ingest "PROV1" (dc/collection-dif10 {:entry-title "c1-dif10"
                                                         :access-value 1})
                           {:format :dif10})
        c2-dif10 (d/ingest "PROV2" (dc/collection-dif10 {:entry-title "c2-dif10"
                                                         :access-value 0})
                           {:format :dif10})
        c1-iso (d/ingest "PROV1" (dc/collection {:entry-title "c1-iso"
                                                 :access-value 1})
                         {:format :iso19115})
        c2-iso (d/ingest "PROV1" (dc/collection {:entry-title "c2-iso"
                                                 :access-value 0})
                         {:format :iso19115})
        ;; access-value is not supported in ISO-SMAP, so it won't be found
        c1-smap (d/ingest "PROV1" (dc/collection {:entry-title "c1-smap"
                                                  :access-value 1})
                          {:format :iso-smap})
        coll3 (d/ingest "PROV1" (dc/collection {:entry-title "coll3"}))]
    (index/wait-until-indexed)

    ;; grant restriction flag acl
    (e/grant-guest (s/context)
                   (e/coll-catalog-item-id
                     "PROV1"
                     (e/coll-id ["c1-echo" "c2-echo" "c1-dif" "c2-dif" "c1-dif10"
                                 "c1-iso" "c2-iso" "c1-smap" "coll3"]
                                {:min-value 0.5 :max-value 1.5})))
    (ingest/reindex-collection-permitted-groups (tc/echo-system-token))
    (index/wait-until-indexed)

    (d/assert-refs-match [c1-echo c1-dif c1-dif10 c1-iso]
                         (search/find-refs :collection {:token guest-token}))))

(deftest collection-search-with-acls-test
  (let [group1-concept-id (e/get-or-create-group (s/context) "group1")
        group2-concept-id (e/get-or-create-group (s/context) "group2")
        group3-concept-id (e/get-or-create-group (s/context) "group3")

        sk1 (dc/science-keyword {:category "Cat1"
                                 :topic "Topic1"
                                 :term "Term1"
                                 :variable-level-1 "Level1-1"
                                 :variable-level-2 "Level1-2"
                                 :variable-level-3 "Level1-3"
                                 :detailed-variable "Detail1"})

        coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1" :science-keywords [sk1]}))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2"}))
        coll3 (d/ingest "PROV1" (dc/collection {:entry-title "coll3"}))
        coll4 (d/ingest "PROV1" (dc/collection {:entry-title "coll4"
                                                :science-keywords [sk1]
                                                :access-value 5.0}))
        ;; no permission granted on coll5
        coll5 (d/ingest "PROV1" (dc/collection {:entry-title "coll5"}))

        ;; PROV2
        coll6 (d/ingest "PROV2" (dc/collection {:entry-title "coll6" :science-keywords [sk1]}))
        coll7 (d/ingest "PROV2" (dc/collection {:entry-title "coll7" :science-keywords [sk1]}))
        ;; A dif collection
        coll8 (d/ingest "PROV2" (dc/collection-dif
                                  {:entry-title "coll8"
                                   :science-keywords [sk1]
                                   :short-name "S8"
                                   :version-id "V8"
                                   :long-name "coll8"})
                        {:format :dif})
        ;; added for atom results
        coll8 (assoc coll8 :original-format "DIF")

        ;; PROV3
        coll9 (d/ingest "PROV3" (dc/collection {:entry-title "coll9" :science-keywords [sk1]}))
        coll10 (d/ingest "PROV3" (dc/collection {:entry-title "coll10"
                                                 :access-value 12.0}))
        ;; PROV4
        ;; group3 has permission to read this collection revision
        coll11-1 (d/ingest "PROV4" (dc/collection {:entry-title "coll11"
                                                   :native-id "coll11"
                                                   :access-value 32.0}))
        ;; tombstone
        coll11-2 (assoc (ingest/delete-concept (d/item->concept coll11-1) {:token (tc/echo-system-token)})
                        :entry-title "coll11"
                        :deleted true
                        :revision-id 2)
        ;; no permissions to read this revision since entry-title has changed
        coll11-3 (d/ingest "PROV4" (dc/collection {:entry-title "coll11"
                                                   :native-id "coll11"
                                                   :access-value 34.0}))
        ;; group 3 has permission to read this collection revision
        coll12-1 (d/ingest "PROV4" (dc/collection {:entry-title "coll12"
                                                   :access-value 32.0
                                                   :native-id "coll12"}))
        ;; no permissions to read this collection since entry-title has changed
        coll12-2 (d/ingest "PROV4" (dc/collection {:entry-title "coll12"
                                                   :access-value 34.0
                                                   :native-id "coll12"}))
        ;; no permision to see this tombstone since it has same entry-title as coll12-2
        coll12-3 (assoc (ingest/delete-concept (d/item->concept coll12-2))
                        :deleted true
                        :revision-id 2)

        all-colls [coll1 coll2 coll3 coll4 coll5 coll6 coll7 coll8 coll9 coll10]
        guest-permitted-collections [coll1 coll4 coll6 coll7 coll8 coll9]
        guest-token (e/login-guest (s/context))
        user1-token (e/login (s/context) "user1")
        user2-token (e/login (s/context) "user2" [group1-concept-id])
        user3-token (e/login (s/context) "user3" [group1-concept-id group2-concept-id])
        user4-token (e/login (s/context) "user4" [group3-concept-id])]

    (index/wait-until-indexed)

    ;; Grant guests permission to coll1
    (e/grant-guest (s/context) (e/coll-catalog-item-id "PROV1" (e/coll-id ["coll1"])))
    (e/grant-guest (s/context) (e/coll-catalog-item-id "PROV1" (e/coll-id ["notexist"])))
    ;; restriction flag acl grants matches coll4
    (e/grant-guest (s/context) (e/coll-catalog-item-id
                                "PROV1" (e/coll-id ["coll4"] {:min-value 4 :max-value 6})))

    ;; Grant undefined access values in prov3
    (e/grant-guest (s/context) (e/coll-catalog-item-id
                                "PROV3" (e/coll-id nil {:include_undefined_value true})))

    ;; all collections in prov2 granted to guests
    (e/grant-guest (s/context) (e/coll-catalog-item-id "PROV2"))
    ;; grant registered users permission to coll2 and coll4
    (e/grant-registered-users (s/context) (e/coll-catalog-item-id
                                           "PROV1" (e/coll-id ["coll2" "coll4"])))
    ;; grant specific group permission to coll3, coll6, and coll8
    (e/grant-group (s/context) group1-concept-id (e/coll-catalog-item-id
                                                  "PROV1" (e/coll-id ["coll3"])))
    (e/grant-group (s/context) group2-concept-id (e/coll-catalog-item-id
                                                  "PROV2" (e/coll-id ["coll6" "coll8"])))
    (e/grant-group (s/context) group3-concept-id (e/coll-catalog-item-id
                                                  "PROV4" (e/coll-id nil {:min-value 30 :max-value 33})))

    (ingest/reindex-collection-permitted-groups (tc/echo-system-token))
    (index/wait-until-indexed)

    (testing "parameter search acl enforcement"
      (util/are3 [token items]
        (d/assert-refs-match items (search/find-refs :collection (when token {:token token})))

        "not logged in should be guest"
        nil guest-permitted-collections

        "login and use guest token"
        guest-token guest-permitted-collections

        "test searching as a user"
        user1-token [coll2 coll4]

        "Test searching with users in groups - user2"
        user2-token [coll2 coll4 coll3]

        "Test searching with users in groups - user3"
        user3-token [coll2 coll4 coll3 coll6 coll8]))

    (testing "token can be sent through a header"
      (d/assert-refs-match [coll2 coll4]
                           (search/find-refs :collection {} {:headers {"Authorization" user1-token}})))
    (testing "aql search parameter enforcement"
      (d/assert-refs-match [coll2 coll4]
                           (search/find-refs-with-aql
                            :collection [] {} {:headers {"Authorization" user1-token}})))
    (testing "Direct transformer retrieval acl enforcement"
      (testing "registered user"
        (d/assert-metadata-results-match
         :echo10 [coll2 coll4]
         (search/find-metadata :collection :echo10 {:token user1-token
                                                    :concept-id (conj (map :concept-id all-colls)
                                                                      "C9999-PROV1")})))
      (testing "guest access"
        (d/assert-metadata-results-match
         :echo10 guest-permitted-collections
         (search/find-metadata :collection :echo10 {:token guest-token
                                                    :concept-id (map :concept-id all-colls)})))
      (testing "Empty token matches guest access"
        (d/assert-metadata-results-match
         :echo10 guest-permitted-collections
         (search/find-metadata :collection :echo10 {:token ""
                                                    :concept-id (map :concept-id all-colls)})))

      (testing "user in groups"
        (d/assert-metadata-results-match
         :echo10 [coll4 coll6 coll3 coll8 coll2]
         (search/find-metadata :collection :echo10 {:token user3-token
                                                    :concept-id (map :concept-id all-colls)}))))
    (testing "ATOM ACL enforcement"
      (testing "all items"
        (let [coll-atom (da/collections->expected-atom
                         guest-permitted-collections
                         (format "collections.atom?token=%s&page_size=100" guest-token))]
          (is (= coll-atom (:results (search/find-concepts-atom :collection {:token guest-token
                                                                             :page-size 100}))))))
      (testing "by concept id"
        (let [concept-ids (map :concept-id all-colls)
              coll-atom (da/collections->expected-atom
                         guest-permitted-collections
                         (str "collections.atom?token=" guest-token
                              "&page_size=100&concept_id="
                              (str/join "&concept_id=" concept-ids)))]
          (is (= coll-atom (:results (search/find-concepts-atom
                                      :collection {:token guest-token
                                                   :page-size 100
                                                   :concept-id concept-ids})))))))

    (testing "JSON ACL enforcement"
      (testing "all items"
        (let [coll-json (da/collections->expected-json
                         guest-permitted-collections
                         (format "collections.json?token=%s&page_size=100" guest-token))]
          (is (= coll-json (:results (search/find-concepts-json :collection {:token guest-token
                                                                             :page-size 100}))))))

      (testing "by concept id"
        (let [concept-ids (map :concept-id all-colls)
              coll-json (da/collections->expected-json
                         guest-permitted-collections
                         (str "collections.json?token=" guest-token
                              "&page_size=100&concept_id="
                              (str/join "&concept_id=" concept-ids)))
              {:keys [hits results]} (search/find-concepts-json
                                      :collection {:token guest-token
                                                   :page-size 100
                                                   :concept-id concept-ids})]
          (is (= hits (count (:entries results))))
          (is (= coll-json results)))))

    (testing "opendata ACL enforcement"
      (let [;; coll8's revision-date is needed to populate "modified" field in opendata.
            umm-json-coll8 (search/find-concepts-umm-json :collection {:token guest-token
                                                                       :concept_id (:concept-id coll8)})
            revision-date-coll8 (-> umm-json-coll8
                                    (get-in [:results :items])
                                    first
                                    (get-in [:meta :revision-date]))
            ;; Normally coll8 doesn't contain the :revision-date field. Only when this field is needed
            ;; to populate modified field, we add it to coll8 so that it can be used for the "expected" in opendata.clj
            coll8-opendata (assoc coll8 :revision-date revision-date-coll8)
            guest-permitted-collections-opendata [coll1 coll4 coll6 coll7 coll8-opendata coll9]]
        (testing "all items"
          (let [actual-od (search/find-concepts-opendata :collection {:token guest-token
                                                                      :page-size 100})]
            (od/assert-collection-opendata-results-match guest-permitted-collections-opendata actual-od)))

        (testing "by concept id"
          (let [concept-ids (map :concept-id all-colls)
                actual-od (search/find-concepts-opendata :collection {:token guest-token
                                                                      :page-size 100
                                                                      :concept-id concept-ids})]
            (od/assert-collection-opendata-results-match guest-permitted-collections-opendata actual-od)))))

    (testing "all_revisions"
      (util/are3 [collections params]
        (d/assert-refs-match collections (search/find-refs :collection params))

        ;; only old revisions satisfy ACL - they should not be returned
        "provider-id all-revisions=false"
        []
        {:provider-id "PROV4" :all-revisions false :token user4-token}

        ;; only permissioned revisions are returned
        "provider-id all-revisions=true"
        [coll11-1 coll12-1]
        {:provider-id "PROV4" :all-revisions true :token user4-token}

        ;; none of the revisions are readable by guest users
        "provider-id all-revisions=true no token"
        []
        {:provider-id "PROV4" :all-revisions true}))

    (testing "CMR-6532: hits match result"
      (let [coll-json (da/collections->expected-atom
                       []
                       (str "collections.json?token=" guest-token
                            "&page_size=100&concept_id="
                            (:concept-id coll5)))
            {:keys [hits results]} (search/find-concepts-json
                                    :collection {:token guest-token
                                                 :page-size 100
                                                 :concept-id (:concept-id coll5)})]
        (is (= hits (count (:entries results))))
        (is (= coll-json results))))))

;; This tests that when acls change after collections have been indexed that collections will be
;; reindexed when ingest detects the acl hash has change.
(deftest acl-change-test
  (let [coll1 (d/ingest "PROV1" (dc/collection-dif10 {:entry-title "coll1"}) {:format :dif10})
        coll2-umm (dc/collection {:entry-title "coll2" :short-name "short1"})
        coll2-1 (d/ingest "PROV1" coll2-umm)
        ;; 2 versions of collection 2 will allow us to test the force reindex option after we
        ;; force delete the latest version of coll2-2
        coll2-2 (d/ingest "PROV1" (assoc-in coll2-umm [:product :short-name] "short2"))
        coll3 (d/ingest "PROV2" (dc/collection-dif10 {:entry-title "coll3"}) {:format :dif10})
        coll4 (d/ingest "PROV2" (dc/collection {:entry-title "coll4"}))

        _ (index/wait-until-indexed)
        acl1 (e/grant-guest (s/context) (e/coll-catalog-item-id "PROV1" (e/coll-id ["coll1"])))
        acl2 (e/grant-guest (s/context) (e/coll-catalog-item-id "PROV2" (e/coll-id ["coll3"])))]

    (testing "normal reindex collection permitted groups"
      (ingest/reindex-collection-permitted-groups (tc/echo-system-token))
      (index/wait-until-indexed)

      ;; before acls change
      (d/assert-refs-match [coll1 coll3] (search/find-refs :collection {}))

      ;; Grant collection 2
      (e/grant-guest (s/context) (e/coll-catalog-item-id "PROV1" (e/coll-id ["coll2"])))
      ;; Ungrant collection 3
      (e/ungrant (s/context) acl2)

      ;; Try searching again before the reindexing
      (d/assert-refs-match [coll1 coll3] (search/find-refs :collection {}))

      ;; Reindex collection permitted groups
      (ingest/reindex-collection-permitted-groups (tc/echo-system-token))
      (index/wait-until-indexed)

      ;; Search after reindexing
      (d/assert-refs-match [coll1 coll2-2] (search/find-refs :collection {})))

    (testing "reindex all collections"

      ;; Grant collection 4
      (e/grant-guest (s/context) (e/coll-catalog-item-id "PROV2" (e/coll-id ["coll4"])))

      ;; Try before reindexing
      (d/assert-refs-match [coll1 coll2-2] (search/find-refs :collection {}))

      ;; Reindex all collections
      ;; Manually check the logs. It should say it's reindexing provider 1 and provider 3 as well.
      (ingest/reindex-all-collections)
      (index/wait-until-indexed)

      ;; Search after reindexing
      (d/assert-refs-match [coll1 coll2-2 coll4] (search/find-refs :collection {})))))

;; Verifies that tokens are cached by checking that a logged out token still works after it was
;; used. This isn't the desired behavior. It's just a side effect that shows it's working.
(deftest cache-token-test
  (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}))
        acl1 (e/grant-registered-users
              (s/context) (e/coll-catalog-item-id "PROV1" (e/coll-id ["coll1"])))
        user1-token (e/login (s/context) "user1")
        user2-token (e/login (s/context) "user2")]

    (ingest/reindex-collection-permitted-groups (tc/echo-system-token))
    (index/wait-until-indexed)

    ;; A logged out token is normally not useful
    (e/logout (s/context) user2-token)
    (is (= {:errors ["Token does not exist"], :status 401}
           (search/find-refs :collection {:token user2-token})))

    ;; Use user1-token so it will be cached
    (d/assert-refs-match [coll1] (search/find-refs :collection {:token user1-token}))

    ;; logout
    (e/logout (s/context) user1-token)
    ;; The token should be cached
    (d/assert-refs-match [coll1] (search/find-refs :collection {:token user1-token}))))

(deftest collection-search-changed-entry-titles-test
  (let [group1-concept-id (e/get-or-create-group (s/context) "group1")
        guest-token (e/login-guest (s/context))
        user1-token (e/login (s/context) "user1" [group1-concept-id])
        user2-token (e/login (s/context) "user2")
        coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"
                                                :native-id "coll1"}))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2"
                                                :native-id "coll2"}))
        coll1-edited (-> coll1
                         (assoc :entry-title "coll1-edited")
                         (assoc :revision-id 2))]
    (index/wait-until-indexed)
    (e/grant-group (s/context) group1-concept-id (e/coll-catalog-item-id
                                                   "PROV1" (e/coll-id ["coll1" "coll2"])))
    (ingest/reindex-collection-permitted-groups (tc/echo-system-token))
    (index/wait-until-indexed)
    (d/assert-refs-match [coll1 coll2] (search/find-refs :collection {:token user1-token}))
    (d/assert-refs-match [] (search/find-refs :collection {:token user2-token}))
    (d/assert-refs-match [] (search/find-refs :collection {:token guest-token}))
    (d/ingest "PROV1" (dc/collection {:entry-title "coll1-edited"
                                      :native-id "coll1"}))
    (index/wait-until-indexed)
    (d/assert-refs-match [coll1-edited coll2] (search/find-refs :collection {:token user1-token}))
    (d/assert-refs-match [] (search/find-refs :collection {:token user2-token}))
    (d/assert-refs-match [] (search/find-refs :collection {:token guest-token}))))
