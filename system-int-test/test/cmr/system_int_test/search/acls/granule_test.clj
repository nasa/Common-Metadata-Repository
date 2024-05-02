(ns cmr.system-int-test.search.acls.granule-test
  "Tests searching for collections with ACLs in place."
  (:require
   [clojure.test :refer :all]
   [clojure.string :as str]
   [cmr.common.services.messages :as msg]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.atom :as da]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.granule-counts :as gran-counts]
   [cmr.system-int-test.data2.core :as d]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.transmit.config :as tc]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"
                                           "provguid3" "PROV3"
                                           "provguid4" "PROV4"
                                           "provguid5" "PROV5"}
                                          {:grant-all-search? false}))

(defn make-coll
  ([n prov]
   (make-coll n prov {}))
  ([n prov attribs]
   (d/ingest prov
             (dc/collection
               (merge {:entry-title (str "coll" n)}
                      attribs)))))

(defn make-gran
  ([n coll]
   (make-gran n coll {}))
  ([n coll attribs]
   (let [prov (:provider-id coll)
         attribs (merge {:granule-ur (str "gran" n)}
                        attribs)]
     (d/ingest prov (dg/granule coll attribs)))))

(comment
  (do
    (dev-sys-util/reset)
    (ingest/create-provider {:provider-guid "provguid1" :provider-id "PROV1"} {:grant-all-search? false})
    (ingest/create-provider {:provider-guid "provguid2" :provider-id "PROV2"} {:grant-all-search? false})
    (ingest/create-provider {:provider-guid "provguid3" :provider-id "PROV3"} {:grant-all-search? false})
    (ingest/create-provider {:provider-guid "provguid4" :provider-id "PROV4"} {:grant-all-search? false})
    (ingest/create-provider {:provider-guid "provguid5" :provider-id "PROV5"} {:grant-all-search? false})))

(deftest granule-search-with-no-acls-test
  ;; system token can see all granules with no ACLs
  (let [guest-token (e/login-guest (s/context))
        coll1 (make-coll 1 "PROV1")
        coll2 (make-coll 2 "PROV1")
        gran1 (make-gran 1 coll1)
        gran2 (make-gran 2 coll1 {:access-value 1000.0})
        gran3 (make-gran 3 coll2)
        gran4 (make-gran 4 coll2 {:access-value 9.0})
        gran5 (make-gran 5 coll2 {:access-value 10.0})]
    (index/wait-until-indexed)

    ;;system token sees everything
    (is (d/refs-match? [gran1 gran2 gran3 gran4 gran5]
                       (search/find-refs :granule {:token (tc/echo-system-token)})))
    ;;guest sees nothing
    (is (d/refs-match? []
                       (search/find-refs :granule {:token guest-token})))))

(deftest granule-search-with-acls-test
  (let [coll1 (make-coll 1 "PROV1")
        coll2 (make-coll 2 "PROV1")
        coll3 (make-coll 3 "PROV2" {:access-value 2.0})
        coll4 (make-coll 4 "PROV2" {:access-value 5.0})
        coll5 (make-coll 5 "PROV3")
        coll6 (make-coll 6 "PROV4")
        coll7 (make-coll 7 "PROV2")
        coll8 (make-coll 8 "PROV2" {:access-value 30.0})
        coll51 (make-coll 51 "PROV5")
        coll52 (make-coll 52 "PROV5")
        coll53 (make-coll 53 "PROV5")
        all-colls [coll1 coll2 coll3 coll4 coll5 coll6 coll7 coll51 coll52 coll53]

        ;; - PROV1 -
        gran1 (make-gran 1 coll1)
        gran2 (make-gran 2 coll1 {:access-value 1000.0})

        ;; Permitted through undefined access value
        gran3 (make-gran 3 coll2)
        ; Not permitted at all (outside of access value range)
        gran4 (make-gran 4 coll2 {:access-value 9.0})
        ;; Permitted through access value range
        gran5 (make-gran 5 coll2 {:access-value 10.0})

        ;; - PROV2 -
        ;; Permitted by collection id and coll access value
        gran6 (make-gran 6 coll3)
        ;; Permitted by collection 4's access value
        gran7 (make-gran 7 coll4)

        ;; Not permitted from granule access value
        gran10 (make-gran 10 coll7)
        ;; Permitted by access value
        gran11 (make-gran 11 coll7 {:access-value 31.0})

        ;; Not permitted. The collection has an access value that matches an acl with a non-existent
        ;; collection
        gran12 (make-gran 12 coll8)

        ;; - PROV3 -
        ;; All granules in prov 3 are permitted
        gran8 (make-gran 8 coll5)
        gran9 (make-gran 9 coll5 {:access-value 0.0})

        ;; - PROV4 - no permitted access
        gran13 (make-gran 13 coll6)

        ;; - PROV5 -
        ;; Not permitted because it has an access value
        gran51 (make-gran 51 coll51 {:access-value 0.0})
        ;; Permitted because it doesn't have an access value
        gran52 (make-gran 52 coll51 {:access-value nil})

        ;; permitted because it is above min
        gran53 (make-gran 53 coll52 {:access-value 11.0})
        ;; permitted because it is equal to min
        gran54 (make-gran 54 coll52 {:access-value 10.0})
        ;; not permitted below min
        gran55 (make-gran 55 coll52 {:access-value 9.0})

        ;; permitted below max
        gran56 (make-gran 56 coll53 {:access-value 9.0})
        ;; permitted equal to max
        gran57 (make-gran 57 coll53 {:access-value 10.0})
        ;; not permitted above max
        gran58 (make-gran 58 coll53 {:access-value 11.0})

        all-grans [gran1 gran2 gran3 gran4 gran5 gran6 gran7 gran8 gran9 gran10 gran11 gran12
                   gran13 gran51 gran52 gran54 gran53 gran55 gran56 gran57 gran58]

        ;; Tokens
        guest-token (e/login-guest (s/context))
        user1-token (e/login (s/context) "user1")

        guest-permitted-granules [gran1 gran2 gran3 gran5 gran11 gran8 gran9]
        guest-permitted-granule-colls [coll1 coll1 coll2 coll2 coll7 coll5 coll5]
        user-permitted-granules [gran6 gran7 gran52 gran53 gran54 gran56 gran57]]

    (index/wait-until-indexed)
    ;; Guests have access to coll1
    (e/grant-guest (s/context) (e/gran-catalog-item-id "PROV1" (e/coll-id ["coll1"])))
    (e/grant-guest (s/context) (e/gran-catalog-item-id "PROV1" (e/coll-id ["collnonexist"])))
    ;; coll 2 has no granule permissions
    ;; Permits granules with access values.
    (e/grant-guest (s/context) (e/gran-catalog-item-id "PROV1" nil
                                                       (e/gran-id {:min-value 10
                                                                   :max-value 20
                                                                   :include_undefined_value true})))

    ;; -- PROV2 --
    ;; Combined collection identifier and granule identifier
    (e/grant-guest
      (s/context)
      (e/gran-catalog-item-id "PROV2" (e/coll-id ["coll7"]) (e/gran-id {:min-value 30
                                                                        :max-value 40})))
    (e/grant-registered-users
      (s/context)
      (e/gran-catalog-item-id "PROV2" (e/coll-id ["coll3"] {:min-value 1 :max-value 3})))
    (e/grant-registered-users
      (s/context)
      (e/gran-catalog-item-id "PROV2" (e/coll-id [] {:min-value 4 :max-value 6})))

    ;; Acls that grant nothing
    (e/grant-guest (s/context) (e/gran-catalog-item-id "PROV2" (e/coll-id ["nonexist1"])))
    (e/grant-guest (s/context) (e/gran-catalog-item-id "PROV2" (e/coll-id [] {:min-value 4000 :max-value 4000})))
    (e/grant-registered-users (s/context) (e/gran-catalog-item-id "PROV2" (e/coll-id ["nonexist2"])))
    (e/grant-guest (s/context) (e/gran-catalog-item-id "PROV2"
                                                       (e/coll-id ["notexist3"]) (e/gran-id {:min-value 30
                                                                                             :max-value 40})))
    ;; -- PROV3 --
    ;; Guests have full access to provider 3
    (e/grant-guest (s/context) (e/gran-catalog-item-id "PROV3"))

    ;; Added for CMR-835: This should have no effect on other providers.
    (e/grant-guest (s/context) (e/gran-catalog-item-id "PROV3" (e/coll-id [] {:include_undefined_value true})))


    ;; -- PROV4 --
    ;; no acls

    ;; -- PROV5 --
    ;; lots of access value filters
    (e/grant-registered-users (s/context)
                              (e/gran-catalog-item-id "PROV5" (e/coll-id ["coll51"]) (e/gran-id {:include_undefined_value true})))
    (e/grant-registered-users (s/context)
                              (e/gran-catalog-item-id "PROV5" (e/coll-id ["coll52"]) (e/gran-id {:min-value 10 :max-value 256})))
    (e/grant-registered-users (s/context)
                              (e/gran-catalog-item-id "PROV5" (e/coll-id ["coll53"]) (e/gran-id {:min-value -256 :max-value 10})))

    ;; Grant all collections to guests so that granule counts query can be executed
    (dotimes [n 5]
      (e/grant-all (s/context) (e/coll-catalog-item-id (str "PROV" (inc n)))))

    (dev-sys-util/clear-caches)
    (ingest/reindex-collection-permitted-groups (tc/echo-system-token))
    (index/wait-until-indexed)

    (testing "Search for refs"
      (are [expected params]
           (let [refs-result (search/find-refs :granule params)
                 match? (d/refs-match? expected refs-result)]
             (when-not match?
               (println "Expected:" (map :concept-id expected))
               (println "Actual:" (map :id (:refs refs-result)))
               (println "Expected:" (map :granule-ur expected))
               (println "Actual:" (map :name (:refs refs-result))))
             match?)
           guest-permitted-granules {:token guest-token}
           user-permitted-granules {:token user1-token}

           ;; Test searching with each collection id as guest and user
           ;; Entry title searches
           [gran1 gran2] {:token guest-token :entry-title "coll1"}
           [gran3 gran5] {:token guest-token :entry-title "coll2"}
           [gran1 gran2 gran3 gran5 gran11] {:token guest-token :entry-title ["coll1" "coll2" "coll7"]}

           ;; provider id
           (filter #(= "PROV1" (:provider-id %))
                   guest-permitted-granules)
           {:token guest-token :provider-id "PROV1"}

           ;; provider id and entry title
           [gran1 gran2] {:token guest-token :entry-title "coll1" :provider-id "PROV1"}
           [] {:token guest-token :entry-title "coll5" :provider-id "PROV1"}

           ;; concept id
           [gran1 gran2] {:token guest-token :concept-id (:concept-id coll1)}
           [gran3 gran5] {:token guest-token :concept-id (:concept-id coll2)}
           guest-permitted-granules
           {:token guest-token :concept-id (cons "C999-PROV1" (map :concept-id all-colls))}
           user-permitted-granules
           {:token user1-token :concept-id (cons "C999-PROV1" (map :concept-id all-colls))}))

    (testing "ATOM ACL Enforcement"
      (testing "all items"
        (let [gran-atom (da/granules->expected-atom
                          guest-permitted-granules
                          guest-permitted-granule-colls
                          (format "granules.atom?token=%s&page_size=100" guest-token))]
          (is (= gran-atom (:results (search/find-concepts-atom :granule {:token guest-token
                                                                          :page-size 100}))))))

      (testing "by concept id"
        (let [concept-ids (map :concept-id all-grans)
              gran-atom (da/granules->expected-atom
                          guest-permitted-granules
                          guest-permitted-granule-colls
                          (str "granules.atom?token=" guest-token
                               "&page_size=100&concept_id="
                               (str/join "&concept_id=" concept-ids)))]
          (is (= gran-atom (:results (search/find-concepts-atom :granule {:token guest-token
                                                                          :page-size 100
                                                                          :concept-id concept-ids})))))))
    (testing "JSON ACL Enforcement"
      (testing "all items"
        (let [gran-atom (da/granules->expected-atom
                          guest-permitted-granules
                          guest-permitted-granule-colls
                          (format "granules.json?token=%s&page_size=100" guest-token))]
          (is (= gran-atom (:results (search/find-concepts-json :granule {:token guest-token
                                                                          :page-size 100}))))))

      (testing "by concept id"
        (let [concept-ids (map :concept-id all-grans)
              gran-atom (da/granules->expected-atom
                          guest-permitted-granules
                          guest-permitted-granule-colls
                          (str "granules.json?token=" guest-token
                               "&page_size=100&concept_id="
                               (str/join "&concept_id=" concept-ids)))]
          (is (= gran-atom (:results (search/find-concepts-json :granule {:token guest-token
                                                                          :page-size 100
                                                                          :concept-id concept-ids})))))))

    (testing "csv"
      (testing "all items"
        (let [expected-granule-urs (map :granule-ur guest-permitted-granules)]
          (is (= expected-granule-urs
                 (search/csv-response->granule-urs
                   (search/find-concepts-csv :granule {:token guest-token
                                                       :page-size 100}))))))

      (testing "by concept id"
        (let [concept-ids (map :concept-id all-grans)
              expected-granule-urs (map :granule-ur guest-permitted-granules)]
          (is (= expected-granule-urs
                 (search/csv-response->granule-urs
                   (search/find-concepts-csv :granule {:token guest-token
                                                       :page-size 100
                                                       :concept-id concept-ids})))))))


    (testing "Direct transformer retrieval acl enforcement"
      (d/assert-metadata-results-match
        :echo10 user-permitted-granules
        (search/find-metadata :granule :echo10 {:token user1-token
                                                :page-size 100
                                                :concept-id (map :concept-id all-grans)})))

    (testing "granule counts acl enforcement"
      (testing "guest"
        (let [refs-result (search/find-refs :collection {:token guest-token
                                                         :include-granule-counts true})]
          (is (gran-counts/granule-counts-match?
                :xml {coll1 2 coll2 2 coll3 0 coll4 0 coll5 2
                      coll6 0 coll7 1 coll51 0 coll52 0 coll53 0}
                refs-result))))

      (testing "user"
        (let [refs-result (search/find-refs :collection {:token user1-token
                                                         :include-granule-counts true})]
          (is (gran-counts/granule-counts-match?
                :xml {coll1 0 coll2 0 coll3 1 coll4 1 coll5 0
                      coll6 0 coll7 0 coll51 1 coll52 2 coll53 2}
                refs-result)))))

    (testing "has granules created at acl enforcement"
      (testing "guest"
        (let [refs-result (search/find-refs
                           :collection {:token guest-token
                                        :has-granules-created-at ["1975-01-01T10:00:00Z,"]})]
          (d/assert-refs-match [coll1 coll2 coll5 coll7] refs-result)))
      (testing "user"
        (let [refs-result (search/find-refs
                           :collection {:token user1-token
                                        :has-granules-created-at ["1975-01-01T10:00:00Z,"]})]
          (d/assert-refs-match [coll3 coll4 coll51 coll52 coll53] refs-result))))))

(deftest granule-search-changed-entry-titles-test
  (let [group1-concept-id (e/get-or-create-group (s/context) "group1")
        guest-token (e/login-guest (s/context))
        user1-token (e/login (s/context) "user1" [group1-concept-id])
        user2-token (e/login (s/context) "user2")
        coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"
                                                :native-id "coll1"}))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2"
                                                :native-id "coll2"}))
        gran1 (make-gran 1 coll1)
        gran2 (make-gran 2 coll1)
        gran3 (make-gran 3 coll2)
        gran4 (make-gran 4 coll2)
        all-colls [coll1 coll2]]

    (index/wait-until-indexed)
    (e/grant-group (s/context) group1-concept-id (e/gran-catalog-item-id
                                                   "PROV1" (e/coll-id ["coll1" "coll2"])))
    (d/assert-refs-match [gran1 gran2 gran3 gran4] (search/find-refs
                                                    :granule
                                                    {:token user1-token
                                                     :concept-id (map :concept-id all-colls)}))
    (d/assert-refs-match [] (search/find-refs :granule {:token user2-token
                                                        :concept-id (map :concept-id all-colls)}))
    (d/assert-refs-match [] (search/find-refs :granule {:token guest-token
                                                        :concept-id (map :concept-id all-colls)}))
    ;; Update entry-title in collection.
    (d/ingest "PROV1" (dc/collection {:entry-title "coll1-edited"
                                      :native-id "coll1"}))

    (index/wait-until-indexed)
    (dev-sys-util/clear-caches)

    ;; Confirm change in entry-title has not affected granule search.
    (d/assert-refs-match [gran1 gran2 gran3 gran4] (search/find-refs
                                                    :granule
                                                    {:token user1-token
                                                     :concept-id (map :concept-id all-colls)}))
    (d/assert-refs-match [] (search/find-refs :granule {:token user2-token
                                                        :concept-id (map :concept-id all-colls)}))
    (d/assert-refs-match [] (search/find-refs :granule {:token guest-token
                                                        :concept-id (map :concept-id all-colls)}))))
