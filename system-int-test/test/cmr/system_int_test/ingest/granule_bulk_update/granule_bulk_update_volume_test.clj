(ns cmr.system-int-test.ingest.granule-bulk-update.granule-bulk-update-volume-test
  "CMR granule bulk update volume integration tests"
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.message-queue.test.queue-broker-side-api :as qb-side-api]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.granule :as granule]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def base-request {:name "large update request"
                   :operation "UPDATE_FIELD"
                   :update-field "OPeNDAPLink"
                   :updates []})

(defn update-instruction
  "Generate an update instruction."
  [ur]
  [ur (str "https://file.example.nasa.gov/" ur)])

;; This test is potentially unstable on systems with smaller amounts of
;; memory and can multiple minutes to complete. Test with caution

(deftest ^:oracle bulk-granule-update-volume-test
  (system/only-with-real-database
   (let [coll1 (data-core/ingest-umm-spec-collection
                "PROV1" (data-umm-c/collection {:EntryTitle "coll1"
                                                :ShortName "short1"
                                                :Version "V1"
                                                :native-id "native1"}))

         bulk-update-options {:token (echo-util/login (system/context) "user1")
                              :accept-format :json
                              :raw? true}]
     (index/wait-until-indexed)

     (are3 [size]
           (let [grans (repeatedly size
                                   #(ingest/ingest-concept
                                     (data-core/item->concept
                                      (granule/granule-with-umm-spec-collection
                                       coll1
                                       (:concept-id coll1)
                                       {}))))
                 _ (index/wait-until-indexed)

                 request (->> grans
                              (map :concept-id)
                              (map update-instruction)
                              (assoc base-request :updates))

                 {:keys [body status]} (ingest/bulk-update-granules "PROV1"
                                                                    request
                                                                    bulk-update-options)
                 response (json/parse-string body true)]
             (is (= 200 status)))

           "tiny request" 2
           "small request" 1500

           ;; Use external SQS for the following
           ;;; dev-system#> lein start-sqs-sns
           ;;; repl> (reset :db :external :messaging :aws)

           ;; "large request" 10000
           ))))
