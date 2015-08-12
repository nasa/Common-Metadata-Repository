(ns cmr.system-int-test.ingest.translation-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as umm-cmn]
            [clj-time.core :as t]
            [cmr.umm-spec.core :as umm-spec]
            [cmr.common.mime-types :as mt]
            [cmr.umm-spec.test.expected-conversion :as expected-conversion]))

(def example-record
  "This contains an example record with fields supported by all formats"
  (umm-c/map->UMM-C
    {:EntryId (umm-cmn/map->EntryIdType {:Id "short_V1"})
     :EntryTitle "The entry title V5"}))

(def valid-formats
  [:umm-json
   :iso-smap
   :iso19115
   :dif
   :dif10
   :echo10])

(defn assert-translate-failure
  [error-regex & args]
  (let [{:keys [status body]} (apply ingest/translate-metadata args)]
    (is (= 400 status))
    (is (re-find error-regex body))))

(deftest translate-metadata
  (doseq [input-format valid-formats
          output-format valid-formats]
    (testing (format "Translating %s to %s" (name input-format) (name output-format))
      (let [input-str (umm-spec/generate-metadata :collection input-format example-record)
            input-correction (expected-conversion/metadata-format->expected-conversion input-format)
            output-correction (expected-conversion/metadata-format->expected-conversion output-format)
            expected (-> example-record input-correction output-correction)
            {:keys [status headers body]} (ingest/translate-metadata :collection input-format input-str output-format)
            content-type (:content-type headers)]
            (is (= 200 status))
            (is (= (mt/format->mime-type output-format) content-type))
            (is (= expected (umm-spec/parse-metadata :collection output-format body))))))

  (testing "Failure cases"
    (testing "unsupported input format"
      (assert-translate-failure
        #"The mime types specified in the content-type header \[application/xml\] are not supported"
        :collection :xml "notread" :umm-json))

    (testing "not specified input format"
      (assert-translate-failure
       #"The mime types specified in the content-type header \[\] are not supported"
        :collection nil "notread" :umm-json))

    (testing "unsupported output format"
      (assert-translate-failure
        #"The mime types specified in the accept header \[application/xml\] are not supported"
        :collection :echo10 "notread" :xml))

    (testing "not specified output format"
      (assert-translate-failure
       #"The mime types specified in the accept header \[\] are not supported"
        :collection :echo10 "notread" nil))))


