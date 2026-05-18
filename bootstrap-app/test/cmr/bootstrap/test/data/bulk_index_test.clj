(ns cmr.bootstrap.test.data.bulk-index-test
  (:require
   [clj-time.core :as time]
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing]]
   [cmr.bootstrap.data.bulk-index :as bulk-index]
   [cmr.bootstrap.embedded-system-helper :as helper]
   [cmr.metadata-db.data.oracle.concept-tables :as concept-tables]))

(deftest date-time->sql-timestamp-literal-test
  (is (= "TO_TIMESTAMP_TZ('2026-05-13T01:02:03.004Z', 'YYYY-MM-DD\"T\"HH24:MI:SS.FF3\"Z\"')"
         (#'bulk-index/date-time->sql-timestamp-literal
          (time/date-time 2026 5 13 1 2 3 4))))
  (is (= "TO_TIMESTAMP_TZ('2026-05-13T01:02:03.004Z', 'YYYY-MM-DD\"T\"HH24:MI:SS.FF3\"Z\"')"
         (#'bulk-index/date-time->sql-timestamp-literal
          "2026-05-13T01:02:03.004Z"))))

(deftest find-concepts-between-date-times-sql-test
  (with-redefs [concept-tables/get-table-name (constantly "concepts_table")]
    (testing "uses a half-open revision-date range"
      (let [sql (#'bulk-index/find-concepts-between-date-times-sql
                 {:provider-id "PROV1"}
                 :collection
                 (time/date-time 2026 5 13 1 0)
                 (time/date-time 2026 5 13 2 0))]
        (is (= (str "select * from concepts_table where revision_date >= "
                    "TO_TIMESTAMP_TZ('2026-05-13T01:00:00.000Z', "
                    "'YYYY-MM-DD\"T\"HH24:MI:SS.FF3\"Z\"') and revision_date < "
                    "TO_TIMESTAMP_TZ('2026-05-13T02:00:00.000Z', "
                    "'YYYY-MM-DD\"T\"HH24:MI:SS.FF3\"Z\"')")
               sql))))

    (testing "escapes provider ids in provider-specific clauses"
      (let [sql (#'bulk-index/find-concepts-between-date-times-sql
                 {:provider-id "PROV'1"}
                 :access-group
                 "2026-05-13T01:00:00Z"
                 "2026-05-13T02:00:00Z")]
        (is (string/includes? sql "and provider_id = 'PROV''1'"))))

    (testing "adds provider clause for miscellaneous provider concepts"
      (let [sql (#'bulk-index/find-concepts-between-date-times-sql
                 {:provider-id "PROV1"}
                 :variable
                 "2026-05-13T01:00:00Z"
                 "2026-05-13T02:00:00Z")]
        (is (string/includes? sql "and provider_id = 'PROV1'"))))

    (testing "does not add provider clause for CMR miscellaneous concepts"
      (let [sql (#'bulk-index/find-concepts-between-date-times-sql
                 {:provider-id "CMR"}
                 :variable
                 "2026-05-13T01:00:00Z"
                 "2026-05-13T02:00:00Z")]
        (is (not (string/includes? sql "provider_id =")))))))

(deftest index-data-between-date-time-aggregates-provider-and-system-results
  (let [calls (atom [])
        start (time/date-time 2026 5 13 1 0)
        end (time/date-time 2026 5 13 2 0)
        collection-max (time/date-time 2026 5 13 1 15)
        granule-max (time/date-time 2026 5 13 1 30)
        system-max (time/date-time 2026 5 13 1 45)]
    (with-redefs [helper/get-provider (fn [_system provider-id]
                                        {:provider-id provider-id})
                  bulk-index/fetch-and-index-concepts-between-date-times
                  (fn [_system provider concept-type start-date-time end-date-time]
                    (swap! calls conj [(:provider-id provider)
                                       concept-type
                                       start-date-time
                                       end-date-time])
                    (case concept-type
                      :collection {:num-indexed 2 :max-revision-date collection-max}
                      :granule {:num-indexed 3 :max-revision-date granule-max}))
                  bulk-index/index-system-misc-concepts-between-date-times
                  (fn [_system start-date-time end-date-time]
                    (swap! calls conj ["CMR" :system start-date-time end-date-time])
                    {:num-indexed 4 :max-revision-date system-max})]
      (is (= {:message "Indexed 5 provider concepts and 4 system concepts."
              :max-revision-date system-max}
             (bulk-index/index-data-between-date-time
              {:db-batch-size 10}
              ["PROV1" "CMR"]
              start
              end)))
      (is (= [["PROV1" :collection start end]
              ["PROV1" :granule start end]
              ["CMR" :system start end]]
             @calls)))))

(deftest index-data-between-date-time-indexes-all-providers-when-provider-ids-empty
  (let [calls (atom [])
        start (time/date-time 2026 5 13 1 0)
        end (time/date-time 2026 5 13 2 0)]
    (with-redefs [helper/get-providers (constantly [{:provider-id "PROV1"}
                                                    {:provider-id "PROV2"}])
                  bulk-index/fetch-and-index-concepts-between-date-times
                  (fn [_system provider concept-type start-date-time end-date-time]
                    (swap! calls conj [(:provider-id provider)
                                       concept-type
                                       start-date-time
                                       end-date-time])
                    {:num-indexed 1 :max-revision-date start-date-time})
                  bulk-index/index-system-misc-concepts-between-date-times
                  (fn [_system start-date-time end-date-time]
                    (swap! calls conj ["CMR" :system start-date-time end-date-time])
                    {:num-indexed 2 :max-revision-date end-date-time})]
      (is (= {:message "Indexed 4 provider concepts and 2 system concepts."
              :max-revision-date end}
             (bulk-index/index-data-between-date-time
              {:db-batch-size 10}
              nil
              start
              end)))
      (is (= [["PROV1" :collection start end]
              ["PROV1" :granule start end]
              ["PROV2" :collection start end]
              ["PROV2" :granule start end]
              ["CMR" :system start end]]
             @calls)))))

(deftest index-provider-data-between-date-time-routes-cmr-to-system-concepts
  (let [calls (atom [])
        start (time/date-time 2026 5 13 1 0)
        end (time/date-time 2026 5 13 2 0)]
    (with-redefs [bulk-index/index-system-misc-concepts-between-date-times
                  (fn [_system start-date-time end-date-time]
                    (swap! calls conj [:system start-date-time end-date-time])
                    {:num-indexed 3 :max-revision-date end-date-time})]
      (bulk-index/index-provider-data-between-date-time
       {:db-batch-size 10}
       "CMR"
       start
       end)
      (is (= [[:system start end]] @calls)))))
