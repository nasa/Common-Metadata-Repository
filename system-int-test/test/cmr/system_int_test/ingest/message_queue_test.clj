(ns cmr.system-int-test.ingest.message-queue-test
  "Tests behavior of ingest and indexer under different message queue failure scenarios."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.index-util :as index-util]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       (index-util/reset-message-queue-behavior-fixture)]))

(defn ingest-coll
  "Ingests the collection."
  [coll]
  (d/ingest "PROV1" coll))

(defn make-coll
  "Creates and ingests a collection using the unique number given."
  [n]
  (ingest-coll (dc/collection {:concept-id (str "C" n "-PROV1")
                               :entry-title (str "ET" n)
                               :native-id (str "ET" n)})))

(defn delete-coll
  "Creates a tombstone for a collection created with the unique number given."
  [n]
  (ingest/delete-concept (assoc (dc/collection {:concept-id (str "C" n "-PROV1")
                                                :entry-title (str "ET" n)})
                                               :native-id (str "ET" n)
                                               :provider-id "PROV1"
                                               :concept-type :collection)))

(defn ingest-gran
  "Ingests the granule."
  [granule]
  (d/ingest "PROV1" granule))

(defn make-gran
  "Creates and ingests a granule using the unique number given."
  [coll n]
  (ingest-gran (dg/granule coll {:granule-ur (str "GR" n)
                                 :native-id (str "GR" n)
                                 :concept-id (str "G" n "-PROV1")})))

(defn delete-gran
  "Creates a tombstone for granule created with the unique number given."
  [coll n]
  (ingest/delete-concept (assoc (dg/granule coll {:granule-ur (str "GR" n)
                                                  :concept-id (str "G" n "-PROV1")})
                                            :native-id (str "GR" n)
                                            :provider-id "PROV1"
                                            :concept-type :granule)))

(deftest message-queue-concept-history-test
  (s/only-with-real-message-queue
    (let [coll1-1 (make-coll 1)
          coll2-1 (make-coll 2)
          coll3-1 (make-coll 3)
          coll2-2 (make-coll 2)
          coll2-3 (make-coll 2)
          gran1-1 (make-gran coll1-1 1)
          delete-granule-response (delete-gran coll1-1 1)
          delete-collection-response (delete-coll 2)]
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
               (index-util/get-concept-message-queue-history)))))))

(deftest message-queue-retry-test
  (s/only-with-real-message-queue
    (testing "Initial index fails and retries once, completes successfully on retry"
      (index-util/set-message-queue-retry-behavior 1)
      (let [collection (make-coll 1)
            granule (make-gran collection 1)]
        ;; Verify the collection and granule are in Oracle - metadata-db find concepts
        (is (ingest/concept-exists-in-mdb? (:concept-id collection) (:revision-id collection)))
        (is (ingest/concept-exists-in-mdb? (:concept-id granule) (:revision-id granule)))
        (index-util/wait-until-indexed)
        ;; Verify the collection and granule are indexed - search returns correct results
        (are [search concept-type expected]
             (d/refs-match? expected (search/find-refs concept-type search))
             (select-keys collection [:concept-id]) :collection [collection]
             (select-keys granule [:concept-id]) :granule [granule])

        ;; Verify retried exactly one time - (need to manually verify the correct retry interval)
        (is (= {[(:concept-id granule) (:revision-id granule)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "success"}],
                [(:concept-id collection) (:revision-id collection)]
                [{:action "enqueue", :result "initial"}
                 {:action "process", :result "retry"}
                 {:action "process", :result "success"}]}
               (index-util/get-concept-message-queue-history)))))))

(deftest message-queue-failure-test
  (s/only-with-real-message-queue
    (testing "Indexing attempts fail with retryable error and eventually all retries are exhausted"
      (index-util/set-message-queue-retry-behavior 6)
      (let [collection (make-coll 1)
            granule (make-gran collection 1)]
        ;; Verify the collection and granule are in Oracle - metadata-db find concepts
        (is (ingest/concept-exists-in-mdb? (:concept-id collection) (:revision-id collection)))
        (is (ingest/concept-exists-in-mdb? (:concept-id granule) (:revision-id granule)))
        (index-util/wait-until-indexed)
        ;; Verify the collection and granule are not indexed
        (are [search concept-type expected]
             (d/refs-match? expected (search/find-refs concept-type search))
             (select-keys collection [:concept-id]) :collection []
             (select-keys granule [:concept-id]) :granule [])

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
               (index-util/get-concept-message-queue-history)))))))

(deftest publish-messages-failure-test
  (s/only-with-real-message-queue
    (testing "Timeouts on putting messages on message queue return 503"
      (index-util/set-message-queue-publish-timeout 0)
      (let [ingest-result (make-coll 1)]
        (is (= 503 (:status ingest-result)))
        (is (= [(str "Request timed out when attempting to publish message: {:action "
                     ":index-concept, :concept-id \"C1-PROV1\", :revision-id 1}")]
               (:errors ingest-result))))
      ;; Verify the collection is in Oracle
      (is (ingest/concept-exists-in-mdb? "C1-PROV1" 1)))))

(deftest message-queue-fallback-to-http-test
  (s/only-with-real-message-queue
    (testing "Fallback to indexing using http if enqueueing a message fails"
      (index-util/set-message-queue-publish-timeout 0)
      (index-util/turn-on-http-fallback)
      (let [collection (make-coll 1)
            granule (make-gran collection 1)]
        (cmr.common.dev.capture-reveal/capture collection)
        (cmr.common.dev.capture-reveal/capture granule)

        ;; Verify the collection and granule are in Oracle - metadata-db find concepts
        (is (ingest/concept-exists-in-mdb? (:concept-id collection) (:revision-id collection)))
        (is (ingest/concept-exists-in-mdb? (:concept-id granule) (:revision-id granule)))

        (index-util/wait-until-indexed)
        ;; Verify the collection and granule are indexed - search returns correct results
        (are [search concept-type expected]
             (d/refs-match? expected (search/find-refs concept-type search))
             (select-keys collection [:concept-id]) :collection [collection]
             (select-keys granule [:concept-id]) :granule [granule])

        ;; Verify the message queue did not process the messages
        (is (nil? (index-util/get-concept-message-queue-history)))
        (testing "Concepts will be deleted via http if enqueuing a message fails"
          (let [delete-granule (delete-gran collection 1)
                delete-collection (delete-coll 1)]
            (is (= 200 (:status delete-collection)))
            (is (= 200 (:status delete-granule)))

            (index-util/wait-until-indexed)

            (are [search concept-type expected]
                 (d/refs-match? expected (search/find-refs concept-type search))
                 (select-keys collection [:concept-id]) :collection []
                 (select-keys granule [:concept-id]) :granule [])
            ;; Verify the message queue did not process the delete messages
            (is (nil? (index-util/get-concept-message-queue-history)))))))))

(comment
  (cmr.demos.helpers/curl "-H 'Cmr-pretty:true' http://localhost:3003/concepts/C1-PROV1")
  (cmr.demos.helpers/curl "http://localhost:3003/concepts/G1-PROV1"))

(comment

  ;; Rabbit MQ Manual Tests

  ;; Pre-req to running any of the tests is to do the following:

  ;; 1.) Bring up the RabbitMQ VM
  ;; 2.) Configure dev-system to use external message queue
  ;; 3.) vagrant ssh to RabbitMQ VM
  ;; 4.) Set the timeout interval for queueing messages to 10 seconds
  (cmr.ingest.config/set-publish-queue-timeout-ms! 10000)
  ;; 5.) Create a provider and give everyone permissions to ingest for that provider
  (ingest/create-provider "provguid1" "PROV1")
  (cmr.mock-echo.client.echo-util/grant-all-ingest (s/context) "PROV1")

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


