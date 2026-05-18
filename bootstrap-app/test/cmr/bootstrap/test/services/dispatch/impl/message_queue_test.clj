(ns cmr.bootstrap.test.services.dispatch.impl.message-queue-test
  (:require
   [clj-time.core :as time]
   [clojure.test :refer [deftest is testing]]
   [cmr.bootstrap.config :as config]
   [cmr.bootstrap.data.message-queue :as message-queue]
   [cmr.bootstrap.embedded-system-helper :as helper]
   [cmr.bootstrap.services.dispatch.core :as dispatch]
   [cmr.bootstrap.services.dispatch.impl.message-queue :as message-queue-dispatcher]))

(deftest date-time-chunks-creates-half-open-ranges
  (let [start (time/date-time 2026 5 13 1 0)
        end (time/date-time 2026 5 13 4 30)]
    (is (= [[(time/date-time 2026 5 13 1 0)
             (time/date-time 2026 5 13 2 0)]
            [(time/date-time 2026 5 13 2 0)
             (time/date-time 2026 5 13 3 0)]
            [(time/date-time 2026 5 13 3 0)
             (time/date-time 2026 5 13 4 0)]
            [(time/date-time 2026 5 13 4 0)
             (time/date-time 2026 5 13 4 30)]]
           (#'message-queue-dispatcher/date-time-chunks start end 1)))))

(deftest date-time-chunks-returns-empty-when-range-is-empty
  (let [start (time/date-time 2026 5 13 1 0)]
    (is (= [] (#'message-queue-dispatcher/date-time-chunks start start 1)))))

(deftest bootstrap-provider-between-date-time-event-test
  (let [start (time/date-time 2026 5 13 1 0)
        end (time/date-time 2026 5 13 2 0)]
    (is (= {:action :index-provider-between-date-time
            :provider-id "PROV1"
            :start-date-time start
            :end-date-time end}
           (message-queue/bootstrap-provider-between-date-time-event
            "PROV1"
            start
            end)))))

(deftest index-data-between-date-time-publishes-provider-chunks
  (let [published (atom [])
        context {:system {:providers [{:provider-id "PROV1"}]}}
        start (time/date-time 2026 5 13 1 0)
        end (time/date-time 2026 5 13 3 30)]
    (with-redefs [config/bulk-index-between-date-time-window-hours (constantly 1)
                  message-queue/publish-bootstrap-concepts-event
                  (fn [_context msg]
                    (swap! published conj msg))]
      (dispatch/index-data-between-date-time
       (message-queue-dispatcher/->MessageQueueDispatcher)
       context
       ["PROV1"]
       start
       end)
      (is (= [{:action :index-provider-between-date-time
               :provider-id "PROV1"
               :start-date-time (time/date-time 2026 5 13 1 0)
               :end-date-time (time/date-time 2026 5 13 2 0)}
              {:action :index-provider-between-date-time
               :provider-id "PROV1"
               :start-date-time (time/date-time 2026 5 13 2 0)
               :end-date-time (time/date-time 2026 5 13 3 0)}
              {:action :index-provider-between-date-time
               :provider-id "PROV1"
               :start-date-time (time/date-time 2026 5 13 3 0)
               :end-date-time (time/date-time 2026 5 13 3 30)}]
             @published)))))

(deftest index-data-between-date-time-expands-empty-provider-list
  (let [published (atom [])
        context {:system :system}
        start (time/date-time 2026 5 13 1 0)
        end (time/date-time 2026 5 13 2 0)]
    (with-redefs [helper/get-providers (constantly [{:provider-id "PROV1"}
                                                    {:provider-id "PROV2"}])
                  config/bulk-index-between-date-time-window-hours (constantly 0)
                  message-queue/publish-bootstrap-concepts-event
                  (fn [_context msg]
                    (swap! published conj msg))]
      (testing "empty provider ids uses all providers plus CMR and clamps chunk hours to one"
        (dispatch/index-data-between-date-time
         (message-queue-dispatcher/->MessageQueueDispatcher)
         context
         nil
         start
         end)
        (is (= ["CMR" "PROV1" "PROV2"] (map :provider-id @published)))
        (is (every? #(= start (:start-date-time %)) @published))
        (is (every? #(= end (:end-date-time %)) @published))))))

(deftest handle-bootstrap-event-index-provider-between-date-time
  (let [call (atom nil)]
    (with-redefs [cmr.bootstrap.data.bulk-index/index-provider-data-between-date-time
                  (fn [& args]
                    (reset! call args))]
      (message-queue-dispatcher/handle-bootstrap-event
       {:system :system}
       {:action :index-provider-between-date-time
        :provider-id "PROV1"
        :start-date-time "2026-05-13T01:00:00Z"
        :end-date-time "2026-05-13T02:00:00Z"})
      (is (= [:system "PROV1" "2026-05-13T01:00:00Z" "2026-05-13T02:00:00Z"]
             @call)))))
