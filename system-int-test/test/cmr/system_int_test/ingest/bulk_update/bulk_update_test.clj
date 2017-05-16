(ns cmr.system-int-test.ingest.bulk-update.bulk-update-test
  "CMR bulk update. Test the actual update "
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.message-queue.test.queue-broker-side-api :as qb-side-api]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def collection-formats
  "Formats to test bulk update"
  [:dif :dif10 :echo10 :iso19115 :iso-smap :umm-json])

(def science-keywords-umm
  {:ScienceKeywords [{:Category "EARTH SCIENCE"
                      :Topic "OCEANS"
                      :Term "MARINE SEDIMENTS"}]})

(defn- generate-concept-id
  [index provider]
  (format "C120000000%s-%s" index provider))

(deftest bulk-update-science-keywords
  ;; Ingest a collection in each format with science keywords to update
  (let [concept-ids
        (for [x (range (count collection-formats))
              :let [format (nth collection-formats x)
                    collection (data-umm-c/collection-concept
                                (data-umm-c/collection x science-keywords-umm)
                                format)]]
          (:concept-id (ingest/ingest-concept
                        (assoc collection :concept-id (generate-concept-id x "PROV1")))))
        _ (index/wait-until-indexed)
        bulk-update-body {:concept-ids concept-ids
                          :update-type "ADD_TO_EXISTING"
                          :update-field "SCIENCE_KEYWORDS"
                          :update-value {:Category "EARTH SCIENCE"
                                         :Topic "HUMAN DIMENSIONS"
                                         :Term "ENVIRONMENTAL IMPACTS"
                                         :VariableLevel1 "HEAVY METALS CONCENTRATION"}}]
       ;; Kick off bulk update
       (ingest/bulk-update-collections "PROV1" bulk-update-body)
       ;; Wait for queueing/indexing to catch up
       (index/wait-until-indexed)
       (let [collection-response (ingest/bulk-update-task-status "PROV1" 1)]
         (is (= "COMPLETE" (:task-status collection-response))))

       ;; Check that each concept was updated
       (doseq [concept-id concept-ids
               :let [concept (-> (search/find-concepts-umm-json
                                   :collection {:concept-id concept-id})
                                 :results
                                 :items
                                 first)]]
        (is (= 2
               (:revision-id (:meta concept))))
        (is (= (:ScienceKeywords (:umm concept))
               [{:Category "EARTH SCIENCE"
                 :Term "MARINE SEDIMENTS"
                 :Topic "OCEANS"}
                {:VariableLevel1 "HEAVY METALS CONCENTRATION"
                 :Category "EARTH SCIENCE"
                 :Term "ENVIRONMENTAL IMPACTS"
                 :Topic "HUMAN DIMENSIONS"}])))))
