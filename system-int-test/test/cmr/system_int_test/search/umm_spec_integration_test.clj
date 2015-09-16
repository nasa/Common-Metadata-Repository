(ns cmr.system-int-test.search.umm-spec-integration-test.clj
  "Round trip test for MMT ingesting to CMR. Converts example UMM-JSON to ISO-19115 then ingests
  and searches based on various fields."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as umm-cmn]
            [clj-time.core :as t]
            [cmr.umm-spec.core :as umm-spec]
            [cmr.common.mime-types :as mt]
            [cmr.umm-spec.test.expected-conversion :as expected-conversion]))

(deftest translate-metadata
  (doseq [input-format valid-formats
          output-format valid-formats]
    (testing (format "Translating %s to %s" (name input-format) (name output-format))
      (let [input-str (umm-spec/generate-metadata :collection input-format expected-conversion/example-record)
            expected (expected-conversion/convert expected-conversion/example-record input-format output-format)
            {:keys [status headers body]} (ingest/translate-metadata :collection input-format input-str output-format)
            content-type (:content-type headers)]
        (is (= 200 status))
        (is (= (mt/format->mime-type output-format) content-type))
        (is (= expected (umm-spec/parse-metadata :collection output-format body))))))
