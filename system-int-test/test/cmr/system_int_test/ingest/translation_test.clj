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
  "This is the minimum valid UMM."
  (umm-c/map->UMM-C
    {:Platform [(umm-cmn/map->PlatformType
                  {:ShortName "Platform"
                   :Instruments [(umm-cmn/map->InstrumentType {:ShortName "Instrument"})]})]
     :ProcessingLevel (umm-c/map->ProcessingLevelType {})
     :RelatedUrl [(umm-cmn/map->RelatedUrlType {:URL ["http://google.com"]})]
     :ResponsibleOrganization [(umm-cmn/map->ResponsibilityType {:Role "RESOURCEPROVIDER"
                                                                 :Party (umm-cmn/map->PartyType {})})]
     :ScienceKeyword [(umm-cmn/map->ScienceKeywordType {:Category "cat" :Topic "top" :Term "ter"})]
     :SpatialExtent [(umm-cmn/map->SpatialExtentType {:GranuleSpatialRepresentation "NO_SPATIAL"})]

     :EntryId "short"
     :EntryTitle "The entry title V5"
     :DataDate [(umm-cmn/map->DateType {:Date (t/date-time 2012)
                                        :Type "CREATE"})]
     :Abstract "A very abstract collection"
     :TemporalExtent [(umm-cmn/map->TemporalExtentType {})]}))

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
        :collection :echo10 "notread" nil))

    (testing "invalid metadata"
      (testing "bad xml"
        (assert-translate-failure
          #"Cannot find the declaration of element 'this'"
          :collection :echo10 "<this> is not good XML</this>" :umm-json))

      (testing "wrong xml format"
        (assert-translate-failure
          #"Invalid content was found starting with element 'Version'"
          :collection :dif (umm-spec/generate-metadata :collection :dif10 example-record) :umm-json))

      (testing "bad json"
        (assert-translate-failure #"object has missing required properties"
                                  :collection :umm-json "{}" :echo10)))))


