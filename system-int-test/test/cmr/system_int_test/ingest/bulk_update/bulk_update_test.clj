(ns cmr.system-int-test.ingest.bulk-update.bulk-update-test
  "CMR bulk update. Test the actual update "
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [clojure.java.io :as io]
   [cmr.common-app.test.side-api :as side]
   [cmr.common.time-keeper :as time-keeper]
   [cmr.common.util :as util :refer [are3]]
   [cmr.ingest.config :as ingest-config]
   [cmr.message-queue.test.queue-broker-side-api :as qb-side-api]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.core :as data2-core]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"})
                       (search/freeze-resume-time-fixture)]))

(def collection-formats
  "Formats to test bulk update"
  [:dif :dif10 :echo10 :iso19115 :iso-smap :umm-json])

(def science-keywords-umm
  {:ScienceKeywords [{:Category "EARTH SCIENCE"
                      :Topic "OCEANS"
                      :Term "MARINE SEDIMENTS"}]})

(def find-and-replace-multiple-science-keywords-umm
  "Used to test the case when update-value contains a list of science keywords."
  {:ScienceKeywords [{:Category "EARTH SCIENCE"
                      :Topic "OCEANS"
                      :Term "MARINE SEDIMENTS"}
                     {:Category "EARTH SCIENCE2"
                      :Topic "HUMAN DIMENSIONS2"
                      :Term "ENVIRONMENTAL IMPACTS2"
                      :VariableLevel1 "HEAVY METALS CONCENTRATION2"}]})

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

(def data-centers-umm-with-home-page-url
  "Defines data center with home page url."
  {:DataCenters [{:ShortName "ShortName"
                  :LongName "Hydrogeophysics Group, Aarhus University "
                  :Roles ["ARCHIVER", "DISTRIBUTOR"]
                  :Uuid "ef941ad9-1662-400d-a24a-c300a72c1531"
                  :ContactInformation {:RelatedUrls [{:URLContentType "DataCenterURL"
                                                      :Type "HOME PAGE"
                                                      :URL "http://nsidc.org/daac/index.html"}]
                                       :ContactMechanisms  [{:Type "Telephone"
                                                             :Value "1 303 492 6199 x"}
                                                            {:Type  "Fax"
                                                             :Value "1 303 492 2468 x"}
                                                            {:Type  "Email"
                                                             :Value "nsidc@nsidc.org"}]}}
                 {:ShortName "ShortName"
                  :LongName "Hydrogeophysics Group, Aarhus University "
                  :Roles ["ARCHIVER"]}
                 {:ShortName "ShortName"
                  :Roles ["PROCESSOR"]
                  :ContactPersons [{:Roles ["Data Center Contact"]
                                    :LastName "Smith"}]
                  :ContactInformation {:RelatedUrls [{:URLContentType "DataCenterURL"
                                                      :Type "HOME PAGE"
                                                      :URL "http://nsidc.org/daac/index.html"}]}}]})

(def find-update-keywords-umm
  {:ScienceKeywords [{:Category "EARTH SCIENCE"
                      :Topic "ATMOSPHERE"
                      :Term "AIR QUALITY"
                      :VariableLevel1 "CARBON MONOXIDE"}
                     {:Category "EARTH SCIENCE"
                      :Topic "ATMOSPHERE"
                      :Term "AIR QUALITY"
                      :VariableLevel1 "CARBON MONOXIDE"}
                     {:Category "EARTH SCIENCE"
                      :Topic "ATMOSPHERE"
                      :Term "CLOUDS"
                      :VariableLevel1 "CLOUD MICROPHYSICS"
                      :VariableLevel2 "CLOUD LIQUID WATER/ICE"}]})

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

(def platforms-instruments-umm
  {:Platforms [{:ShortName "a340-600-1"
                :LongName "airbus a340-600-1"
                :Type "Aircraft"}
               {:ShortName "a340-600-2"
                :LongName "airbus a340-600"
                :Type "Aircraft"
                :Instruments [{:ShortName "atm"
                               :LongName "airborne topographic mapper"
                               :Technique "testing"
                               :NumberOfInstruments 0
                               :OperationalModes ["mode1" "mode2"]}]}
               {:ShortName "a340-600-3"
                :LongName "airbus a340-600"
                :Type "Aircraft"
                :Instruments [{:ShortName "atm"
                               :LongName "airborne topographic mapper"}]}]})

(def large-status-message-umm
  {:RelatedUrls [{:Description "Related url description"
                  :URL "www.foobarbazquxquux.com"
                  :URLContentType "DistributionURL"
                  :Type "GET DATA"
                  :Subtype "Earthdata Search"
                  :GetData {:Format "ascii"
                            :Size 10.0
                            :Unit "MB"
                            :Fees "fees"}}
                 {:Description "Related url description"
                  :URL "www.foobarbazquxquux.com"
                  :URLContentType "DistributionURL"
                  :Type "GET DATA"
                  :Subtype "Earthdata Search"
                  :GetData {:Format "ascii"
                            :Size 10.0
                            :Unit "MB"
                            :Fees "fees"}}
                 {:Description "Related url description"
                  :URL "www.foobarbazquxquux.com"
                  :URLContentType "DistributionURL"
                  :Type "GET DATA"
                  :Subtype "Earthdata Search"
                  :GetData {:Format "ascii"
                            :Size 10.0
                            :Unit "MB"
                            :Fees "fees"}}]})

(defn- generate-concept-id
  [index provider]
  (format "C120000000%s-%s" index provider))

(defn- ingest-collection-in-each-format
  "Ingest a collection in each format and return a list of concept-ids"
  ([attribs]
   (ingest-collection-in-each-format attribs collection-formats))
  ([attribs formats]
   (doall
     (for [x (range (count formats))
           :let [format (nth formats x)
                 collection (data-umm-c/collection-concept
                             (data-umm-c/collection x attribs)
                             format)]]
       (:concept-id (ingest/ingest-concept
                     (assoc collection :concept-id (generate-concept-id x "PROV1"))))))))

(defn- ingest-collection-in-umm-json-format
  "Ingest a collection in UMM Json format and return a list of one concept-id.
   This is used to test the CMR-4517, on complete removal of platforms/instruments.
   Since it's only testing the bulk update part after the ingest, which format to use
   for ingest is irrelevant. So it's easier to just test with one format of ingest."
  [attribs]
  (let [collection (data-umm-c/collection-concept
                     (data-umm-c/collection 1 attribs)
                     :umm-json)]
    [(:concept-id (ingest/ingest-concept
                   (assoc collection :concept-id (generate-concept-id 1 "PROV1"))))]))

(deftest bulk-update-science-keywords
  ;; Ingest a collection in each format with science keywords to update
  (let [concept-ids (ingest-collection-in-each-format science-keywords-umm)
        bulk-update-options1 {:token (e/login (s/context) "user1") :user-id "user2"}
        bulk-update-options2 {:token (e/login (s/context) "user1")}
        bulk-update-options3 {:user-id "user2"}
        bulk-update-body {:concept-ids ["all"]
                          :update-type "ADD_TO_EXISTING"
                          :update-field "SCIENCE_KEYWORDS"
                          :update-value {:Category "EARTH SCIENCE"
                                         :Topic "HUMAN DIMENSIONS"
                                         :Term "ENVIRONMENTAL IMPACTS"
                                         :VariableLevel1 "HEAVY METALS CONCENTRATION"}}
        ;; CMR-4570 tests that no duplicate science keywords are created.
        duplicate-body {:concept-ids ["ALL" ]
                        :update-type "ADD_TO_EXISTING"
                        :update-field "SCIENCE_KEYWORDS"
                        :update-value {:Category "EARTH SCIENCE"
                                       :Term "MARINE SEDIMENTS"
                                       :Topic "OCEANS"}}]

    ;; Initiate bulk update that shouldn't add any duplicates.
    (let [response (ingest/bulk-update-collections "PROV1" duplicate-body)
          _ (index/wait-until-indexed)
          {:keys [task-id]} response
          ;; I am surprised to find out that the task-id returned in the response is a string
          ;; Here I am just blindly patch this test to work with a string.
          next-task-id (+ 1 (Integer/parseInt task-id))
          collection-response (ingest/bulk-update-task-status "PROV1" task-id)]
      (is (= "COMPLETE" (:task-status collection-response)))

      (side/eval-form `(ingest-config/set-collection-bulk-update-enabled! false))
      ;; Kick off bulk update when bulk update is disabled is not allowed
      (let [response (ingest/bulk-update-collections "PROV1" bulk-update-body)]
        (is (= 400 (:status response)))
        (is (= ["Bulk update is disabled."] (:errors response))))
      (let [collection-response (ingest/bulk-update-task-status "PROV1" next-task-id)]
        (is (= 404 (:status collection-response)))
        (is (= [(format "Bulk update task with task id [%s] could not be found for provider id [PROV1]."
                        next-task-id)]
               (:errors collection-response))))
      (let [collection-response (ingest/bulk-update-task-status "PROV1" task-id)]
        (is (= 200 (:status collection-response))))
      (let [collection-response (ingest/bulk-update-task-status "PROV2" task-id)]
        (is (= 404 (:status collection-response)))
        (is (= [(format "Bulk update task with task id [%s] could not be found for provider id [PROV2]."
                        task-id)]
               (:errors collection-response))))
      (side/eval-form `(ingest-config/set-collection-bulk-update-enabled! true)))

    ;; Kick off bulk update
    (let [response (ingest/bulk-update-collections
                     "PROV1" bulk-update-body bulk-update-options1)]
      (is (= 200 (:status response)))
      ;; Wait for queueing/indexing to catch up
      (index/wait-until-indexed)
      ;; Check that each concept was updated
      (doseq [concept-id concept-ids
              :let [concept (-> (search/find-concepts-umm-json :collection
                                                               {:concept-id concept-id})
                                :results
                                :items
                                first)]]
        (is (= 3 (:revision-id (:meta concept))))
        (ingest/assert-user-id concept-id 3 "user2")
        (is (= "2017-01-01T00:00:00.000Z"
               (:revision-date (:meta concept))))
        (some #(= {:Date "2017-01-01T00:00:00Z" :Type "UPDATE"} %) (:MetadataDates (:umm concept)))
        (is (= "application/vnd.nasa.cmr.umm+json"
               (:format (:meta concept))))
        (is (= [{:Category "EARTH SCIENCE"
                 :Term "MARINE SEDIMENTS"
                 :Topic "OCEANS"}
                {:VariableLevel1 "HEAVY METALS CONCENTRATION"
                 :Category "EARTH SCIENCE"
                 :Term "ENVIRONMENTAL IMPACTS"
                 :Topic "HUMAN DIMENSIONS"}]
               (:ScienceKeywords (:umm concept))))))
    (let [response (ingest/bulk-update-collections
                     "PROV1" bulk-update-body bulk-update-options2)]
      (is (= 200 (:status response)))
      ;; Wait for queueing/indexing to catch up
      (index/wait-until-indexed)
      ;; Check that each concept was updated
      (doseq [concept-id concept-ids
              :let [concept (-> (search/find-concepts-umm-json :collection
                                                               {:concept-id concept-id})
                                :results
                                :items
                                first)]]
        (is (= 4 (:revision-id (:meta concept))))
        (ingest/assert-user-id concept-id 4 "user1")))

    (let [response (ingest/bulk-update-collections
                     "PROV1" bulk-update-body bulk-update-options3)]
      (is (= 200 (:status response)))
      ;; Wait for queueing/indexing to catch up
      (index/wait-until-indexed)
      ;; Check that each concept was updated
      (doseq [concept-id concept-ids
              :let [concept (-> (search/find-concepts-umm-json :collection
                                                               {:concept-id concept-id})
                                :results
                                :items
                                first)]]
        (is (= 5 (:revision-id (:meta concept))))
        (ingest/assert-user-id concept-id 5 "user2")))))

(deftest bulk-update-add-to-existing-multiple-science-keywords
  ;; This test is the same as the previous bulk-update-science-keywords test except
  ;; that it shows that update-value could be an array of objects.
  ;; Ingest a collection in each format with science keywords to update
  (let [concept-ids (ingest-collection-in-each-format science-keywords-umm)
        _ (index/wait-until-indexed)
        bulk-update-body {:concept-ids concept-ids
                          :update-type "ADD_TO_EXISTING"
                          :update-field "SCIENCE_KEYWORDS"
                          :update-value [{:Category "EARTH SCIENCE1"
                                          :Topic "HUMAN DIMENSIONS1"
                                          :Term "ENVIRONMENTAL IMPACTS1"
                                          :VariableLevel1 "HEAVY METALS CONCENTRATION1"}
                                         {:Category "EARTH SCIENCE2"
                                          :Topic "HUMAN DIMENSIONS2"
                                          :Term "ENVIRONMENTAL IMPACTS2"
                                          :VariableLevel1 "HEAVY METALS CONCENTRATION2"}]}
        ;; CMR-4570 tests that no duplicate science keywords are created.
        duplicate-body {:concept-ids concept-ids
                        :update-type "ADD_TO_EXISTING"
                        :update-field "SCIENCE_KEYWORDS"
                        :update-value [{:Category "EARTH SCIENCE"
                                        :Term "MARINE SEDIMENTS"
                                        :Topic "OCEANS"}
                                       {:Category "EARTH SCIENCE1"
                                        :Topic "HUMAN DIMENSIONS1"
                                        :Term "ENVIRONMENTAL IMPACTS1"
                                        :VariableLevel1 "HEAVY METALS CONCENTRATION1"}]}]
       ;; Initiate bulk update that shouldn't add any duplicates.
       (ingest/bulk-update-collections "PROV1" duplicate-body)
       ;; Wait for queueing/indexing to catch up
       (index/wait-until-indexed)
       ;; Kick off bulk update
       (let [response (ingest/bulk-update-collections "PROV1" bulk-update-body)]
         (is (= 200 (:status response)))
         ;; Wait for queueing/indexing to catch up
         (index/wait-until-indexed)
         (let [collection-response (ingest/bulk-update-task-status "PROV1" (:task-id response))]
           (is (= "COMPLETE" (:task-status collection-response))))

         ;; Check that each concept was updated
         (doseq [concept-id concept-ids
                 :let [concept (-> (search/find-concepts-umm-json :collection
                                                                  {:concept-id concept-id})
                                   :results
                                   :items
                                   first)]]
          (is (= (:ScienceKeywords (:umm concept))
                 [{:Category "EARTH SCIENCE"
                   :Term "MARINE SEDIMENTS"
                   :Topic "OCEANS"}
                  {:VariableLevel1 "HEAVY METALS CONCENTRATION1"
                   :Category "EARTH SCIENCE1"
                   :Term "ENVIRONMENTAL IMPACTS1"
                   :Topic "HUMAN DIMENSIONS1"}
                  {:VariableLevel1 "HEAVY METALS CONCENTRATION2"
                   :Category "EARTH SCIENCE2"
                   :Term "ENVIRONMENTAL IMPACTS2"
                   :Topic "HUMAN DIMENSIONS2"}]))))))

(deftest bulk-update-clear-all-and-replace-with-multiple-science-keywords
  ;; This test shows that update-value could be an array of objects when doing CLEAR_ALL_AND_REPLACE.
  ;; Ingest a collection in each format with science keywords to update
  (let [concept-ids (ingest-collection-in-each-format science-keywords-umm)
        _ (index/wait-until-indexed)
        bulk-update-body {:concept-ids concept-ids
                          :update-type "CLEAR_ALL_AND_REPLACE"
                          :update-field "SCIENCE_KEYWORDS"
                          :update-value [{:Category "EARTH SCIENCE1"
                                          :Topic "HUMAN DIMENSIONS1"
                                          :Term "ENVIRONMENTAL IMPACTS1"
                                          :VariableLevel1 "HEAVY METALS CONCENTRATION1"}
                                         {:Category "EARTH SCIENCE2"
                                          :Topic "HUMAN DIMENSIONS2"
                                          :Term "ENVIRONMENTAL IMPACTS2"
                                          :VariableLevel1 "HEAVY METALS CONCENTRATION2"}
                                         {:Category "EARTH SCIENCE1"
                                          :Topic "HUMAN DIMENSIONS1"
                                          :Term "ENVIRONMENTAL IMPACTS1"
                                          :VariableLevel1 "HEAVY METALS CONCENTRATION1"}]}]
       ;; Kick off bulk update
       (let [response (ingest/bulk-update-collections "PROV1" bulk-update-body)]
         (is (= 200 (:status response)))
         ;; Wait for queueing/indexing to catch up
         (index/wait-until-indexed)
         (let [collection-response (ingest/bulk-update-task-status "PROV1" (:task-id response))]
           (is (= "COMPLETE" (:task-status collection-response))))

         ;; Check that each concept was updated
         (doseq [concept-id concept-ids
                 :let [concept (-> (search/find-concepts-umm-json :collection
                                                                  {:concept-id concept-id})
                                   :results
                                   :items
                                   first)]]
          (is (= (:ScienceKeywords (:umm concept))
                 [{:VariableLevel1 "HEAVY METALS CONCENTRATION1"
                   :Category "EARTH SCIENCE1"
                   :Term "ENVIRONMENTAL IMPACTS1"
                   :Topic "HUMAN DIMENSIONS1"}
                  {:VariableLevel1 "HEAVY METALS CONCENTRATION2"
                   :Category "EARTH SCIENCE2"
                   :Term "ENVIRONMENTAL IMPACTS2"
                   :Topic "HUMAN DIMENSIONS2"}]))))))

(deftest bulk-update-find-and-replace-with-multiple-science-keywords
  ;; This test shows that update-value could be an array of objects when doing FIND_AND_REPLACE.
  ;; Ingest a collection in each format with science keywords to update
  (let [concept-ids (ingest-collection-in-each-format find-and-replace-multiple-science-keywords-umm)
        _ (index/wait-until-indexed)
        bulk-update-body {:concept-ids concept-ids
                          :update-type "FIND_AND_REPLACE"
                          :find-value {:Topic "OCEANS"}
                          :update-field "SCIENCE_KEYWORDS"
                          :update-value [{:Category "EARTH SCIENCE1"
                                          :Topic "HUMAN DIMENSIONS1"
                                          :Term "ENVIRONMENTAL IMPACTS1"
                                          :VariableLevel1 "HEAVY METALS CONCENTRATION1"}
                                         {:Category "EARTH SCIENCE2"
                                          :Topic "HUMAN DIMENSIONS2"
                                          :Term "ENVIRONMENTAL IMPACTS2"
                                          :VariableLevel1 "HEAVY METALS CONCENTRATION2"}
                                         {:Category "EARTH SCIENCE1"
                                          :Topic "HUMAN DIMENSIONS1"
                                          :Term "ENVIRONMENTAL IMPACTS1"
                                          :VariableLevel1 "HEAVY METALS CONCENTRATION1"}]}]
       ;; Kick off bulk update
       (let [response (ingest/bulk-update-collections "PROV1" bulk-update-body)]
         (is (= 200 (:status response)))
         ;; Wait for queueing/indexing to catch up
         (index/wait-until-indexed)
         (let [collection-response (ingest/bulk-update-task-status "PROV1" (:task-id response))]
           (is (= "COMPLETE" (:task-status collection-response))))

         ;; Check that each concept was updated
         (doseq [concept-id concept-ids
                 :let [concept (-> (search/find-concepts-umm-json :collection
                                                                  {:concept-id concept-id})
                                   :results
                                   :items
                                   first)]]
          (is (= (:ScienceKeywords (:umm concept))
                 [{:VariableLevel1 "HEAVY METALS CONCENTRATION1"
                   :Category "EARTH SCIENCE1"
                   :Term "ENVIRONMENTAL IMPACTS1"
                   :Topic "HUMAN DIMENSIONS1"}
                  {:VariableLevel1 "HEAVY METALS CONCENTRATION2"
                   :Category "EARTH SCIENCE2"
                   :Term "ENVIRONMENTAL IMPACTS2"
                   :Topic "HUMAN DIMENSIONS2"}]))))))

(deftest data-center-bulk-update
    (let [concept-ids (ingest-collection-in-each-format data-centers-umm)
          _ (index/wait-until-indexed)]
      (testing "Data center find and update"
        (let [bulk-update-body {:concept-ids concept-ids
                                :update-type "FIND_AND_UPDATE"
                                :update-field "DATA_CENTERS"
                                :find-value {:ShortName "NSID"}
                                :update-value {:ShortName "NSIDC"
                                               :Roles ["ORIGINATOR"]}}
              task-id (:task-id (ingest/bulk-update-collections "PROV1" bulk-update-body))]
          (index/wait-until-indexed)
          (let [collection-response (ingest/bulk-update-task-status "PROV1" task-id)]
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
           (is (= [{:ShortName "NSIDC"
                    :Roles ["ORIGINATOR"]}
                   {:ShortName "LPDAAC"
                    :Roles ["PROCESSOR"]}]
                  (map #(select-keys % [:Roles :ShortName])
                       (:DataCenters (:umm concept))))))))))

(deftest nil-instrument-long-name-bulk-update
    (let [concept-ids (ingest-collection-in-umm-json-format platforms-instruments-umm)
          _ (index/wait-until-indexed)]
      (testing "nil instrument long name find and update"
        (let [bulk-update-body {:concept-ids concept-ids
                                :update-type "FIND_AND_UPDATE"
                                :update-field "INSTRUMENTS"
                                :find-value {:ShortName "atm"}
                                :update-value {:ShortName "LVIS"
                                               :LongName nil}}
              task-id (:task-id (ingest/bulk-update-collections "PROV1" bulk-update-body))]
          (index/wait-until-indexed)
          (let [collection-response (ingest/bulk-update-task-status "PROV1" task-id)]
            (is (= "COMPLETE" (:task-status collection-response))))

          ;; Check that each concept was updated
          (doseq [concept-id concept-ids
                  :let [concept (-> (search/find-concepts-umm-json :collection
                                                                   {:concept-id concept-id})
                                    :results
                                    :items
                                    first)
                        platforms (get-in concept [:umm :Platforms])]]
           (is (= 2
                  (:revision-id (:meta concept))))
           (is (= [{:ShortName "a340-600-1"
                    :LongName "airbus a340-600-1"
                    :Type "Aircraft"}
                   {:ShortName "a340-600-2"
                    :LongName "airbus a340-600"
                    :Type "Aircraft"
                    :Instruments [{:ShortName "LVIS"
                                   :Technique "testing"
                                   :NumberOfInstruments 0
                                   :OperationalModes ["mode1" "mode2"]}]}
                   {:ShortName "a340-600-3"
                    :LongName "airbus a340-600"
                    :Type "Aircraft"
                    :Instruments [{:ShortName "LVIS"}]}]
                  platforms)))))))

(deftest instrument-bulk-update-find-and-replace
    (let [concept-ids (ingest-collection-in-umm-json-format platforms-instruments-umm)
          _ (index/wait-until-indexed)]
      (testing "instrument find and replace"
        (let [bulk-update-body {:concept-ids concept-ids
                                :update-type "FIND_AND_REPLACE"
                                :update-field "INSTRUMENTS"
                                :find-value {:ShortName "atm"}
                                :update-value [{:ShortName "LVIS"}]}
              task-id (:task-id (ingest/bulk-update-collections "PROV1" bulk-update-body))]
          (index/wait-until-indexed)
          (let [collection-response (ingest/bulk-update-task-status "PROV1" task-id)]
            (is (= "COMPLETE" (:task-status collection-response))))

          ;; Check that each concept was updated
          (doseq [concept-id concept-ids
                  :let [concept (-> (search/find-concepts-umm-json :collection
                                                                   {:concept-id concept-id})
                                    :results
                                    :items
                                    first)
                        platforms (get-in concept [:umm :Platforms])]]
           (is (= 2
                  (:revision-id (:meta concept))))
           ;; Make sure find and replace won't add an empty Instruments to the first platform which
           ;; doesn't contain any instruments.
           (is (= [{:ShortName "a340-600-1"
                    :LongName "airbus a340-600-1"
                    :Type "Aircraft"}
                   {:ShortName "a340-600-2"
                    :LongName "airbus a340-600"
                    :Type "Aircraft"
                    :Instruments [{:ShortName "LVIS"}]}
                   {:ShortName "a340-600-3"
                    :LongName "airbus a340-600"
                    :Type "Aircraft"
                    :Instruments [{:ShortName "LVIS"}]}]
                  platforms)))))))

(deftest nil-platform-long-name-bulk-update
    (let [concept-ids (ingest-collection-in-umm-json-format platforms-instruments-umm)
          _ (index/wait-until-indexed)]
      (testing "nil platform long name find and update"
        (let [bulk-update-body {:concept-ids concept-ids
                                :update-type "FIND_AND_UPDATE"
                                :update-field "PLATFORMS"
                                :find-value {:ShortName "a340-600-1"}
                                :update-value {:ShortName "SMAP"
                                               :LongName nil}}
              task-id (:task-id (ingest/bulk-update-collections "PROV1" bulk-update-body))]
          (index/wait-until-indexed)
          (let [collection-response (ingest/bulk-update-task-status "PROV1" task-id)]
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
           (is (= [{:ShortName "SMAP"
                    :Type "Aircraft"}
                   {:ShortName "a340-600-2"
                    :LongName "airbus a340-600"
                    :Type "Aircraft"
                    :Instruments [{:ShortName "atm"
                                   :LongName "airborne topographic mapper"
                                   :Technique "testing"
                                   :NumberOfInstruments 0
                                   :OperationalModes ["mode1" "mode2"]}]}
                   {:ShortName "a340-600-3"
                    :LongName "airbus a340-600"
                    :Type "Aircraft"
                    :Instruments [{:ShortName "atm"
                                   :LongName "airborne topographic mapper"}]}]
                  (:Platforms (:umm concept)))))))))

(deftest data-center-home-page-url-removal
    (let [concept-ids (ingest-collection-in-umm-json-format data-centers-umm-with-home-page-url)
          _ (index/wait-until-indexed)]
      (testing "Data center find and update home page url - removal case."
        (let [bulk-update-body {:concept-ids concept-ids
                                :update-type "FIND_AND_UPDATE_HOME_PAGE_URL"
                                :update-field "DATA_CENTERS"
                                :find-value {:ShortName "ShortName"}
                                :update-value {:ShortName "New ShortName"
                                               :LongName nil}}
              task-id (:task-id (ingest/bulk-update-collections "PROV1" bulk-update-body))]
          (index/wait-until-indexed)
          (let [collection-response (ingest/bulk-update-task-status "PROV1" task-id)]
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
           (is (= [{:ShortName "New ShortName"
                    :ContactInformation {:ContactMechanisms  [{:Type "Telephone"
                                                               :Value "1 303 492 6199 x"}
                                                              {:Type  "Fax"
                                                               :Value "1 303 492 2468 x"}
                                                              {:Type  "Email"
                                                               :Value "nsidc@nsidc.org"}]}}
                   {:ShortName "New ShortName"}
                   {:ShortName "New ShortName"
                    :ContactInformation {}}]
                  (map #(select-keys % [:ShortName :LongName :ContactInformation])
                       (:DataCenters (:umm concept))))))))))

(deftest data-center-home-page-url-update
    (let [concept-ids (ingest-collection-in-umm-json-format data-centers-umm-with-home-page-url)
          _ (index/wait-until-indexed)]
      (testing "Data center find and update home page url - update case."
        (let [bulk-update-body {:concept-ids concept-ids
                                :update-type "FIND_AND_UPDATE_HOME_PAGE_URL"
                                :update-field "DATA_CENTERS"
                                :find-value {:ShortName "ShortName"}
                                :update-value {:ShortName "New ShortName"
                                               :LongName "New LongName"
                                               :ContactInformation {:RelatedUrls [{:URLContentType "DataCenterURL"
                                                                                   :Type "HOME PAGE"
                                                                                   :URL "http://test.org/daac/index.html"}]}}}
              task-id (:task-id (ingest/bulk-update-collections "PROV1" bulk-update-body))]
          (index/wait-until-indexed)
          (let [collection-response (ingest/bulk-update-task-status "PROV1" task-id)]
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
           (is (= [{:ShortName "New ShortName"
                    :LongName "New LongName"
                    :ContactInformation {:RelatedUrls [{:URLContentType "DataCenterURL"
                                                        :Type "HOME PAGE"
                                                        :URL "http://test.org/daac/index.html"}]
                                         :ContactMechanisms  [{:Type "Telephone"
                                                               :Value "1 303 492 6199 x"}
                                                              {:Type  "Fax"
                                                               :Value "1 303 492 2468 x"}
                                                              {:Type  "Email"
                                                               :Value "nsidc@nsidc.org"}]}}
                   {:ShortName "New ShortName"
                    :LongName "New LongName"
                    :ContactInformation {:RelatedUrls [{:URLContentType "DataCenterURL"
                                                        :Type "HOME PAGE"
                                                        :URL "http://test.org/daac/index.html"}]}}
                   {:ShortName "New ShortName"
                    :LongName "New LongName"
                    :ContactInformation {:RelatedUrls [{:URLContentType "DataCenterURL"
                                                        :Type "HOME PAGE"
                                                        :URL "http://test.org/daac/index.html"}]}}]
                  (map #(select-keys % [:ShortName :LongName :ContactInformation])
                       (:DataCenters (:umm concept))))))))))

(deftest bulk-update-replace-test
  (let [concept-ids (ingest-collection-in-each-format find-replace-keywords-umm)
        _ (index/wait-until-indexed)
        bulk-update-body {:concept-ids concept-ids
                          :update-type "FIND_AND_REPLACE"
                          :update-field "SCIENCE_KEYWORDS"
                          :find-value {:Topic "ATMOSPHERE"}
                          :update-value {:Category "EARTH SCIENCE"
                                         :Topic "ATMOSPHERE"
                                         :Term "AIR QUALITY"
                                         :VariableLevel1 "EMISSIONS"}}
        task-id (:task-id (ingest/bulk-update-collections "PROV1" bulk-update-body))]
      (index/wait-until-indexed)
      (let [collection-response (ingest/bulk-update-task-status "PROV1" task-id)]
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
       (is (= [{:Category "EARTH SCIENCE"
                :Topic "ATMOSPHERE"
                :Term "AIR QUALITY"
                :VariableLevel1 "EMISSIONS"}]
              (:ScienceKeywords (:umm concept)))))))

(deftest bulk-update-replace-with-identical-update-value-test
  (let [concept-ids (ingest-collection-in-each-format find-replace-keywords-umm)
        _ (index/wait-until-indexed)
        bulk-update-body {:concept-ids concept-ids
                          :update-type "FIND_AND_REPLACE"
                          :update-field "SCIENCE_KEYWORDS"
                          :find-value {:VariableLevel1 "CARBON MONOXIDE"}
                          :update-value {:Category "EARTH SCIENCE"
                                         :Topic "ATMOSPHERE"
                                         :Term "AIR QUALITY"
                                         :VariableLevel1 "CARBON MONOXIDE"}}
        task-id (:task-id (ingest/bulk-update-collections "PROV1" bulk-update-body))]
      (index/wait-until-indexed)
      (let [collection-response (ingest/bulk-update-task-status "PROV1" task-id)]
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
       (is (= [{:Category "EARTH SCIENCE"
                :Topic "ATMOSPHERE"
                :Term "AIR QUALITY"
                :VariableLevel1 "CARBON MONOXIDE"}
               {:Category "EARTH SCIENCE"
                :Topic "ATMOSPHERE"
                :Term "CLOUDS"
                :VariableLevel1 "CLOUD MICROPHYSICS"
                :VariableLevel2 "CLOUD LIQUID WATER/ICE"}]
              (:ScienceKeywords (:umm concept)))))))

(deftest bulk-update-remove-all-instruments-test
  (let [concept-ids (ingest-collection-in-umm-json-format platforms-instruments-umm)
        _ (index/wait-until-indexed)
        bulk-update-body {:concept-ids concept-ids
                          :update-type "FIND_AND_REMOVE"
                          :update-field "INSTRUMENTS"
                          :find-value {:ShortName "atm"}}
        task-id (:task-id (ingest/bulk-update-collections "PROV1" bulk-update-body))]
      (index/wait-until-indexed)
      (let [collection-response (ingest/bulk-update-task-status "PROV1" task-id)
            collection-status (first (:collection-statuses collection-response))]
        (is (= "COMPLETE" (:task-status collection-response)))
        (is (= "UPDATED" (:status collection-status)))
        (is (= "Collection was updated successfully, but translating the collection to UMM-C had the following issues: [:MetadataDates] latest UPDATE date value: [2017-01-01T00:00:00.000Z] should be in the past.; [:Platforms 0] Platform short name [a340-600-1], long name [airbus a340-600-1], and type [Aircraft] was not a valid keyword combination.; [:Platforms 1] Platform short name [a340-600-2], long name [airbus a340-600], and type [Aircraft] was not a valid keyword combination.; [:Platforms 2] Platform short name [a340-600-3], long name [airbus a340-600], and type [Aircraft] was not a valid keyword combination." (:status-message collection-status))))

      ;; Check that each concept was updated.
      (doseq [concept-id concept-ids
              :let [concept (-> (search/find-concepts-umm-json :collection
                                                               {:concept-id concept-id})
                                :results
                                :items
                                first)]]
       (is (= 2
              (:revision-id (:meta concept))))
       (is (= [{:ShortName "a340-600-1"
                :LongName "airbus a340-600-1"
                :Type "Aircraft"}
               {:ShortName "a340-600-2"
                :LongName "airbus a340-600"
                :Type "Aircraft"}
               {:ShortName "a340-600-3"
                :LongName "airbus a340-600"
                :Type "Aircraft"}]
              (:Platforms (:umm concept)))))))

(deftest bulk-update-update-test
  (let [concept-ids (ingest-collection-in-each-format find-update-keywords-umm)
        _ (index/wait-until-indexed)
        bulk-update-body {:concept-ids concept-ids
                          :update-type "FIND_AND_UPDATE"
                          :update-field "SCIENCE_KEYWORDS"
                          :find-value {:Topic "ATMOSPHERE"}
                          :update-value {:Category "EARTH SCIENCE"
                                         :Topic "ATMOSPHERE"
                                         :Term "AIR QUALITY"
                                         :VariableLevel1 "EMISSIONS"}}
        task-id (:task-id (ingest/bulk-update-collections "PROV1" bulk-update-body))]
      (index/wait-until-indexed)
      (let [collection-response (ingest/bulk-update-task-status "PROV1" task-id)]
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
       (is (= [{:Category "EARTH SCIENCE",
                :Topic "ATMOSPHERE",
                :Term "AIR QUALITY",
                :VariableLevel1 "EMISSIONS"}
               {:Category "EARTH SCIENCE",
                :Topic "ATMOSPHERE",
                :Term "AIR QUALITY",
                :VariableLevel1 "EMISSIONS",
                :VariableLevel2 "CLOUD LIQUID WATER/ICE"}]
              (:ScienceKeywords (:umm concept)))))))

(deftest bulk-update-update-with-identical-update-value-test
  (let [concept-ids (ingest-collection-in-each-format science-keywords-umm)
        _ (index/wait-until-indexed)
        bulk-update-body {:concept-ids concept-ids
                          :update-type "FIND_AND_UPDATE"
                          :update-field "SCIENCE_KEYWORDS"
                          :find-value {:Topic "OCEANS"}
                          :update-value {:Category "EARTH SCIENCE"
                                         :Topic "OCEANS"
                                         :Term "MARINE SEDIMENTS"}}
        task-id (:task-id (ingest/bulk-update-collections "PROV1" bulk-update-body))]
      (index/wait-until-indexed)
      (let [collection-response (ingest/bulk-update-task-status "PROV1" task-id)]
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
       (is (= [{:Category "EARTH SCIENCE"
                :Topic "OCEANS"
                :Term "MARINE SEDIMENTS"}]
              (:ScienceKeywords (:umm concept)))))))

(deftest bulk-update-update-not-found-test
  (let [concept-ids (ingest-collection-in-each-format find-update-keywords-umm)
        _ (index/wait-until-indexed)
        bulk-update-body {:concept-ids concept-ids
                          :update-type "FIND_AND_UPDATE"
                          :update-field "SCIENCE_KEYWORDS"
                          ;; Note: find-value is case-sensitive.
                          :find-value {:Topic "aTmoSPHERE"}
                          :update-value {:Category "EARTH SCIENCE"
                                         :Topic "ATMOSPHERE"
                                         :Term "AIR QUALITY"
                                         :VariableLevel1 "EMISSIONS"}}
        original-concepts (doseq [concept-id concept-ids]
                            (-> (search/find-concepts-umm-json :collection
                                                               {:concept-id concept-id})
                                :results
                                :items
                                first))
        task-id (:task-id (ingest/bulk-update-collections "PROV1" bulk-update-body))
        _ (index/wait-until-indexed)
        new-concepts (doseq [concept-id concept-ids]
                       (-> (search/find-concepts-umm-json :collection
                                                           {:concept-id concept-id})
                            :results
                            :items
                            first))]
      (let [collection-response (ingest/bulk-update-task-status "PROV1" task-id)
            collection-statuses (for [concept-id concept-ids]
                                  {:concept-id concept-id
                                   :status "SKIPPED"
                                   :status-message (str "Collection with concept-id [" concept-id
                                                        "] is not updated because no find-value found.")})]
        (is (= "COMPLETE" (:task-status collection-response)))
        (is (= "Task completed with 6 SKIPPED out of 6 total collection update(s)." (:status-message collection-response)))
        (is (= collection-statuses
               (:collection-statuses collection-response))))

      ;; Check that each concept was not updated.
      ;; revision-id not changed, format not changed, it's identical to the original-concepts.
      (is (= new-concepts original-concepts))))

(deftest bulk-update-update-all-tombstone-test
  (let [coll1 (data2-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "E1"
                                                                                     :ShortName "S1"}))
        coll2 (data2-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "E2"
                                                                                     :ShortName "S2"}))
        _ (index/wait-until-indexed)
        bulk-update-body1 {:concept-ids ["ALL"]
                           :update-type "FIND_AND_UPDATE"
                           :update-field "SCIENCE_KEYWORDS"
                           ;; Note: find-value is case-sensitive.
                           :find-value {:Topic "aTmoSPHERE"}
                           :update-value {:Category "EARTH SCIENCE"
                                          :Topic "ATMOSPHERE"
                                          :Term "AIR QUALITY"
                                          :VariableLevel1 "EMISSIONS"}}
        bulk-update-body2 {:concept-ids [(:concept-id coll1) (:concept-id coll2)]
                           :update-type "FIND_AND_UPDATE"
                           :update-field "SCIENCE_KEYWORDS"
                           ;; Note: find-value is case-sensitive.
                           :find-value {:Topic "aTmoSPHERE"}
                           :update-value {:Category "EARTH SCIENCE"
                                          :Topic "ATMOSPHERE"
                                          :Term "AIR QUALITY"
                                          :VariableLevel1 "EMISSIONS"}}]
    ;; perform bulk update, verify that both collections are skipped because find-value is not found.
    (testing "all the non-deleted collections are included in update all case."
      (let [response (ingest/bulk-update-collections "PROV1" bulk-update-body1)
            _ (index/wait-until-indexed)
            collection-response (ingest/bulk-update-task-status "PROV1" (:task-id response))]
        (is (= "COMPLETE" (:task-status collection-response)))
        (is (= "Task completed with 2 SKIPPED out of 2 total collection update(s)." (:status-message collection-response)))))

    ;; delete the collection
    (is (= 200 (:status (ingest/delete-concept (data2-core/umm-c-collection->concept coll1 :echo10)))))
    (index/wait-until-indexed)

    ;; perform another bulk update, verify that the deleted collection is not included when getting all
    ;; collections from the provider.
    (testing "Deleted collection is excluded in update all case."
      (let [response (ingest/bulk-update-collections "PROV1" bulk-update-body1)
            _ (index/wait-until-indexed)
            collection-response (ingest/bulk-update-task-status "PROV1" (:task-id response))]
        (is (= "Task completed with 1 SKIPPED out of 1 total collection update(s)." (:status-message collection-response)))))
    ;; perform a bulk update with the deleted collection's concept-id and a non-deleted collection's concept-id
    ;; The deleted one should fail the update.
    (testing "Deleted collection is failed in update concept-id case."
      (let [response (ingest/bulk-update-collections "PROV1" bulk-update-body2)
            _ (index/wait-until-indexed)
            collection-response (ingest/bulk-update-task-status "PROV1" (:task-id response))
            collection-statuses (:collection-statuses collection-response)]
        (is (= "COMPLETE" (:task-status collection-response)))
        (is (= "Collection with concept-id [C1200000009-PROV1] is deleted. Can not be updated."
               (get (first collection-statuses) :status-message)))
        (is (= "Task completed with 1 FAILED and 1 SKIPPED out of 2 total collection update(s)." (:status-message collection-response)))))

    ;; delete the second collection
    (is (= 200 (:status (ingest/delete-concept (data2-core/umm-c-collection->concept coll2 :echo10)))))
    (index/wait-until-indexed)

    ;; perform another bulk update, verify that the deleted collections are not included when getting all
    ;; collections from the provider.
    (testing "All collections are deleted in update all case."
      (let [response (ingest/bulk-update-collections "PROV1" bulk-update-body1)
            _ (index/wait-until-indexed)]
        (is (= ["There are no collections that have not been deleted for provider [PROV1]."]
               (:errors response)))))))

(deftest bulk-update-default-name-test
  (let [concept-ids (ingest-collection-in-each-format find-update-keywords-umm)
        _ (index/wait-until-indexed)
        bulk-update-body {:concept-ids concept-ids
                          :update-type "FIND_AND_UPDATE"
                          :update-field "SCIENCE_KEYWORDS"
                          :find-value {:Topic "ATMOSPHERE"}
                          :update-value {:Category "EARTH SCIENCE"
                                         :Topic "ATMOSPHERE"
                                         :Term "AIR QUALITY"
                                         :VariableLevel1 "EMISSIONS"}}
        response (ingest/bulk-update-collections "PROV1" bulk-update-body)]
    (is (= 200 (:status response)))
    ;; Wait for queueing/indexing to catch up
    (index/wait-until-indexed)
    (let [collection-response (ingest/bulk-update-task-status "PROV1" (:task-id response))]
      (is (= (:task-id response) (get collection-response :name)))
      (is (= "COMPLETE" (:task-status collection-response))))))

(deftest bulk-update-unique-name-test
  (let [concept-ids (ingest-collection-in-each-format find-update-keywords-umm)
        _ (index/wait-until-indexed)
        bulk-update-body {:concept-ids concept-ids
                          :name "unique"
                          :update-type "FIND_AND_UPDATE"
                          :update-field "SCIENCE_KEYWORDS"
                          :find-value {:Topic "ATMOSPHERE"}
                          :update-value {:Category "EARTH SCIENCE"
                                         :Topic "ATMOSPHERE"
                                         :Term "AIR QUALITY"
                                         :VariableLevel1 "EMISSIONS"}}
        response (ingest/bulk-update-collections "PROV1" bulk-update-body)
        response2 (ingest/bulk-update-collections "PROV1" bulk-update-body)]
    (is (= 200 (:status response)))
    (is (= 422 (:status response2)))
    (is (= ["Error creating bulk update task: Bulk update name needs to be unique within the provider."]
           (:errors response2)))))

(deftest bulk-update-xml-to-umm-failure-test
  (let [coll-metadata (slurp (io/resource "dif-samples/cmr-4455-collection.xml"))
        concept (ingest/ingest-concept
                 (ingest/concept :collection "PROV1" "foo" :dif coll-metadata))
        _ (index/wait-until-indexed)
        bulk-update-body {:concept-ids [(:concept-id concept)]
                          :update-type "ADD_TO_EXISTING"
                          :update-field "SCIENCE_KEYWORDS"
                          :update-value {:Category "EARTH SCIENCE"
                                         :Topic "HUMAN DIMENSIONS"
                                         :Term "ENVIRONMENTAL IMPACTS"
                                         :VariableLevel1 "HEAVY METALS CONCENTRATION"}}]
    ;; Kick off bulk update
    (let [response (ingest/bulk-update-collections "PROV1" bulk-update-body)]
      (is (= 200 (:status response)))
      ;; Wait for queueing/indexing to catch up
      (index/wait-until-indexed)
      (let [collection-response (ingest/bulk-update-task-status "PROV1" (:task-id response))]
        (is (= "COMPLETE" (:task-status collection-response)))))))

(deftest bulk-update-large-status-message-test
  (let [concept-ids (ingest-collection-in-umm-json-format large-status-message-umm)
        _ (index/wait-until-indexed)
        bulk-update-body {:concept-ids concept-ids
                          :update-type "ADD_TO_EXISTING"
                          :update-field "SCIENCE_KEYWORDS"
                          :update-value {:Category "EARTH SCIENCE"
                                         :Topic "HUMAN DIMENSIONS"
                                         :Term "ENVIRONMENTAL IMPACTS"
                                         :VariableLevel1 "HEAVY METALS CONCENTRATION"}}
        task-id (:task-id (ingest/bulk-update-collections "PROV1" bulk-update-body))]
    (index/wait-until-indexed)
    (let [collection-response (ingest/bulk-update-task-status "PROV1" task-id)
          collection-status (first (:collection-statuses collection-response))]
      (is (= "COMPLETE" (:task-status collection-response)))
      (is (= "UPDATED" (:status collection-status)))
      (is (< 255 (count (:status-message collection-status)))))))
