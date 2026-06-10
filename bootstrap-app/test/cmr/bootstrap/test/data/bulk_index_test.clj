(ns cmr.bootstrap.test.data.bulk-index-test
  (:require
   [clj-time.core :as time]
   [clojure.test :refer [deftest is testing]]
   [cmr.bootstrap.data.bulk-index :as bulk-index]
   [cmr.bootstrap.embedded-system-helper :as helper]))

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

(deftest index-system-misc-between-does-not-duplicate-misc-types
  (let [calls (atom [])]
    (with-redefs [bulk-index/fetch-and-index-concepts-between-date-times
                  (fn [_system _provider concept-type _start _end]
                    (swap! calls conj concept-type)
                    {:num-indexed 0 :max-revision-date nil})]
      (#'bulk-index/index-system-misc-concepts-between-date-times
       {}
       (time/date-time 2026 5 13 1)
       (time/date-time 2026 5 13 2))
      (is (= (count @calls) (count (distinct @calls)))
          (str "Duplicate concept types indexed: " @calls)))))
