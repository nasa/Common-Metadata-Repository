(ns cmr.indexer.test.services.index-service
  "Integration test for elasticsearch data service"
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [cmr.indexer.services.index-service :as svc]
            [cmr.indexer.services.concepts.collection]))

(deftest concept->elastic-doc-test
  (let [short-name "MINIMAL"
        version-id "1"
        dataset-id "A minimal valid collection V 1"
        provider-id "PROV1"
        concept-id "C1234-PROV1"
        concept {:concept-id concept-id
                 :provider-id provider-id}
        platforms [{:short-name "PLATFORM ONE"
                    :long-name "dummy"
                    :type "Dummy"
                    :instruments [{:short-name "SAR"}
                                  {:short-name "MAR"}]}
                   {:short-name "PLATFORM TWO"
                    :long-name "dummy"
                    :type "Dummy"
                    :instruments [{:short-name "DAR"}]}]
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
                     :product {:short-name short-name
                               :version-id version-id}
                     :revision-date  nil
                     :temporal {:range-date-times [{:beginning-date-time (t/date-time 1996 2 24 22 20 41)
                                                    :ending-date-time (t/date-time 1997 3 25 23 23 43 123)}]}
                     :platforms platforms
                     :projects projects
                     :two-d-coordinate-systems [{:name "FOO"}
                                                {:name "Bar"}]
                     :organizations orgs}
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
                  :start-date "1996-02-24T22:20:41.000Z"
                  :end-date "1997-03-25T23:23:43.123Z"
                  :platform-sn ["PLATFORM ONE" "PLATFORM TWO"]
                  :platform-sn.lowercase ["platform one" "platform two"]
                  :instrument-sn ["SAR" "MAR" "DAR"]
                  :instrument-sn.lowercase ["sar" "mar" "dar"]
                  :project-sn ["ESI" "EVI" "EPI"]
                  :project-sn.lowercase ["esi" "evi" "epi"]
                  :archive-center ["SEDAC AC"]
                  :archive-center.lowercase ["sedac ac"]
                  :attributes []
                  :two-d-coord-name ["FOO" "Bar"]
                  :two-d-coord-name.lowercase  ["foo" "bar"]}
        actual (svc/concept->elastic-doc nil concept umm-concept)]
    (is (= expected actual))))

