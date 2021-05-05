(ns cmr.system-int-test.ingest.granule-bulk-update.granule-bulk-update-volume-test
  "CMR granule bulk update volume integration tests"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.test :refer :all]
   [clojure.xml :as xml]
   [clojure.zip :as zip]
   [cmr.common.util :as util :refer [are3]]
   [cmr.message-queue.test.queue-broker-side-api :as qb-side-api]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.granule :as granule]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def base-request {:name "large update request"
                   :operation "UPDATE_FIELD"
                   :update-field "OPeNDAPLink"
                   :updates []})

(defn update-instruction
  "Generate an update instruction."
  [ur identifier]
  [ur (str "https://file.example.nasa.gov/" ur "/" identifier)])

(deftest ^:oracle bulk-granule-update-partition-test
  (testing "messages will be partitioned into smaller sizes to allow queueing in SQS"
    (system/only-with-real-database
     (try
       (dev-sys-util/eval-in-dev-sys
        `(cmr.ingest.services.granule-bulk-update-service/set-granule-bulk-update-chunk-size! 2))
       (let [bulk-update-options {:token (echo-util/login (system/context) "user1")
                                  :accept-format :json
                                  :raw? true}
             coll1 (data-core/ingest-umm-spec-collection
                    "PROV1" (data-umm-c/collection {:EntryTitle "coll1"
                                                    :ShortName "short1"
                                                    :Version "V1"
                                                    :native-id "native1"}))
             _ (index/wait-until-indexed)
             gran1 (ingest/ingest-concept
                    (data-core/item->concept
                     (granule/granule-with-umm-spec-collection
                      coll1
                      (:concept-id coll1)
                      {:native-id "gran-native-1"
                       :granule-ur "g1"})))
             gran2 (ingest/ingest-concept
                    (data-core/item->concept
                     (granule/granule-with-umm-spec-collection
                      coll1
                      (:concept-id coll1)
                      {:native-id "gran-native-2"
                       :granule-ur "g2"})))
             gran3 (ingest/ingest-concept
                    (data-core/item->concept
                     (granule/granule-with-umm-spec-collection
                      coll1
                      (:concept-id coll1)
                      {:native-id "gran-native-3"
                       :granule-ur "g3"})))]
         (index/wait-until-indexed)

         (are3
          [urs identifier]
          (let [request (->> urs
                             (map #(update-instruction % identifier))
                             (assoc base-request :updates))
                {:keys [body status]} (ingest/bulk-update-granules
                                       "PROV1"
                                       request
                                       bulk-update-options)
                {:keys [task-id]} (json/parse-string body true)]
            (is (= 200 status))
            (is (number? task-id))

            (index/wait-until-indexed)
            (qb-side-api/wait-for-terminal-states)

            (testing "The job can be marked as complete"
              (ingest/update-granule-bulk-update-task-statuses)
              (is (= "COMPLETE" (:task-status (ingest/granule-bulk-update-task-status task-id)))))

            (testing "The data is reflected in the updated values in search"
              (index/refresh-elastic-index)
              (let [next-granules (-> (search/find-concepts-umm-json :granule {:granule-ur urs})
                                      :body
                                      (json/parse-string true)
                                      :items)
                    next-urls (->> next-granules
                                   (map :umm)
                                   (map :RelatedUrls)
                                   (map first)
                                   (map :URL))]
                (is (= (map #(second (update-instruction % identifier)) urs)
                       next-urls)))))

          "unsplit request"
          ["g1"] "unsplit"

          "split request"
          ["g1" "g2" "g3"] "split"))
       (finally (dev-sys-util/eval-in-dev-sys
                 `(cmr.ingest.services.granule-bulk-update-service/set-granule-bulk-update-chunk-size! 100)))))))
