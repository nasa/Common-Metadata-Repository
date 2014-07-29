(ns cmr.indexer.test.data.elasticsearch
  "Tests for elasticsearch in data layer"
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [cmr.indexer.data.elasticsearch :as es]
            [cmr.indexer.data.concepts.collection]))

(deftest concept->elastic-doc-test
  (let [short-name "MINIMAL"
        version-id "1"
        dataset-id "A minimal valid collection V 1"
        provider-id "PROV1"
        concept-id "C1234-PROV1"
        processing-level-id "1B"
        collection-data-type "NEAR_REAL_TIME"
        concept {:concept-id concept-id
                 :provider-id provider-id
                 :format "application/echo10+xml"}
        platforms [{:short-name "PLATFORM ONE"
                    :long-name "dummy"
                    :type "Dummy"
                    :instruments [{:short-name "SAR"
                                   :sensors [{:short-name "DAR"}
                                             {:short-name "TAR"}]}
                                  {:short-name "MAR"}]}
                   {:short-name "PLATFORM TWO"
                    :long-name "dummy"
                    :type "Dummy"
                    :instruments [{:short-name "DAR"
                                   :sensors [{:short-name "WAR"}]}]}]
        projects [{:short-name "ESI"
                   :long-name "dummy"}
                  {:short-name "EVI"
                   :long-name "dummy"}
                  {:short-name "EPI"
                   :long-name "dummy"}]
        orgs [{:type :processing-center :org-name "SEDAC PC"}
              {:type :archive-center :org-name "SEDAC AC"}]
        umm-concept {:entry-id "MINIMAL_1"
                     :entry-title dataset-id
                     :summary "Summary of collection"
                     :product {:short-name short-name
                               :version-id version-id
                               :processing-level-id processing-level-id
                               :collection-data-type collection-data-type}
                     :revision-date  nil
                     :temporal {:range-date-times [{:beginning-date-time (t/date-time 1996 2 24 22 20 41)
                                                    :ending-date-time (t/date-time 1997 3 25 23 23 43 123)}]}
                     :platforms platforms
                     :projects projects
                     :two-d-coordinate-systems [{:name "FOO"}
                                                {:name "Bar"}]
                     :spatial-keywords ["New York" "Washington DC"]
                     :organizations orgs
                     :data-provider-timestamps {:update-time (t/date-time 2014 2 24 22 20 56)}
                     :spatial-coverage {:spatial-representation :cartesian}
                     :associated-difs ["DIF-100" "DIF-101"]}
        expected {:concept-id concept-id
                  :entry-id "MINIMAL_1"
                  :entry-id.lowercase "minimal_1"
                  :entry-title dataset-id
                  :entry-title.lowercase "a minimal valid collection v 1"
                  :provider-id provider-id
                  :provider-id.lowercase "prov1"
                  :short-name short-name
                  :short-name.lowercase "minimal"
                  :version-id version-id
                  :version-id.lowercase "1"
                  :revision-date  nil
                  :processing-level-id "1B"
                  :processing-level-id.lowercase "1b"
                  :collection-data-type "NEAR_REAL_TIME"
                  :collection-data-type.lowercase "near_real_time"
                  :start-date "1996-02-24T22:20:41.000Z"
                  :end-date "1997-03-25T23:23:43.123Z"
                  :platform-sn ["PLATFORM ONE" "PLATFORM TWO"]
                  :platform-sn.lowercase ["platform one" "platform two"]
                  :instrument-sn ["SAR" "MAR" "DAR"]
                  :instrument-sn.lowercase ["sar" "mar" "dar"]
                  :sensor-sn ["DAR" "TAR" "WAR"]
                  :sensor-sn.lowercase ["dar" "tar" "war"]
                  :project-sn ["ESI" "EVI" "EPI"]
                  :project-sn.lowercase ["esi" "evi" "epi"]
                  :archive-center ["SEDAC AC"]
                  :archive-center.lowercase ["sedac ac"]
                  :spatial-keyword ["New York" "Washington DC"]
                  :spatial-keyword.lowercase ["new york" "washington dc"]
                  :attributes []
                  :science-keywords []
                  :two-d-coord-name ["FOO" "Bar"]
                  :two-d-coord-name.lowercase  ["foo" "bar"]
                  :downloadable false
                  :browsable false
                  :atom-links []
                  :summary "Summary of collection"
                  :original-format "ECHO10"
                  :coordinate-system "CARTESIAN"
                  :update-time "2014-02-24T22:20:56.000Z"
                  :associated-difs ["DIF-100" "DIF-101"]
                  :associated-difs.lowercase ["dif-100" "dif-101"]}
        actual (es/concept->elastic-doc nil concept umm-concept)]
    (is (= expected actual))))

