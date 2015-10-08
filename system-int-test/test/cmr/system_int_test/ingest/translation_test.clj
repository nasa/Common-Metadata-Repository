(ns cmr.system-int-test.ingest.translation-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as umm-cmn]
            [clj-time.core :as t]
            [cmr.umm-spec.core :as umm-spec]
            [cmr.common.mime-types :as mt]
            [cmr.umm-spec.test.expected-conversion :as expected-conversion]))

(def valid-formats
  [
   :umm-json
   :iso19115
   ;; the following formats will be re-enabled once ISO support is complete
   ;; :iso-smap
   ;; :dif
   ;; :dif10
   ;; :echo10
   ])

(defn assert-translate-failure
  [error-regex & args]
  (let [{:keys [status body]} (apply ingest/translate-metadata args)]
    (is (= 400 status))
    (is (re-find error-regex body))))

(deftest translate-metadata
  (doseq [input-format valid-formats
          output-format valid-formats]
    (testing (format "Translating %s to %s" (name input-format) (name output-format))
      (let [input-str (umm-spec/generate-metadata :collection input-format expected-conversion/example-record)
            expected (expected-conversion/convert expected-conversion/example-record input-format output-format)
            {:keys [status headers body]} (ingest/translate-metadata :collection input-format input-str output-format)
            content-type (first (mt/extract-mime-types (:content-type headers)))]
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
          :collection :dif (umm-spec/generate-metadata :collection :dif10 expected-conversion/example-record) :umm-json))

      (testing "bad json"
        (assert-translate-failure #"object has missing required properties"
                                  :collection :umm-json "{}" :echo10)))))



(comment

  (do
    (def input :dif)
    (def output :dif)


    (def metadata (umm-spec/generate-metadata :collection input expected-conversion/example-record))

    (def parsed-from-metadata (umm-spec/parse-metadata :collection input metadata))

    (def metadata-regen (umm-spec/generate-metadata :collection output parsed-from-metadata))

    (def parsed-from-metadata-regen (umm-spec/parse-metadata :collection output metadata-regen))
    )

  (println metadata)

  (println metadata-regen)

  (= metadata-regen metadata)


  (println (:body (ingest/translate-metadata :collection :echo10 metadata :echo10)))

  (def expected (-> expected-conversion/example-record
                    (expected-conversion/convert input)
                    (expected-conversion/convert output)
                    ))

  )
