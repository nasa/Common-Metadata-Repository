(ns cmr.metadata-db.test.data.oracle.search
  (:require
   [clj-time.coerce :as time-coerce]
   [clj-time.core :as time]
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing]]
   [cmr.metadata-db.data.oracle.search :as search]))

(deftest find-concepts-between-date-times-sql-test
  (let [start-date-time (time/date-time 2026 5 13 1)
        end-date-time (time/date-time 2026 5 13 2)]
    (testing "uses bound params for a half-open revision-date range"
      (let [[sql start-param end-param] (#'search/find-concepts-between-date-times-sql
                                         "concepts_table"
                                         "PROV1"
                                         :collection
                                         start-date-time
                                         end-date-time)
            sql (string/lower-case sql)]
        (is (string/starts-with? sql "select "))
        (is (string/includes? sql " from concepts_table "))
        (is (string/includes? sql "revision_date >= ?"))
        (is (string/includes? sql "revision_date < ?"))
        (is (not (string/includes? sql "select *")))
        (is (= [(time-coerce/to-sql-time start-date-time)
                (time-coerce/to-sql-time end-date-time)]
               [start-param end-param]))))

    (testing "binds provider id for provider-specific system tables"
      (let [[sql _start-param _end-param provider-param]
            (#'search/find-concepts-between-date-times-sql
             "cmr_groups"
             "PROV'1"
             :access-group
            start-date-time
             end-date-time)
            sql (string/lower-case sql)]
        (is (string/includes? sql "provider_id = ?"))
        (is (= "PROV'1" provider-param))))

    (testing "binds provider id for miscellaneous provider concepts"
      (let [[sql _start-param _end-param provider-param]
            (#'search/find-concepts-between-date-times-sql
             "cmr_variables"
             "PROV1"
             :variable
             start-date-time
             end-date-time)
            sql (string/lower-case sql)]
        (is (string/includes? sql "provider_id = ?"))
        (is (= "PROV1" provider-param))))

    (testing "does not bind provider id for CMR miscellaneous concepts"
      (let [[sql & params] (#'search/find-concepts-between-date-times-sql
                            "cmr_variables"
                            "CMR"
                            :variable
                            start-date-time
                            end-date-time)
            sql (string/lower-case sql)]
        (is (not (string/includes? sql "provider_id =")))
        (is (= 2 (count params)))))))
