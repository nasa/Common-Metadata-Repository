(ns cmr.system-int-test.ingest.message-queue-test
  "Tests behavior of ingest and indexer under different message queue failure scenarios."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.metadata-db-util :as mdb]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.index-util :as index-util]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.indexer.config :as indexer-config]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       (index-util/reset-message-queue-behavior-fixture)]))

(defn- ingest-coll
  "Ingests the collection."
  [coll]
  (d/ingest "PROV1" coll))

(defn- make-coll
  "Creates and ingests a collection using the unique number given."
  [n]
  (ingest-coll (dc/collection {:concept-id (str "C" n "-PROV1")
                               :entry-title (str "ET" n)
                               :native-id (str "ET" n)})))

(defn- delete-coll
  "Creates a tombstone for the given collection."
  [collection]
  (ingest/delete-concept (assoc collection
                                :native-id (:entry-title collection)
                                :provider-id "PROV1"
                                :concept-type :collection)))

(defn- ingest-gran
  "Ingests the granule."
  [granule]
  (d/ingest "PROV1" granule))

(defn- make-gran
  "Creates and ingests a granule using the unique number given."
  [coll n]
  (ingest-gran (dg/granule coll {:granule-ur (str "GR" n)
                                 :native-id (str "GR" n)
                                 :concept-id (str "G" n "-PROV1")})))

(defn- delete-gran
  "Creates a tombstone for the given granule."
  [granule]
  (ingest/delete-concept (assoc granule
                                :native-id (:granule-ur granule)
                                :provider-id "PROV1"
                                :concept-type :granule)))

(defn- assert-indexed
  "Verifies that a given concept is found when searching"
  [concept-type concept]
  (is (d/refs-match? [concept]
                     (search/find-refs concept-type (select-keys concept [:concept-id])))))

(defn- assert-not-indexed
  "Verifies that a given concept is not found when searching"
  [concept-type concept]
  (is (zero? (:hits (search/find-refs concept-type (select-keys concept [:concept-id]))))))

(defn- assert-in-metadata-db
  "Verifies that all of the provided concepts are stored in metadata-db"
  [& concepts]
  (doseq [concept concepts]
    (is (mdb/concept-exists-in-mdb? (:concept-id concept) (:revision-id concept)))))

(deftest message-queue-concept-history-test
  (s/only-with-real-message-queue
    (let [coll1-1 (make-coll 1)
          coll2-1 (make-coll 2)
          coll3-1 (make-coll 3)
          coll2-2 (make-coll 2)
          coll2-3 (make-coll 2)
          gran1-1 (make-gran coll1-1 1)
          delete-granule-response (delete-gran gran1-1)
          delete-collection-response (delete-coll coll2-1)]
      (is (= 200 (:status delete-granule-response) (:status delete-collection-response)))
      (index-util/wait-until-indexed)
      (testing "Successfully processed index and delete concept messages"
        (is (= {["C2-PROV1" 4]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "success"}],
                ["G1-PROV1" 2]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "success"}],
                ["G1-PROV1" 1]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "success"}],
                ["C2-PROV1" 3]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "success"}],
                ["C2-PROV1" 2]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "success"}],
                ["C3-PROV1" 1]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "success"}],
                ["C2-PROV1" 1]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "success"}],
                ["C1-PROV1" 1]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "success"}]}
               (index-util/get-concept-message-queue-history (indexer-config/index-queue-name))))))))

(deftest message-queue-retry-test
  (s/only-with-real-message-queue
    (testing "Initial index fails and retries once, completes successfully on retry"
      (index-util/set-message-queue-retry-behavior 1)
      (let [collection (make-coll 1)
            granule (make-gran collection 1)]
        (assert-in-metadata-db collection granule)
        (index-util/wait-until-indexed)
        (assert-indexed :collection collection)
        (assert-indexed :granule granule)

        ;; Verify retried exactly one time - (need to manually verify the correct retry interval)
        (is (= {[(:concept-id granule) (:revision-id granule)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "success"}],
                [(:concept-id collection) (:revision-id collection)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "success"}]}
               (index-util/get-concept-message-queue-history (indexer-config/index-queue-name))))))))

(deftest message-queue-failure-test
  (s/only-with-real-message-queue
    (testing "Indexing attempts fail with retryable error and eventually all retries are exhausted"
      (index-util/set-message-queue-retry-behavior 6)
      (let [collection (make-coll 1)
            granule (make-gran collection 1)]
        (assert-in-metadata-db collection granule)
        (index-util/wait-until-indexed)
        (assert-not-indexed :collection collection)
        (assert-not-indexed :granule granule)

        ;; Verify retried five times and then marked as a failure
        (is (= {[(:concept-id granule) (:revision-id granule)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "failure"}],
                [(:concept-id collection) (:revision-id collection)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "failure"}]}
               (index-util/get-concept-message-queue-history (indexer-config/index-queue-name))))))))

;; This test isn't reliable as sometimes the item is able to be queued
;; See CMR-1717
#_(deftest publish-messages-failure-test
  (s/only-with-real-message-queue
    (testing "When unable to publish a message on the queue the ingest fails."
      (testing "Update concept"
        (index-util/set-message-queue-publish-timeout 0)
        (let [collection (d/ingest "PROV1" (dc/collection) {:allow-failure? true})]
          ;; Verify ingest received a failed status code
          (is (= 503 (:status collection)))
          (index-util/wait-until-indexed)
          ;; Verify the message queue did not receive the message
          (is (nil? (index-util/get-concept-message-queue-history (indexer-config/index-queue-name))))))

      (testing "Delete concept"
        (index-util/set-message-queue-publish-timeout 10000)
        (let [collection (d/ingest "PROV1" (dc/collection))
              _ (index-util/set-message-queue-publish-timeout 0)
              response (ingest/delete-concept (d/item->concept collection))]
          ;; Verify ingest received a failed status code
          (is (= 503 (:status response)))
          (index-util/wait-until-indexed)
          ;; Verify the message queue did not receive the delete message
          (is (= {[(:concept-id collection) 1]
                  [{:action "enqueue", :result "initial"}
                   {:action "process", :result "success"}]}
                 (index-util/get-concept-message-queue-history (indexer-config/index-queue-name)))))))))

(comment

  ;; Rabbit MQ Manual Tests

  ;; Pre-req to running any of the tests is to do the following:

  ;; 1.) Bring up the RabbitMQ VM
  ;; 2.) Configure dev-system to use external message queue
  ;; 3.) vagrant ssh to RabbitMQ VM
  ;; 4.) Create a provider and give everyone permissions to ingest for that provider
  (cmr.system-int-test.utils.dev-system-util/reset)
  (ingest/create-provider "provguid1" "PROV1")

  ;; Memory Threshold Exceeded while queueing messages

  ;; 1.) sudo vi /etc/rabbitmq/rabbitmq.config.insufficient_memory
  ;; The contents of the file should be the following:
  ;; [{rabbit, [{vm_memory_high_watermark, 0.06}, {cluster_nodes, {['rabbit@rabbit1', 'rabbit@rabbit2', 'rabbit@rabbit3'], disc}}]}].
  ;; 2.) Backup the original configuration file:
  ;;      sudo cp /etc/rabbitmq/rabbitmq.config /etc/rabbitmq/rabbitmq.config.orig
  ;; 3.) Switch the config files
  ;;      sudo cp /etc/rabbitmq/rabbitmq.config.insufficient_memory /etc/rabbitmq/rabbitmq.config
  ;; 4.) Restart RabbitMQ
  ;;      sudo service rabbitmq-server restart
  ;; 5.) Attempt to ingest the collection
  (cmr.demos.helpers/curl "-XPUT -H 'Content-Type:application/echo10+xml' http://localhost:3002/providers/PROV1/collections/example_coll -d"
                          "<Collection>
                          <ShortName>ShortName_Larc</ShortName>
                          <VersionId>Version01</VersionId>
                          <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
                          <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
                          <DeleteTime>2015-05-23T22:30:59</DeleteTime>
                          <LongName>LarcLongName</LongName>
                          <DataSetId>LarcDatasetId</DataSetId>
                          <Description>A minimal valid collection</Description>
                          <Orderable>true</Orderable>
                          <Visible>true</Visible>
                          </Collection>")
  ;; 6.) Verify that after 10 seconds a 503 is returned with an error message indicating a timeout
  ;; 7.) Switch the config file back
  ;;       sudo cp /etc/rabbitmq/rabbitmq.config /etc/rabbitmq/rabbitmq.config.orig
  ;; 8.) Restart RabbitMQ
  ;;       sudo service rabbitmq-server restart
  ;; 9.) Verify you can ingest the collection above

  ;; Rabbit MQ Down while trying to queue a message

  ;; 1.) Bring down RabbitMQ
  ;;      sudo service rabbitmq-server stop
  ;; 2.) Attempt to ingest the collection
  (cmr.demos.helpers/curl "-XPUT -H 'Content-Type:application/echo10+xml' http://localhost:3002/providers/PROV1/collections/example_coll -d"
                          "<Collection>
                          <ShortName>ShortName_Larc</ShortName>
                          <VersionId>Version01</VersionId>
                          <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
                          <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
                          <DeleteTime>2015-05-23T22:30:59</DeleteTime>
                          <LongName>LarcLongName</LongName>
                          <DataSetId>LarcDatasetId</DataSetId>
                          <Description>A minimal valid collection</Description>
                          <Orderable>true</Orderable>
                          <Visible>true</Visible>
                          </Collection>")
  ;; 3.) Verify that after 10 seconds a 503 is returned with an error message indicating it was
  ;; unable to queue a message
  ;; 4.) Bring RabbitMQ back up
  ;;      sudo service rabbitmq-server start

  ;; Messages retrying while RabbitMQ is restarted

  ;; 1.) Force messages to take longer than normal:
  ;; Add the following line to indexer-app/src/cmr/indexer/services/index_service.clj to the
  ;; index-concept function:
  ;;   (Thread/sleep 2000)
  ;; 2.) Force messages to retry 4 times before being processed so that messages are on each of the
  ;; different queues.
  (index-util/set-message-queue-retry-behavior 4)
  ;; 3.) Get a count of the collections in your system before you start:
  (cmr.demos.helpers/curl "http://localhost:3003/collections.xml?page_size=0")
  ;; 4.) Ingest a bunch of collections - note you will want to be ready to restart things quickly
  (doseq [_ (range 150)]
    (ingest/ingest-concept (dc/collection-concept {})))
  ;; 5.) You can monitor the queues at http://localhost:15672/#/queues to see that there are some
  ;; messages on the various queues. You can adjust the sleep time to longer in step #1 if you
  ;; do not see messages on all of the queues.
  ;; 6.) Restart RabbitMQ several times using stop and/or restart:
  ;;      sudo service rabbitmq-server stop
  ;; Wait 30 seconds then run the following several times:
  ;;      sudo service rabbitmq-server restart
  ;; 7.) Wait until everything is indexed
  ;; 8.) Verify that every collection was ingested successfully by adding 150 to the original count.
  (cmr.demos.helpers/curl "http://localhost:3003/collections.xml?page_size=0")

  ;; At the end of the tests make sure to return behavior to normal by running reset
  )


