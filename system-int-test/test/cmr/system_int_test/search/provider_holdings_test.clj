(ns cmr.system-int-test.search.provider-holdings-test
  "Integration tests for provider holdings"
  (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.search.api.providers :as providers]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"
                                           "provguid3" "PROV3"}
                                          {:grant-all-search? false}))

;; total number of collections in PROV1
(def prov1-collection-count 5)

(def prov1-grans-increment-count
  "Number of granule increment in each collection,
  e.g. first collection has prov1-grans-increment-count number of granules
  second collection has (2 * prov1-grans-increment-count) number of granules
  third collection has (3 * prov1-grans-increment-count) number of granules and so on."
  2)

(def prov2-collection-count 3)
(def prov2-grans-increment-count 3)

(def prov3-collection-count
  "The number of collections to create in provider 3. These collections will have no granules
  created. If you want to test the CMR with many collections you can set this to some very high
  number and run the test. I tried this with 12,000 and it completed successfully."
  3)

(defn- collection-holding
  "Returns the collection holding for the given collection and granule count"
  [provider-id coll granule-count]
  (let [{:keys [entry-title concept-id]} coll]
    {:entry-title entry-title
     :concept-id concept-id
     :granule-count granule-count
     :provider-id provider-id}))

(defn create-holdings
  "Set up the provider holdings fixtures for tests."
  []
  (let [;; Provider 1
        prov1-colls (doall (for [_ (range 0 prov1-collection-count)]
                             (d/ingest "PROV1" (dc/collection))))
        prov1-granule-counts (map #(* prov1-grans-increment-count %) (range 1 (inc prov1-collection-count)))
        prov1-holdings (map (partial collection-holding "PROV1") prov1-colls prov1-granule-counts)

        ;; Provider 2
        prov2-colls (doall (for [_ (range 0 prov2-collection-count)]
                             (d/ingest "PROV2" (dc/collection))))
        prov2-granule-counts (map #(* prov2-grans-increment-count %) (range 1 (inc prov2-collection-count)))
        prov2-holdings (map (partial collection-holding "PROV2") prov2-colls prov2-granule-counts)

        ;; Provider 3
        prov3-colls (doall (for [n (range 0 prov3-collection-count)]
                             (d/ingest "PROV3" (dc/collection))))
        prov3-holdings (doall (map #(collection-holding "PROV3" % 0) prov3-colls))]

    ;; Create provider 1 granules
    (dotimes [n prov1-collection-count]
      (let [coll (nth prov1-colls n)
            granule-count (nth prov1-granule-counts n)]
        (dotimes [m granule-count]
          (d/ingest "PROV1" (dg/granule coll)))))

    ;; Create provider 2 granules
    (dotimes [n prov2-collection-count]
      (let [coll (nth prov2-colls n)
            granule-count (nth prov2-granule-counts n)]
        (dotimes [m granule-count]
          (d/ingest "PROV2" (dg/granule coll)))))

    (index/wait-until-indexed)

    {"PROV1" prov1-holdings
     "PROV2" prov2-holdings
     "PROV3" prov3-holdings}))

(comment
  (do
    (dev-sys-util/reset)
    (ingest/create-provider {:provider-guid "provguid1" :provider-id "PROV1"})
    (ingest/create-provider {:provider-guid "provguid2" :provider-id "PROV2"})
    (ingest/create-provider {:provider-guid "provguid3" :provider-id "PROV3"})

    ;; Grant all holdings to registered users
    (e/grant-registered-users (s/context) (e/coll-catalog-item-id "provguid1"))
    (e/grant-registered-users (s/context) (e/coll-catalog-item-id "provguid2"))
    (e/grant-registered-users (s/context) (e/coll-catalog-item-id "provguid3"))

    ;; Grant provider 1 holdings to guests
    (e/grant-guest (s/context) (e/coll-catalog-item-id "provguid1"))


    (def all-holdings (create-holdings))
    (def expected-all-holdings (set (flatten (vals all-holdings))))
    (def guest-token (e/login-guest (s/context)))
    (def user-token (e/login (s/context) "user1")))


  (mdb/provider-holdings)

  )

(deftest retrieve-provider-holdings


  ;; Grant all holdings to registered users
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV1"))
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV2"))
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV3"))

  ;; Grant provider 1 holdings to guests
  (e/grant-guest (s/context) (e/coll-catalog-item-id "PROV1"))

  (let [all-holdings (create-holdings)
        expected-all-holdings (set (flatten (vals all-holdings)))
        guest-token (e/login-guest (s/context))
        user-token (e/login (s/context) "user1")]

    (testing "Retrieving provider holdings from Metadata DB"
      (let [response (mdb/provider-holdings)]
        (is (= 200 (:status response)))
        (is (= expected-all-holdings
               (set (:results response))))))

    (testing "Retrieving provider holdings from the search application in various formats"

      (testing "Retrieve all provider holdings in unsupported format opendata"
        (try
          (search/provider-holdings-in-format :opendata {:token user-token})
          (catch clojure.lang.ExceptionInfo e
            (is (= {:status 400
                    :body "{\"errors\":[\"Unsupported format: opendata on the provider holdings endpoint.\"]}"}
                   (select-keys (ex-data e) [:status :body]))))))

      (testing "Retrieve all provider holdings in unsupported format umm-json"
        (try
          (search/provider-holdings-in-format :umm-json {:token user-token})
          (catch clojure.lang.ExceptionInfo e
            (is (= {:status 400
                    :body "{\"errors\":[\"Unsupported format: umm-json-results on the provider holdings endpoint.\"]}"}
                   (select-keys (ex-data e) [:status :body]))))))

      (doseq [format [:xml :json :csv]]
        (testing (str (name format) " Response")
          (testing "Retrieve all provider holdings"
            (let [response (search/provider-holdings-in-format format {:token user-token})]
              (is (= 200 (:status response)))
              (is (= expected-all-holdings
                     (set (:results response))))))
          (testing "Retrieve provider holdings for list of providers"
            (let [response (search/provider-holdings-in-format format {:provider_id "PROV1"
                                                                       :token user-token})]
              (is (= 200 (:status response)))
              (is (= (set (get all-holdings "PROV1"))
                     (set (:results response)))))
            (let [response (search/provider-holdings-in-format
                             format
                             {:provider_id ["PROV1" "PROV2" "PROV3"]
                              :token user-token})]
              (is (= 200 (:status response)))
              (is (= expected-all-holdings
                     (set (:results response))))))
          (testing "Retrieve provider holdings applies acls"
            ;; Guests have access to all providers regardless of the ACLs
            (let [response (search/provider-holdings-in-format format {:token guest-token})]
              (is (= 200 (:status response)))
              (is (= expected-all-holdings
                     (set (:results response))))))
          (testing "Retrieve provider holdings with echo-compatible true"
            (let [response (search/provider-holdings-in-format
                             format {:token user-token :echo-compatible? true})]
              (is (= 200 (:status response)))
              (is (= expected-all-holdings
                     (set (:results response))))))
          (testing "As extension"
            (is (= (select-keys
                     (search/provider-holdings-in-format format {:provider_id "PROV1"
                                                                 :token user-token})
                     [:status :results])
                   (select-keys
                     (search/provider-holdings-in-format format
                                                         {:provider_id "PROV1"
                                                          :token user-token}
                                                         {:url-extension (name format)})
                     [:status :results]))))
          (testing "Retrieve provider holdings with count summaries in headers"
            (let [response (search/provider-holdings-in-format format {:provider_id "PROV1"
                                                                        :token user-token})
                  headers (:headers response)
                  header-granule-count (headers providers/CMR_GRANULE_COUNT_HEADER)
                  header-collection-count (headers providers/CMR_COLLECTION_COUNT_HEADER)
                  granule-count (reduce + (map :granule-count (all-holdings "PROV1")))]
              (is (= 200 (:status response)))
              (is (and (= (str prov1-collection-count) header-collection-count)
                       (= (str granule-count) header-granule-count))))))))))
