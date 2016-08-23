(ns cmr.system-int-test.ingest.translation-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.umm-spec.models.umm-collection-models :as umm-c]
            [cmr.umm-spec.models.umm-common-models :as umm-cmn]
            [clj-time.core :as t]
            [cmr.umm-spec.umm-spec-core :as umm-spec]
            [cmr.common.mime-types :as mt]
            [cmr.umm-spec.test.expected-conversion :as expected-conversion]
            [cmr.umm-spec.test.location-keywords-helper :as lkt]))

(def valid-formats
  [:umm-json
   :iso19115
   :iso-smap
   :dif
   :dif10
   :echo10])

(def test-context (lkt/setup-context-for-test lkt/sample-keyword-map))

(defn assert-translate-failure
  [error-regex & args]
  (let [{:keys [status body]} (apply ingest/translate-metadata args)]
    (is (= 400 status))
    (is (re-find error-regex body))))

(defn assert-invalid-data
  [error-regex & args]
  (let [{:keys [status body]} (apply ingest/translate-metadata args)]
    (is (= 422 status))
    (is (re-find error-regex body))))

(def minimal-valid-echo-xml
  "<Collection>
  <ShortName>ShortName_Larc</ShortName>
  <VersionId>Version01</VersionId>
  <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
  <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
  <DeleteTime>2015-05-23T22:30:59</DeleteTime>
  <LongName>LarcLongName</LongName>
  <DataSetId>LarcDatasetId</DataSetId>
  <Description>A minimal valid collection</Description>
  <Orderable>true</Orderable>
  <Visible>true</Visible>
  </Collection>")

(deftest translate-metadata
  (doseq [input-format valid-formats
          output-format valid-formats]
    (testing (format "Translating %s to %s" (name input-format) (name output-format))
      (let [input-str (umm-spec/generate-metadata test-context expected-conversion/example-collection-record input-format)
            expected (expected-conversion/convert expected-conversion/example-collection-record input-format output-format)
            {:keys [status headers body]} (ingest/translate-metadata :collection input-format input-str output-format)
            _ (is (= 200 status) body)
            content-type (first (mt/extract-mime-types (:content-type headers)))
            parsed-umm-json (umm-spec/parse-metadata test-context :collection output-format body)]
        (is (= 200 status) body)
        (is (= (mt/format->mime-type output-format) content-type))
        (is (= expected parsed-umm-json)))))

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
          #"Element 'Entry_ID' is a simple type, so it must have no element information item"
          :collection :dif (umm-spec/generate-metadata test-context expected-conversion/example-collection-record :dif10) :umm-json))

      (testing "bad json"
        (assert-translate-failure #"object has missing required properties"
                                  :collection :umm-json "{}" :echo10))

      (testing "Good XML, invalid UMM"
        (assert-invalid-data #"object has missing required properties"
                             :collection :echo10 minimal-valid-echo-xml :dif10))

      (testing "Good XML, invalid UMM, skip validation"
        (let [input-format :echo10
              output-format :dif10
              {:keys [status headers body]} (ingest/translate-metadata :collection input-format minimal-valid-echo-xml output-format
                                                                       {:query-params {"skip_umm_validation" "true"}})
              content-type (first (mt/extract-mime-types (:content-type headers)))]
          (is (= 200 status))
          (is (= (mt/format->mime-type output-format) content-type))
          (is (re-find #"<Short_Name>ShortName_Larc</Short_Name>" body)))))))

(deftest translate-metadata-handles-date-string
  (testing "CMR-2257: date-string in DIF9 causes InternalServerError"
    (let [dif9-xml "<DIF xmlns=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\" xmlns:dif=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/ http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/dif_v9.8.4.xsd\">
                   <Entry_ID>minimal_dif_dataset</Entry_ID>
                   <Entry_Title>A minimal dif dataset</Entry_Title>
                   <Data_Set_Citation>
                   <Dataset_Title>dataset_title</Dataset_Title>
                   </Data_Set_Citation>
                   <Parameters>
                   <Category>category</Category>
                   <Topic>topic</Topic>
                   <Term>term</Term>
                   </Parameters>
                   <Temporal_Coverage>
                   <Start_Date>1975-01-01</Start_Date>
                   </Temporal_Coverage>
                   <Data_Center>
                   <Data_Center_Name>
                   <Short_Name>datacenter_short_name</Short_Name>
                   <Long_Name>data center long name</Long_Name>
                   </Data_Center_Name>
                   <Personnel>
                   <Role>DummyRole</Role>
                   <Last_Name>UNEP</Last_Name>
                   </Personnel>
                   </Data_Center>
                   <Summary>
                   <Abstract>summary of the dataset</Abstract>
                   <Purpose>A grand purpose</Purpose>
                   </Summary>
                   <Metadata_Name>CEOS IDN DIF</Metadata_Name>
                   <Metadata_Version>VERSION 9.8.4</Metadata_Version>
                   <Last_DIF_Revision_Date>2013-10-22</Last_DIF_Revision_Date>
                   </DIF>"
                   {:keys [status]} (ingest/translate-metadata
                                           :collection :dif dif9-xml :umm-json
                                           {:query-params {"skip_umm_validation" "true"}})]
      (is (= 200 status)))))

(comment

  (do
    (def input :dif)
    (def output :dif)


    (def metadata (umm-spec/generate-metadata test-context expected-conversion/example-collection-record input))

    (def parsed-from-metadata (umm-spec/parse-metadata test-context :collection input metadata))

    (def metadata-regen (umm-spec/generate-metadata test-context parsed-from-metadata output))

    (def parsed-from-metadata-regen (umm-spec/parse-metadata test-context :collection output metadata-regen)))


  (println metadata)

  (println metadata-regen)

  (= metadata-regen metadata)


  (println (:body (ingest/translate-metadata :collection :echo10 metadata :echo10)))

  (def expected (-> expected-conversion/example-collection-record
                    (expected-conversion/convert input)
                    (expected-conversion/convert output))))
