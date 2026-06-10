(ns cmr.bootstrap.test.api.bulk-index-test
  (:require
   [clj-time.core :as time]
   [clojure.test :refer [deftest is testing]]
   [cmr.bootstrap.api.bulk-index :as bulk-index]
   [cmr.bootstrap.api.util :as api-util]
   [cmr.bootstrap.config :as bootstrap-config]
   [cmr.bootstrap.services.bootstrap-service :as service]
   [cmr.common.time-keeper :as time-keeper]))

(defn- service-error
  [f]
  (try
    (f)
    (catch clojure.lang.ExceptionInfo e
      (ex-data e))))

(deftest data-between-date-time-delegates-to-between-service
  (let [calls (atom [])
        context {:system :system}
        start (time/date-time 2026 5 13 1 0)
        end (time/date-time 2026 5 13 3 0)]
    (with-redefs [api-util/get-dispatcher (fn [& args]
                                            (swap! calls conj [:dispatcher args])
                                            :dispatcher)
                  service/index-data-between-date-time
                  (fn [& args]
                    (swap! calls conj [:service args])
                    {:message "indexed"})]
      (let [{:keys [status]} (bulk-index/data-between-date-time
                              context
                              {"provider_ids" ["PROV1"]}
                              {:start_date_time "2026-05-13T01:00:00Z"
                               :end_date_time "2026-05-13T03:00:00Z"})]
        (is (= 202 status))
        (is (= [[:dispatcher [context
                              {:start_date_time "2026-05-13T01:00:00Z"
                               :end_date_time "2026-05-13T03:00:00Z"}
                              :index-data-between-date-time]]
                [:service [context :dispatcher ["PROV1"] start end]]]
               @calls))))))

(deftest data-between-date-time-supports-hours-and-default-end-of-day
  (testing "hours creates the end date-time from the start date-time"
    (let [service-call (atom nil)
          context {:system :system}]
      (with-redefs [api-util/get-dispatcher (constantly :dispatcher)
                    service/index-data-between-date-time
                    (fn [& args]
                      (reset! service-call args)
                      {:message "indexed"})]
        (bulk-index/data-between-date-time
         context
         {}
         {:start_date_time "2026-05-13T01:00:00Z"
          :hours "2"})
        (is (= [context
                :dispatcher
                nil
                (time/date-time 2026 5 13 1 0)
                (time/date-time 2026 5 13 3 0)]
               @service-call)))))

  (testing "missing end date-time defaults to the start date's end-of-day"
    (let [service-call (atom nil)
          context {:system :system}]
      (with-redefs [api-util/get-dispatcher (constantly :dispatcher)
                    service/index-data-between-date-time
                    (fn [& args]
                      (reset! service-call args)
                      {:message "indexed"})]
        (bulk-index/data-between-date-time
         context
         {}
         {:start_date_time "2026-05-13T01:00:00Z"})
        (is (= [context
                :dispatcher
                nil
                (time/date-time 2026 5 13 1 0)
                (time/date-time 2026 5 14 0 0)]
               @service-call))))))

(deftest data-between-date-time-validates-request
  (let [context {:system :system}]
    (with-redefs [api-util/get-dispatcher (constantly :dispatcher)]
      (testing "start date-time is required"
        (is (= {:type :invalid-data
                :errors ["The parameters [:start_date_time] are required"]}
               (service-error
                #(bulk-index/data-between-date-time context {} {})))))

      (testing "only one end selector may be provided"
        (is (= {:type :invalid-data
                :errors ["Only one of end_date_time or hours may be provided."]}
               (service-error
                #(bulk-index/data-between-date-time
                  context
                  {}
                  {:start_date_time "2026-05-13T01:00:00Z"
                   :end_date_time "2026-05-13T03:00:00Z"
                   :hours "1"})))))

      (testing "hours must be positive"
        (is (= {:type :invalid-data
                :errors ["The hours parameter must be a positive integer."]}
               (service-error
                #(bulk-index/data-between-date-time
                  context
                  {}
                  {:start_date_time "2026-05-13T01:00:00Z"
                   :hours "0"})))))

      (testing "end date-time must be after start date-time"
        (is (= {:type :invalid-data
                :errors ["The end date-time must be after the start date-time."]}
               (service-error
                #(bulk-index/data-between-date-time
                  context
                  {}
                  {:start_date_time "2026-05-13T03:00:00Z"
                   :end_date_time "2026-05-13T01:00:00Z"}))))))))

(deftest data-between-date-time-validates-date-time-strings
  (let [context {:system :system}]
    (with-redefs [api-util/get-dispatcher (constantly :dispatcher)]
      (testing "start date-time must parse"
        (is (= {:type :invalid-data
                :errors ["bad-start is not a valid date-time."]}
               (service-error
                #(bulk-index/data-between-date-time
                  context
                  {}
                  {:start_date_time "bad-start"
                   :end_date_time "2026-05-13T01:00:00Z"})))))

      (testing "end date-time must parse"
        (is (= {:type :invalid-data
                :errors ["bad-end is not a valid date-time."]}
               (service-error
                #(bulk-index/data-between-date-time
                  context
                  {}
                  {:start_date_time "2026-05-13T01:00:00Z"
                   :end_date_time "bad-end"})))))

      (testing "hours must be numeric"
        (is (= {:type :invalid-data
                :errors ["The hours parameter must be a positive integer."]}
               (service-error
                #(bulk-index/data-between-date-time
                  context
                  {}
                  {:start_date_time "2026-05-13T01:00:00Z"
                   :hours "two"}))))))))

(deftest data-between-date-time-returns-sync-message
  (let [context {:system :system}]
    (with-redefs [api-util/get-dispatcher (constantly :dispatcher)
                  service/index-data-between-date-time
                  (constantly {:message "Indexed 2 provider concepts and 1 system concepts."})]
      (is (= {:status 202
              :body {:message "Indexed 2 provider concepts and 1 system concepts."}}
             (bulk-index/data-between-date-time
              context
              {}
              {:start_date_time "2026-05-13T01:00:00Z"
               :end_date_time "2026-05-13T02:00:00Z"
               :synchronous "true"}))))))

(deftest data-later-than-date-time-delegates-to-bounded-between-service
  (let [service-call (atom nil)
        context {:system :system}
        now (time/date-time 2026 5 13 3 0)]
    (with-redefs [api-util/get-dispatcher (constantly :dispatcher)
                  time-keeper/now (constantly now)
                  bootstrap-config/bulk-index-after-date-time-max-window-hours (constantly 3)
                  service/index-data-between-date-time
                  (fn [& args]
                    (reset! service-call args)
                    {:message "indexed"})]
      (let [{:keys [status]} (bulk-index/data-later-than-date-time
                              context
                              {"provider_ids" ["PROV1"]}
                              {:date_time "2026-05-13T01:00:00Z"})]
        (is (= 202 status))
        (is (= [context
                :dispatcher
                ["PROV1"]
                (time/date-time 2026 5 13 1 0)
                now]
               @service-call))))))

(deftest data-later-than-date-time-rejects-large-window
  (let [context {:system :system}]
    (with-redefs [api-util/get-dispatcher (constantly :dispatcher)
                  time-keeper/now (constantly (time/date-time 2026 5 13 5 0))
                  bootstrap-config/bulk-index-after-date-time-max-window-hours (constantly 3)]
      (is (= {:type :invalid-data
              :errors [(str "The requested time window exceeds the /bulk_index/after_date_time limit of 3 hours. "
                            "Please use a smaller date_time value so the range to now is within 3 hours.")]}
             (service-error
              #(bulk-index/data-later-than-date-time
                context
                {}
                {:date_time "2026-05-13T01:00:00Z"})))))))

(deftest data-later-than-date-time-validates-range
  (let [context {:system :system}]
    (with-redefs [api-util/get-dispatcher (constantly :dispatcher)
                  time-keeper/now (constantly (time/date-time 2026 5 13 1 0))
                  bootstrap-config/bulk-index-after-date-time-max-window-hours (constantly 3)]
      (testing "date-time must parse"
        (is (= {:type :invalid-data
                :errors ["bad-date is not a valid date-time."]}
               (service-error
                #(bulk-index/data-later-than-date-time
                  context
                  {}
                  {:date_time "bad-date"})))))

      (testing "date-time must be before now"
        (is (= {:type :invalid-data
                :errors ["The end date-time must be after the start date-time."]}
               (service-error
                #(bulk-index/data-later-than-date-time
                  context
                  {}
                  {:date_time "2026-05-13T01:00:00Z"}))))))))
