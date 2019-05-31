(ns cmr.access-control.int-test.acl-search-permitted-concept-id-test
  "Contains tests for searching ACLs by permitted concept id. For a given collection
   concept id, ACLs can grant permission to the collection through temporal, access-value
   or entry-title. For a given granule concept id, ACLs can grant permission to the granule
   through temporal, access-value or parent collection id."
  (:require
   [clj-time.core :as t]
   [clojure.test :refer :all]
   [cmr.access-control.int-test.fixtures :as fixtures]
   [cmr.access-control.test.util :as u]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.transmit.access-control :as ac]
   [cmr.umm.umm-collection :as c]))

(use-fixtures :each
              (fixtures/reset-fixture {"prov1guid" "PROV1", "prov2guid" "PROV2"} ["user1"])
              (fixtures/grant-all-group-fixture ["PROV1" "PROV2"])
              (fixtures/grant-all-acl-fixture))
(use-fixtures :once (fixtures/int-test-fixtures))

(deftest invalid-permitted-concept-id-search-test
  (testing "Invalid permitted concept id"
    (is (= {:status 400
            :body {:errors ["Must be collection or granule concept id."]}
            :content-type :json}
           (ac/search-for-acls
            (u/conn-context) {:permitted-concept-id "BAD_CONCEPT_ID"} {:raw? true}))))
  (testing "Permitted concept id does not exist"
    (is (= {:status 400
            :body {:errors ["permitted_concept_id does not exist."]}
            :content-type :json}
           (ac/search-for-acls
            (u/conn-context) {:permitted-concept-id "C1200000001-PROV1"} {:raw? true})))))

(deftest acl-search-permitted-concept-id-through-temporal
  ;; This test is for searching ACLs by permitted concept id.  For a given
  ;; collection concept id or granule concept id,
  ;; acls granting permission to this collection by temporal
  ;; are returned.
  (let [token (e/login (u/conn-context) "user1")

        coll1 (u/save-collection {:entry-title "coll1 entry title"
                                  :short-name "coll1"
                                  :native-id "coll1"
                                  :provider-id "PROV1"
                                  :temporal-range {:BeginningDateTime (t/date-time 2010)
                                                   :EndingDateTime (t/date-time 2011)}
                                  :format :umm-json})

        coll2 (u/save-collection {:entry-title "coll2 entry title"
                                  :short-name "coll2"
                                  :native-id "coll2"
                                  :provider-id "PROV1"
                                  :temporal-range {:BeginningDateTime (t/date-time 2009)
                                                   :EndingDateTime (t/date-time 2010)}
                                  :format :iso-smap})

        coll3 (u/save-collection {:entry-title "coll3 entry title"
                                  :short-name "coll3"
                                  :native-id "coll3"
                                  :provider-id "PROV1"
                                  :temporal-range {:BeginningDateTime (t/date-time 2011)
                                                   :EndingDateTime (t/date-time 2012)}
                                  :format :iso19115})

        coll4 (u/save-collection {:entry-title "coll4 entry title"
                                  :short-name "coll4"
                                  :native-id "coll4"
                                  :provider-id "PROV1"
                                  :temporal-range {:BeginningDateTime (t/date-time 2011 1 1 0 0 1)
                                                   :EndingDateTime (t/date-time 2012)}
                                  :format :echo10})

        coll5 (u/save-collection {:entry-title "coll5 entry title"
                                  :short-name "coll5"
                                  :native-id "coll5"
                                  :provider-id "PROV1"
                                  :temporal-range {:BeginningDateTime (t/date-time 2009)
                                                   :EndingDateTime (t/date-time 2009 12 31 12 59 59)}
                                  :format :dif10})

        coll6 (u/save-collection {:entry-title "coll6 entry title"
                                  :short-name "coll6"
                                  :native-id "coll6"
                                  :provider-id "PROV1"
                                  :temporal-range {:BeginningDateTime (t/date-time 2009 12 31 12 59 59)
                                                   :EndingDateTime (t/date-time 2012 1 1 0 0 1)}
                                  :format :dif})

        coll7 (u/save-collection {:entry-title "coll7 entry title"
                                  :short-name "coll7"
                                  :native-id "coll7"
                                  :provider-id "PROV1"
                                  :temporal-range {:BeginningDateTime (t/date-time 2009 12 31 12 59 59)}})

        coll8 (u/save-collection {:entry-title "coll8 entry title"
                                  :short-name "coll8"
                                  :native-id "coll8"
                                  :provider-id "PROV1"
                                  :temporal-singles #{(t/date-time 2012 1 1 0 0 1)}})

        gran1 (u/save-granule coll1
                              {:temporal {:range-date-time {:beginning-date-time (t/date-time 2010)
                                                            :ending-date-time (t/date-time 2011)}}}
                              :umm-json)
        gran2 (u/save-granule coll2 {:temporal {:range-date-time {:beginning-date-time (t/date-time 2009)
                                                                  :ending-date-time (t/date-time 2010)}}})
        gran3 (u/save-granule coll3 {:temporal {:range-date-time {:beginning-date-time (t/date-time 2011)
                                                                  :ending-date-time (t/date-time 2012)}}})
        gran4 (u/save-granule coll4 {:temporal {:range-date-time {:beginning-date-time (t/date-time 2011 1 1 0 0 1)
                                                                  :ending-date-time (t/date-time 2012)}}})
        gran5 (u/save-granule coll5 {:temporal {:range-date-time {:beginning-date-time (t/date-time 2009)
                                                                  :ending-date-time (t/date-time 2009 12 31 12 59 59)}}})
        gran6 (u/save-granule coll6 {:temporal {:range-date-time {:beginning-date-time (t/date-time 2009 12 31 12 59 59)
                                                                  :ending-date-time (t/date-time 2012 1 1 0 0 1)}}})
        gran7 (u/save-granule coll7 {:temporal {:range-date-time {:beginning-date-time (t/date-time 2009 12 31 12 59 59)}}})
        gran8 (u/save-granule coll8 {:temporal {:single-date-time (t/date-time 2012 1 1 0 0 1)}})

        acl1 (u/ingest-acl token (assoc (u/catalog-item-acl "Access value 1-10")
                                        :catalog_item_identity {:name "Access value 1-10"
                                                                :collection_applicable true
                                                                :collection_identifier {:access_value {:min_value 1 :max_value 10}}
                                                                :granule_applicable true
                                                                :granule_identifier {:access_value {:min_value 1 :max_value 10}}
                                                                :provider_id "PROV1"}))
        acl2 (u/ingest-acl token (u/catalog-item-acl "No collection identifier"))
        acl3 (u/ingest-acl token (assoc-in (u/catalog-item-acl "No collection identifier PROV2")
                                           [:catalog_item_identity :provider_id] "PROV2"))

        acl4 (u/ingest-acl token (assoc (u/catalog-item-acl "Temporal contains")
                                        :catalog_item_identity {:name "Temporal contains"
                                                                :collection_applicable true
                                                                :collection_identifier {:temporal {:start_date "2010-01-01T00:00:00Z"
                                                                                                   :stop_date "2011-01-01T00:00:00Z"
                                                                                                   :mask "contains"}}
                                                                :granule_applicable true
                                                                :granule_identifier {:temporal {:start_date "2010-01-01T00:00:00Z"
                                                                                                :stop_date "2011-01-01T00:00:00Z"
                                                                                                :mask "contains"}}
                                                                :provider_id "PROV1"}))
        acl5 (u/ingest-acl token (assoc (u/catalog-item-acl "Temporal intersect")
                                        :catalog_item_identity {:name "Temporal intersect"
                                                                :collection_applicable true
                                                                :collection_identifier {:temporal {:start_date "2010-01-01T00:00:00Z"
                                                                                                   :stop_date "2011-01-01T00:00:00Z"
                                                                                                   :mask "intersect"}}
                                                                :granule_applicable true
                                                                :granule_identifier {:temporal {:start_date "2010-01-01T00:00:00Z"
                                                                                                :stop_date "2011-01-01T00:00:00Z"
                                                                                                :mask "intersect"}}
                                                                :provider_id "PROV1"}))
        acl6 (u/ingest-acl token (assoc (u/catalog-item-acl "Temporal disjoint")
                                        :catalog_item_identity {:name "Temporal disjoint"
                                                                :collection_applicable true
                                                                :collection_identifier {:temporal {:start_date "2010-01-01T00:00:00Z"
                                                                                                   :stop_date "2011-01-01T00:00:00Z"
                                                                                                   :mask "disjoint"}}
                                                                :granule_applicable true
                                                                :granule_identifier {:temporal {:start_date "2010-01-01T00:00:00Z"
                                                                                                :stop_date "2011-01-01T00:00:00Z"
                                                                                                :mask "disjoint"}}
                                                                :provider_id "PROV1"}))]
    (testing "collection concept id search temporal"
      (are3 [params acls]
        (let [response (ac/search-for-acls (u/conn-context) params)]
          (is (= (u/acls->search-response (count acls) acls)
                 (dissoc response :took))))

        "gran1 test as umm-json"
        {:permitted-concept-id gran1}
        [acl2 acl4 acl5]

        "gran2 test"
        {:permitted-concept-id gran2}
        [acl2 acl5]

        "gran3 test"
        {:permitted-concept-id gran3}
        [acl2 acl5]

        "gran4 test"
        {:permitted-concept-id gran4}
        [acl2 acl6]

        "gran5 test"
        {:permitted-concept-id gran5}
        [acl2 acl6]

        "gran6 test"
        {:permitted-concept-id gran6}
        [acl2 acl5]

        "gran7 test"
        {:permitted-concept-id gran7}
        [acl2 acl5]

        "gran8 test"
        {:permitted-concept-id gran8}
        [acl2 acl6]

        "coll1 test"
        {:permitted-concept-id coll1}
        [acl2 acl4 acl5]

        "coll2 test"
        {:permitted-concept-id coll2}
        [acl2 acl5]

        "coll3 test"
        {:permitted-concept-id coll3}
        [acl2 acl5]

        "coll4 test"
        {:permitted-concept-id coll4}
        [acl2 acl6]

        "coll5 test"
        {:permitted-concept-id coll5}
        [acl2 acl6]

        "coll6 test"
        {:permitted-concept-id coll6}
        [acl2 acl5]

        "coll7 test"
        {:permitted-concept-id coll7}
        [acl2 acl5]

        "coll8 test"
        {:permitted-concept-id coll8}
        [acl2 acl6]))))

(deftest acl-search-permitted-concept-id-through-access-value
  ;; This test is for searching ACLs by permitted concept id.  For a given
  ;; collection concept id or granule concept id,
  ;; acls granting permission to this collection by access-value
  ;; are returned.
  (let [token (e/login (u/conn-context) "user1")
        save-access-value-collection (fn [short-name access-value format]
                                       (u/save-collection {:entry-title (str short-name " entry title")
                                                           :short-name short-name
                                                           :native-id short-name
                                                           :provider-id "PROV1"
                                                           :access-value access-value
                                                           :format format}))
        ;; one collection with a low access value
        coll1 (save-access-value-collection "coll1" 1 :umm-json)
        ;; one with an intermediate access value
        coll2 (save-access-value-collection "coll2" 2 :dif10)
        ;; one with a higher access value
        coll3 (save-access-value-collection "coll3" 3 :dif)
        ;; one with no access value
        coll4 (save-access-value-collection "coll4" nil :echo10)
        ;; one with FOO entry-title
        coll5 (u/save-collection {:entry-title "FOO"
                                  :short-name "coll5"
                                  :native-id "coll5"
                                  :provider-id "PROV1"
                                  :format :iso19115})
        ;; one with a different provider, shouldn't match
        coll6 (u/save-collection {:entry-title "coll6 entry title"
                                  :short-name "coll6"
                                  :native-id "coll6"
                                  :access-value 2
                                  :provider-id "PROV2"
                                  :format :iso-smap})

        gran1 (u/save-granule coll1 {:access-value 1} :umm-json)
        gran2 (u/save-granule coll2 {:access-value 2})
        gran3 (u/save-granule coll3 {:access-value 3})
        gran4 (u/save-granule coll4 {:access-value nil})
        gran5 (u/save-granule coll6 {:access-value 2 :provider-id "PROV2"})

        ;; For testing that a full range encompassing multiple collections will
        ;; properly match all collections
        acl1 (u/ingest-acl
              token (assoc (u/catalog-item-acl "Access value 1-3")
                           :catalog_item_identity
                           {:name "Access value 1-3"
                            :granule_applicable true
                            :granule_identifier {:access_value {:min_value 1 :max_value 3}}
                            :collection_applicable true
                            :collection_identifier {:access_value {:min_value 1 :max_value 3}}
                            :provider_id "PROV1"}))

        ;; For testing a single access value, instead of a range of multiple access values
        acl2 (u/ingest-acl
              token (assoc (u/catalog-item-acl "Access value 1")
                           :catalog_item_identity
                           {:name "Access value 1"
                            :granule_applicable true
                            :granule_identifier {:access_value {:min_value 1 :max_value 1}}
                            :collection_applicable true
                            :collection_identifier {:access_value {:min_value 1 :max_value 1}}
                            :provider_id "PROV1"}))
        ;; For testing a range, but one that doesn't include all posssible collections, with min value checked
        acl3 (u/ingest-acl
              token (assoc (u/catalog-item-acl "Access value 1-2")
                           :catalog_item_identity
                           {:name "Access value 1-2"
                            :granule_applicable true
                            :granule_identifier {:access_value {:min_value 1 :max_value 2}}
                            :collection_applicable true
                            :collection_identifier {:access_value {:min_value 1 :max_value 2}}
                            :provider_id "PROV1"}))
        ;; For testing a range, but one that doesn't include all posssible collections, with max value checked
        acl4 (u/ingest-acl
              token (assoc (u/catalog-item-acl "Access value 2-3")
                           :catalog_item_identity
                           {:name "Access value 2-3"
                            :granule_applicable true
                            :granule_identifier {:access_value {:min_value 2 :max_value 3}}
                            :collection_applicable true
                            :collection_identifier {:access_value {:min_value 2 :max_value 3}}
                            :provider_id "PROV1"}))
        ;; For testing an access value which will match no collections
        acl5 (u/ingest-acl
              token (assoc (u/catalog-item-acl "Access value 4")
                           :catalog_item_identity
                           {:name "Access value 4"
                            :granule_applicable true
                            :granule_identifier {:access_value {:min_value 4 :max_value 4}}
                            :collection_applicable true
                            :collection_identifier {:access_value {:min_value 4 :max_value 4}}
                            :provider_id "PROV1"}))
        ;; For testing on undefined access values
        acl6 (u/ingest-acl
              token (assoc (u/catalog-item-acl "Access value undefined")
                           :catalog_item_identity
                           {:name "include undefined value"
                            :granule_applicable true
                            :granule_identifier {:access_value {:include_undefined_value true}}
                            :collection_applicable true
                            :collection_identifier {:access_value {:include_undefined_value true}}
                            :provider_id "PROV1"}))

        ;; For testing that an ACL with no collection identifier will still match collections with
        ;; access values
        acl7 (u/ingest-acl token (u/catalog-item-acl "No collection identifier"))
        ;; Same as above, but with a different provider.
        acl8 (u/ingest-acl token (assoc-in (u/catalog-item-acl "No collection identifier PROV2")
                                           [:catalog_item_identity :provider_id] "PROV2"))
        ;; For testing that an ACL with a collection identifier other than access values
        ;; does not match
        acl9 (u/ingest-acl
              token (assoc (u/catalog-item-acl "Entry titles FOO")
                           :catalog_item_identity {:name "Entry titles FOO"
                                                   :collection_applicable true
                                                   :collection_identifier {:entry_titles ["FOO"]}
                                                   :provider_id "PROV1"}))]


    (testing "collection concept id search access value"
      (are3 [params acls]
        (let [response (ac/search-for-acls (u/conn-context) params)]
          (is (= (u/acls->search-response (count acls) acls)
                 (dissoc response :took))))

        "gran1 test as umm json"
        {:permitted-concept-id gran1}
        [acl1 acl2 acl3 acl7]

        "gran2 test"
        {:permitted-concept-id gran2}
        [acl1 acl3 acl4 acl7]

        "gran3 test"
        {:permitted-concept-id gran3}
        [acl1 acl4 acl7]

        "gran4 test"
        {:permitted-concept-id gran4}
        [acl6 acl7]

        "gran5 test"
        {:permitted-concept-id gran5}
        [acl8]

        "coll1 test"
        {:permitted-concept-id coll1}
        [acl1 acl2 acl3 acl7]

        "coll2 test"
        {:permitted-concept-id coll2}
        [acl1 acl3 acl4 acl7]

        "coll3 test"
        {:permitted-concept-id coll3}
        [acl1 acl4 acl7]

        "coll4 test"
        {:permitted-concept-id coll4}
        [acl6 acl7]

        "coll5 test"
        {:permitted-concept-id coll5}
        [acl7 acl9]

        "coll6 test"
        {:permitted-concept-id coll6}
        [acl8]))))


(deftest acl-search-permitted-concept-id-through-entry-title
  (let [token (e/login (u/conn-context) "user1")
        coll1 (u/save-collection {:entry-title "EI1"
                                  :short-name "coll1"
                                  :native-id "coll1"
                                  :provider-id "PROV1"})
        coll2 (u/save-collection {:entry-title "ei2"
                                  :short-name "coll2"
                                  :native-id "coll2"
                                  :provider-id "PROV1"})
        coll3 (u/save-collection {:entry-title "EI3"
                                  :short-name "coll3"
                                  :native-id "coll3"
                                  :provider-id "PROV1"})
        coll4 (u/save-collection {:entry-title "EI1"
                                  :short-name "coll4"
                                  :native-id "coll4"
                                  :provider-id "PROV2"})

        acl1 (u/ingest-acl token (assoc (u/catalog-item-acl "PROV1 EI1")
                                      :catalog_item_identity {:name "Entry title EI1"
                                                              :collection_applicable true
                                                              :collection_identifier {:entry_titles ["EI1"]}
                                                              :provider_id "PROV1"}))
        acl2 (u/ingest-acl token (assoc (u/catalog-item-acl "PROV1 ei2")
                                      :catalog_item_identity {:name "Entry title ei2"
                                                              :collection_applicable true
                                                              :collection_identifier {:entry_titles ["ei2"]}
                                                              :provider_id "PROV1"}))
        acl3 (u/ingest-acl token (assoc (u/catalog-item-acl "PROV1 ei2 EI3")
                                      :catalog_item_identity {:name "Entry title ei2 EI3"
                                                              :collection_applicable true
                                                              :collection_identifier {:entry_titles ["EI3" "ei2"]}
                                                              :provider_id "PROV1"}))
        acl4 (u/ingest-acl token (assoc (u/catalog-item-acl "PROV2 EI1")
                                      :catalog_item_identity {:name "Entry title PROV2 EI1"
                                                              :collection_applicable true
                                                              :collection_identifier {:entry_titles ["EI1"]}
                                                              :provider_id "PROV2"}))
        ;; ACL references PROV1 with no collection identifier
        acl5 (u/ingest-acl token (u/catalog-item-acl "No collection identifier"))]


    (testing "collection concept id search entry title"
      (are3 [params acls]
        (let [response (ac/search-for-acls (u/conn-context) params)]
          (is (= (u/acls->search-response (count acls) acls)
                 (dissoc response :took))))
        "coll1 test"
        {:permitted-concept-id coll1}
        [acl1 acl5]

        "coll2 test"
        {:permitted-concept-id coll2}
        [acl2 acl3 acl5]

        "coll3 test"
        {:permitted-concept-id coll3}
        [acl3 acl5]

        "coll4 test"
        {:permitted-concept-id coll4}
        [acl4]))))

(deftest acl-search-permitted-concept-id-through-parent-collection
  ;; If an ACL is granule applicable, has a collection identifier
  ;; but doesn't have a granule identifier, then all granules associated with a collection
  ;; matching this ACL are also matched in the permitted-concept-id search
  (let [token (e/login (u/conn-context) "user1")
        coll1 (u/save-collection {:entry-title "coll1 entry title"
                                  :short-name "coll1"
                                  :native-id "coll1"
                                  :provider-id "PROV1"
                                  :access-value 1
                                  :temporal-range {:BeginningDateTime (t/date-time 2009)
                                                   :EndingDateTime (t/date-time 2010)}})

        ;; Needs to exist to create FOO entry title ACL
        coll2 (u/save-collection {:entry-title "FOO"
                                  :short-name "coll5"
                                  :native-id "coll5"
                                  :provider-id "PROV1"})

        ;; Needs to exist to create PROV2 ACL with "coll1 entry title"
        coll3 (u/save-collection {:entry-title "coll1 entry title"
                                  :short-name "coll1-prov2"
                                  :native-id "coll1-prov2"
                                  :provider-id "PROV2"})

        gran1 (u/save-granule
               coll1 {:access-value nil
                      :provider-id "PROV1"
                      :temporal {:range-date-time {:beginning-date-time (t/date-time 2009)
                                                   :ending-date-time (t/date-time 2010)}}})

        ;; All possible collection identifiers and granule identifiers present and match
        acl1 (u/ingest-acl
              token (assoc (u/catalog-item-acl "all identifiers match")
                           :catalog_item_identity
                           {:name "all identifiers match"
                            :collection_applicable true
                            :granule_applicable true
                            :granule_identifier {:temporal {:start_date "2009-01-01T00:00:00Z"
                                                            :stop_date "2010-01-01T00:00:00Z"
                                                            :mask "contains"}
                                                 :access_value {:include_undefined_value true}}
                            :collection_identifier {:temporal {:start_date "2009-01-01T00:00:00Z"
                                                               :stop_date "2010-01-01T00:00:00Z"
                                                               :mask "contains"}
                                                    :entry_titles ["coll1 entry title"]
                                                    :access_value {:min_value 1 :max_value 1}}
                            :provider_id "PROV1"}))

        ;; Only entry title collection identifier present and match. Should be found.
        acl2 (u/ingest-acl
              token (assoc (u/catalog-item-acl "entry title collection identifier")
                           :catalog_item_identity
                           {:name "entry title collection identifier"
                            :collection_applicable false
                            :granule_applicable true
                            :granule_identifier {}
                            :collection_identifier {:entry_titles ["coll1 entry title"]}
                            :provider_id "PROV1"}))

        ;; Only temporal collection identifier present and match. Should be found.
        acl3 (u/ingest-acl
              token (assoc (u/catalog-item-acl "temporal collection identifier")
                           :catalog_item_identity
                           {:name "temporal collection identifier"
                            :collection_applicable true
                            :granule_applicable true
                            :granule_identifier {}
                            :collection_identifier {:temporal {:start_date "2009-01-01T00:00:00Z"
                                                               :stop_date "2010-01-01T00:00:00Z"
                                                               :mask "contains"}}
                            :provider_id "PROV1"}))

        ;; Only access value collection identifier present and match. Should be found.
        acl4 (u/ingest-acl
              token (assoc (u/catalog-item-acl "access value collection identifier")
                           :catalog_item_identity
                           {:name "access value collection identifier"
                            :collection_applicable true
                            :granule_applicable true
                            :granule_identifier {}
                            :collection_identifier {:access_value {:min_value 1 :max_value 1}}
                            :provider_id "PROV1"}))

        ;; Some of collection identifiers (entry title and temporal) and granule identifiers
        ;; present and match, collection_applicable is false. Should be found.
        ;; collection_applicable has no effect on search result.
        acl5 (u/ingest-acl
              token (assoc (u/catalog-item-acl "some collection identifiers")
                           :catalog_item_identity
                           {:name "some collection identifiers"
                            :collection_applicable false
                            :granule_applicable true
                            :granule_identifier {:access_value {:include_undefined_value true}}
                            :collection_identifier {:temporal {:start_date "2009-01-01T00:00:00Z"
                                                               :stop_date "2010-01-01T00:00:00Z"
                                                               :mask "contains"}
                                                    :entry_titles ["coll1 entry title"]}
                            :provider_id "PROV1"}))

        ;; no collection or granule identifier. Should be found.
        acl6 (u/ingest-acl
              token (assoc (u/catalog-item-acl "no collection identifier")
                           :catalog_item_identity
                           {:name "no collection identifier"
                            :collection_applicable true
                            :granule_applicable true
                            :granule_identifier {}
                            :collection_identifier {}
                            :provider_id "PROV1"}))

        ;; no collection or granule identifier, granule-applicable false. Should not be found.
        acl7 (u/ingest-acl
              token (assoc (u/catalog-item-acl "granule-applicable false")
                           :catalog_item_identity
                           {:name "granule-applicable false"
                            :collection_applicable true
                            :granule_applicable false
                            :granule_identifier {}
                            :collection_identifier {}
                            :provider_id "PROV1"}))

        ;; Only entry title collection identifier present and not match. Should not be found.
        acl8 (u/ingest-acl
              token (assoc (u/catalog-item-acl "entry title collection identifier not match")
                           :catalog_item_identity
                           {:name "entry title collection identifier not match"
                            :collection_applicable false
                            :granule_applicable true
                            :granule_identifier {}
                            :collection_identifier {:entry_titles ["FOO"]}
                            :provider_id "PROV1"}))

        ;; Only temporal collection identifier present and not match. Should not be found.
        acl9 (u/ingest-acl
              token (assoc (u/catalog-item-acl "temporal collection identifier not match")
                           :catalog_item_identity
                           {:name "temporal collection identifier not match"
                            :collection_applicable true
                            :granule_applicable true
                            :granule_identifier {}
                            :collection_identifier {:temporal {:start_date "2009-01-01T00:00:00Z"
                                                               :stop_date "2010-01-01T00:00:00Z"
                                                               :mask "disjoint"}}
                            :provider_id "PROV1"}))

        ;; Only access value collection identifier present and not match Should not be found.
        acl10 (u/ingest-acl
               token (assoc (u/catalog-item-acl "access value collection identifier not match")
                            :catalog_item_identity
                            {:name "access value collection identifier not match"
                             :collection_applicable false
                             :granule_applicable true
                             :granule_identifier {}
                             :collection_identifier {:access_value {:min_value 2 :max_value 2}}
                             :provider_id "PROV1"}))

        ;; one of collection identifiers not match, some match. Should not be found.
        acl11 (u/ingest-acl
               token (assoc (u/catalog-item-acl "collection applicable false")
                            :catalog_item_identity
                            {:name "collection applicable false"
                             :collection_applicable false
                             :granule_applicable true
                             :granule_identifier {:access_value {:include_undefined_value true}}
                             :collection_identifier {:temporal {:start_date "2009-01-01T00:00:00Z"
                                                                :stop_date "2010-01-01T00:00:00Z"
                                                                :mask "contains"}
                                                     :entry_titles ["FOO"]}
                             :provider_id "PROV1"}))
        ;; entry title collection identifier present and match but ACL on a different provider.
        ;; Should not be found.
        acl12 (u/ingest-acl
               token (assoc (u/catalog-item-acl "PROV2 entry title collection identifier")
                            :catalog_item_identity
                            {:name "PROV2 entry title collection identifier"
                             :collection_applicable false
                             :granule_applicable true
                             :granule_identifier {}
                             :collection_identifier {:entry_titles ["coll1 entry title"]}
                             :provider_id "PROV2"}))
        expected-acls [acl1 acl2 acl3 acl4 acl5 acl6]]
    (testing "granule concept id search parent collection"
      (let [response (ac/search-for-acls (u/conn-context) {:permitted-concept-id gran1})]
        (is (= (u/acls->search-response (count expected-acls) expected-acls)
               (dissoc response :took)))))))

(deftest acl-search-permitted-concept-id-through-multiple-collection-identifier-filters
  (let [token (e/login (u/conn-context) "user1")
        coll1 (u/save-collection {:entry-title "EI1"
                                  :short-name "coll1"
                                  :native-id "coll1"
                                  :provider-id "PROV1"
                                  :access-value 1
                                  :temporal-range {:BeginningDateTime (t/date-time 2010)
                                                   :EndingDateTime (t/date-time 2011)}})
        coll2 (u/save-collection {:entry-title "EI2"
                                  :short-name "coll2"
                                  :native-id "coll2"
                                  :provider-id "PROV1"})
        ingest-coll-identifier-acl (fn [name coll-identifier]
                                     (u/ingest-acl
                                      token (assoc (u/catalog-item-acl name)
                                                   :catalog_item_identity
                                                   {:name name
                                                    :collection_applicable true
                                                    :collection_identifier coll-identifier
                                                    :provider_id "PROV1"})))
        ;; ACL matches coll1 on all filters
        acl1 (ingest-coll-identifier-acl "ACL1" {:entry_titles ["EI1"]
                                                 :access_value {:min_value 1 :max_value 3}
                                                 :temporal {:start_date "2010-01-01T00:00:00Z"
                                                            :stop_date "2011-01-01T00:00:00Z"
                                                            :mask "contains"}})
        ;; ACL matches coll1 on entry-title and acccess value, no temporal filter
        acl2 (ingest-coll-identifier-acl "ACL2" {:entry_titles ["EI1"]
                                                 :access_value {:min_value 1 :max_value 3}})
        ;; ACL matches coll1 on entry-title and temporal, no access value filter
        acl3 (ingest-coll-identifier-acl "ACL3" {:entry_titles ["EI1"]
                                                 :temporal {:start_date "2010-01-01T00:00:00Z"
                                                            :stop_date "2011-01-01T00:00:00Z"
                                                            :mask "contains"}})
        ;; ACL matches coll1 on access value and temporal, no entry title filter
        acl4 (ingest-coll-identifier-acl "ACL4" {:access_value {:min_value 1 :max_value 3}
                                                 :temporal {:start_date "2010-01-01T00:00:00Z"
                                                            :stop_date "2011-01-01T00:00:00Z"
                                                            :mask "contains"}})
        ;; ACL matches coll1 on entry title and access value, but not temporal filter
        acl5 (ingest-coll-identifier-acl "ACL5" {:entry_titles ["EI1"]
                                                 :access_value {:min_value 1 :max_value 3}
                                                 :temporal {:start_date "2001-01-01T00:00:00Z"
                                                            :stop_date "2009-01-01T00:00:00Z"
                                                            :mask "contains"}})
        ;; ACL matches coll1 on entry title and temporal, but not access value filter
        acl6 (ingest-coll-identifier-acl "ACL6" {:entry_titles ["EI1"]
                                                 :access_value {:min_value 2 :max_value 3}
                                                 :temporal {:start_date "2010-01-01T00:00:00Z"
                                                            :stop_date "2011-01-01T00:00:00Z"
                                                            :mask "contains"}})
        ;; ACL matches coll1 on access value and temporal, but not entry title filter
        acl7 (ingest-coll-identifier-acl "ACL7" {:entry_titles ["EI2"]
                                                 :access_value {:min_value 1 :max_value 3}
                                                 :temporal {:start_date "2010-01-01T00:00:00Z"
                                                            :stop_date "2011-01-01T00:00:00Z"
                                                            :mask "contains"}})]
    (testing "collection identifier multiple filters search"
      (let [expected-acls [acl1 acl2 acl3 acl4]
            response (ac/search-for-acls (u/conn-context) {:permitted-concept-id coll1})]
        (is (= (u/acls->search-response (count expected-acls) expected-acls)
               (dissoc response :took)))))))

(deftest acl-search-permitted-concept-id-through-multiple-granule-identifier-filters
  (let [token (e/login (u/conn-context) "user1")
        coll1 (u/save-collection {:entry-title "EI1"
                                  :short-name "coll1"
                                  :native-id "coll1"
                                  :provider-id "PROV1"
                                  :access-value 1
                                  :temporal-range {:BeginningDateTime (t/date-time 2009)
                                                   :EndingDateTime (t/date-time 2011)}})
        gran1 (u/save-granule
               coll1 {:access-value 1
                      :provider-id "PROV1"
                      :temporal {:range-date-time {:beginning-date-time (t/date-time 2009)
                                                   :ending-date-time (t/date-time 2010)}}})

        ingest-granule-identifier-acl (fn [name granule-identifier]
                                        (u/ingest-acl
                                         token (assoc (u/catalog-item-acl name)
                                                      :catalog_item_identity
                                                      {:name name
                                                       :collection_applicable false
                                                       :collection_identifier {}
                                                       :granule_applicable true
                                                       :granule_identifier granule-identifier
                                                       :provider_id "PROV1"})))
        ;; ACL matches gran1 on all filters
        acl1 (ingest-granule-identifier-acl "ACL1" {:access_value {:min_value 1 :max_value 3}
                                                    :temporal {:start_date "2009-01-01T00:00:00Z"
                                                               :stop_date "2010-01-01T00:00:00Z"
                                                               :mask "contains"}})
        ;; ACL matches gran1 on acccess value, no temporal filter
        acl2 (ingest-granule-identifier-acl "ACL2" {:access_value {:min_value 1 :max_value 3}})
        ;; ACL matches gran1 on temporal, no access value filter
        acl3 (ingest-granule-identifier-acl "ACL3" {:temporal {:start_date "2009-01-01T00:00:00Z"
                                                               :stop_date "2010-01-01T00:00:00Z"
                                                               :mask "contains"}})
        ;; ACL matches gran1 on access value, but not temporal filter
        acl4 (ingest-granule-identifier-acl "ACL4" {:access_value {:min_value 1 :max_value 3}
                                                    :temporal {:start_date "2011-01-01T00:00:00Z"
                                                               :stop_date "2012-01-01T00:00:00Z"
                                                               :mask "contains"}})
        ;; ACL matches gran1 on temporal, but not access value filter
        acl5 (ingest-granule-identifier-acl "ACL5" {:access_value {:min_value 2 :max_value 3}
                                                    :temporal {:start_date "2009-01-01T00:00:00Z"
                                                               :stop_date "2010-01-01T00:00:00Z"
                                                               :mask "contains"}})]
    (testing "granule identifier multiple filters search"
      (let [expected-acls [acl1 acl2 acl3]
            response (ac/search-for-acls (u/conn-context) {:permitted-concept-id gran1})]
        (is (= (u/acls->search-response (count expected-acls) expected-acls)
               (dissoc response :took)))))))
