(ns cmr.system-int-test.ingest.bulk-update.bulk-update-test
  "CMR bulk update. Test the actual update "
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common-app.test.side-api :as side]
   [cmr.common.time-keeper :as time-keeper]
   [cmr.common.util :as util :refer [are3]]
   [cmr.ingest.config :as ingest-config]
   [cmr.message-queue.test.queue-broker-side-api :as qb-side-api]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       (search/freeze-resume-time-fixture)]))

(def collection-formats
  "Formats to test bulk update"
  [:dif :dif10 :echo10 :iso19115 :iso-smap :umm-json])

(def science-keywords-umm
  {:ScienceKeywords [{:Category "EARTH SCIENCE"
                      :Topic "OCEANS"
                      :Term "MARINE SEDIMENTS"}]})

(def data-centers-umm
  {:DataCenters [{:ShortName "NSID" ;; intentional misspelling for tests
                  :LongName "National Snow and Ice Data Center"
                  :Roles ["ARCHIVER"]
                  :ContactPersons [{:Roles ["Data Center Contact"]
                                    :LastName "Smith"}]}
                 {:ShortName "LPDAAC"
                  :Roles ["PROCESSOR"]
                  :ContactPersons [{:Roles ["Data Center Contact"]
                                    :LastName "Smith"}]}]})

(def find-replace-keywords-umm
  {:ScienceKeywords [{:Category "EARTH SCIENCE"
                      :Topic "ATMOSPHERE"
                      :Term "AIR QUALITY"
                      :VariableLevel1 "CARBON MONOXIDE"}
                     {:Category "EARTH SCIENCE"
                      :Topic "ATMOSPHERE"
                      :Term "CLOUDS"
                      :VariableLevel1 "CLOUD MICROPHYSICS"
                      :VariableLevel2 "CLOUD LIQUID WATER/ICE"}]})

(defn- generate-concept-id
  [index provider]
  (format "C120000000%s-%s" index provider))

(defn- ingest-collection-in-each-format
  "Ingest a collection in each format and return a list of concept-ids"
  [attribs]
  (for [x (range (count collection-formats))
        :let [format (nth collection-formats x)
              collection (data-umm-c/collection-concept
                          (data-umm-c/collection x attribs)
                          format)]]
    (:concept-id (ingest/ingest-concept
                  (assoc collection :concept-id (generate-concept-id x "PROV1"))))))

(deftest bulk-update-science-keywords
  ;; CMR-4334 - bulk update failures when using Oracle as the backend
  (s/only-with-in-memory-database
    ;; Ingest a collection in each format with science keywords to update
    (let [concept-ids (ingest-collection-in-each-format science-keywords-umm)
          _ (index/wait-until-indexed)
          bulk-update-body {:concept-ids concept-ids
                            :update-type "ADD_TO_EXISTING"
                            :update-field "SCIENCE_KEYWORDS"
                            :update-value {:Category "EARTH SCIENCE"
                                           :Topic "HUMAN DIMENSIONS"
                                           :Term "ENVIRONMENTAL IMPACTS"
                                           :VariableLevel1 "HEAVY METALS CONCENTRATION"}}]
         (side/eval-form `(ingest-config/set-bulk-update-enabled! false))
         ;; Kick off bulk update
         (let [response (ingest/bulk-update-collections "PROV1" bulk-update-body)]
           (is (= 400 (:status response)))
           (is (= ["Bulk update is disabled."] (:errors response))))
         ;; Wait for queueing/indexing to catch up
         (index/wait-until-indexed)
         (let [collection-response (ingest/bulk-update-task-status "PROV1" 1)]
           (is (= 404 (:status collection-response)))
           (is (= ["Bulk update task with task id [1] could not be found."]
                  (:errors collection-response))))

         (side/eval-form `(ingest-config/set-bulk-update-enabled! true))
         ;; Kick off bulk update
         (let [response (ingest/bulk-update-collections "PROV1" bulk-update-body)]
           (is (= 200 (:status response)))
           (is (= 1 (:task-id response))))
         ;; Wait for queueing/indexing to catch up
         (index/wait-until-indexed)
         (let [collection-response (ingest/bulk-update-task-status "PROV1" 1)]
           (is (= "COMPLETE" (:task-status collection-response))))

         ;; Check that each concept was updated
         (doseq [concept-id concept-ids
                 :let [concept (-> (search/find-concepts-umm-json :collection
                                                                  {:concept-id concept-id})
                                   :results
                                   :items
                                   first)]]
          (is (= 2
                 (:revision-id (:meta concept))))
          (is (= "2017-01-01T00:00:00Z"
                 (:revision-date (:meta concept))))
          (some #(= {:Date "2017-01-01T00:00:00Z" :Type "UPDATE"} %) (:MetadataDates (:umm concept)))
          (is (= "application/vnd.nasa.cmr.umm+json"
                 (:format (:meta concept))))
          (is (= (:ScienceKeywords (:umm concept))
                 [{:Category "EARTH SCIENCE"
                   :Term "MARINE SEDIMENTS"
                   :Topic "OCEANS"}
                  {:VariableLevel1 "HEAVY METALS CONCENTRATION"
                   :Category "EARTH SCIENCE"
                   :Term "ENVIRONMENTAL IMPACTS"
                   :Topic "HUMAN DIMENSIONS"}]))))))

(deftest data-center-bulk-update
  ;; CMR-4334 - bulk update failures when using Oracle as the backend
  (s/only-with-in-memory-database
    (let [concept-ids (ingest-collection-in-each-format data-centers-umm)
          _ (index/wait-until-indexed)]
      (testing "Invalid data center update"
        (let [bulk-update-body {:concept-ids concept-ids
                                :update-type "ADD_TO_EXISTING"
                                :update-field "DATA_CENTERS"
                                :update-value {:ShortName "LARC"}}]
          (ingest/bulk-update-collections "PROV1" bulk-update-body)
          (index/wait-until-indexed)
          (let [collection-response (ingest/bulk-update-task-status "PROV1" 1)]
            (is (= "COMPLETE" (:task-status collection-response)))
            ;; These error messages all being the same are contingent on the
            ;; bulk update saving umm-json. If that changes these have to change.
            (is (every? #(and (= "FAILED" (:status %))
                              (= "/DataCenters/2 object has missing required properties ([\"Roles\"])"
                                 (:status-message %)))
                        (:collection-statuses collection-response))))))

      (testing "Data center find and update"
        (let [bulk-update-body {:concept-ids concept-ids
                                :update-type "FIND_AND_UPDATE"
                                :update-field "DATA_CENTERS"
                                :find-value {:ShortName "NSID"}
                                :update-value {:ShortName "NSIDC"
                                               :Roles ["ORIGINATOR"]}}]
          (ingest/bulk-update-collections "PROV1" bulk-update-body)
          (index/wait-until-indexed)
          (let [collection-response (ingest/bulk-update-task-status "PROV1" 1)]
            (is (= "COMPLETE" (:task-status collection-response))))

          ;; Check that each concept was updated
          (doseq [concept-id concept-ids
                  :let [concept (-> (search/find-concepts-umm-json :collection
                                                                   {:concept-id concept-id})
                                    :results
                                    :items
                                    first)]]
           ;; On rev 2, not 3, since previous update failed
           (is (= 2
                  (:revision-id (:meta concept))))
           (is (= [{:ShortName "NSIDC"
                    :Roles ["ORIGINATOR"]}
                   {:ShortName "LPDAAC"
                    :Roles ["PROCESSOR"]}]
                  (map #(select-keys % [:Roles :ShortName])
                       (:DataCenters (:umm concept)))))))))))

(deftest bulk-update-replace-test
  ;; CMR-4334 - bulk update failures when using Oracle as the backend
  (s/only-with-in-memory-database
    (let [concept-ids (ingest-collection-in-each-format find-replace-keywords-umm)
          _ (index/wait-until-indexed)
          bulk-update-body {:concept-ids concept-ids
                            :update-type "FIND_AND_REPLACE"
                            :update-field "SCIENCE_KEYWORDS"
                            :find-value {:Topic "ATMOSPHERE"}
                            :update-value {:Category "EARTH SCIENCE"
                                           :Topic "ATMOSPHERE"
                                           :Term "AIR QUALITY"
                                           :VariableLevel1 "EMISSIONS"}}]
         (ingest/bulk-update-collections "PROV1" bulk-update-body)
         (index/wait-until-indexed)
         (let [collection-response (ingest/bulk-update-task-status "PROV1" 1)]
           (is (= "COMPLETE" (:task-status collection-response))))

         ;; Check that each concept was updated
         (doseq [concept-id concept-ids
                 :let [concept (-> (search/find-concepts-umm-json :collection
                                                                  {:concept-id concept-id})
                                   :results
                                   :items
                                   first)]]
          (is (= 2
                 (:revision-id (:meta concept))))
          (is (= "application/vnd.nasa.cmr.umm+json"
                 (:format (:meta concept))))
          (is (= (:ScienceKeywords (:umm concept))
                 [{:Category "EARTH SCIENCE"
                   :Topic "ATMOSPHERE"
                   :Term "AIR QUALITY"
                   :VariableLevel1 "EMISSIONS"}
                  {:Category "EARTH SCIENCE"
                   :Topic "ATMOSPHERE"
                   :Term "AIR QUALITY"
                   :VariableLevel1 "EMISSIONS"}]))))))
