(ns cmr.bootstrap.test.data.bulk-index-test
  (:require
   [clj-time.core :as time]
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing]]
   [cmr.bootstrap.data.bulk-index :as bulk-index]
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
        (is (string/includes? sql "and provider_id = 'PROV''1'"))))))
