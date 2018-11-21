(ns cmr.system-int-test.ingest.granule-translation-test
  (:require
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.umm-spec.legacy :as umm-legacy]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.test.umm-g.expected-util :as expected-util]
   [cmr.umm-spec.umm-spec-core :as umm-spec]))

(def ^:private valid-formats
  [:umm-json
   :iso-smap
   :echo10])

(def test-context (lkt/setup-context-for-test))

(defn- assert-translate-failure
  [error-regex & args]
  (let [{:keys [status body]} (apply ingest/translate-metadata args)]
    (is (= 400 status))
    (is (re-find error-regex body))))

(defn- assert-invalid-data
  [error-regex & args]
  (let [{:keys [status body]} (apply ingest/translate-metadata args)]
    (is (= 422 status))
    (is (re-find error-regex body))))

(defn- umm->umm-for-comparison
  "Modifies the UMM record for comparison purpose, as not all fields are supported the same in
  between the different granule formats. This function is used marshall ECHO10 and UMM-G parsed
  umm-lib model for comparison. ISO SMAP has even less supported fields and will not use this."
  [gran]
  (-> gran
      (update-in [:spatial-coverage :geometries] set)
      ;; Need to remove the possible duplicate entries in crid-ids and feature-ids
      ;; because Identifiers in UMM-G v1.4 can't contain any duplicates.
      (as-> updated-umm (if (get-in updated-umm [:data-granule :crid-ids])
                          (assoc-in updated-umm [:data-granule :crid-ids] nil)
                          updated-umm))
      (as-> updated-umm (if (get-in updated-umm [:data-granule :feature-ids])
                          (assoc-in updated-umm [:data-granule :feature-ids] nil)
                          updated-umm))
      ;; RelatedUrls mapping between ECHO10 and UMM-G is different
      (assoc :related-urls nil)))

(deftest translate-granule-metadata
  (doseq [input-format valid-formats
          output-format valid-formats]
    (testing (format "Translating %s to %s" (name input-format) (name output-format))
      (let [input-str (umm-legacy/generate-metadata test-context expected-util/expected-sample-granule input-format)
            expected (umm->umm-for-comparison expected-util/expected-sample-granule)
            {:keys [status headers body]} (ingest/translate-metadata :granule input-format input-str output-format)
            content-type (first (mt/extract-mime-types (:content-type headers)))
            actual-parsed (umm-legacy/parse-concept
                           test-context {:concept-type :granule
                                         :format (mt/format->mime-type output-format)
                                         :metadata body})
            actual-parsed (umm->umm-for-comparison actual-parsed)]

        (is (= 200 status) body)
        (is (= (mt/format->mime-type output-format) content-type))
        (if (or (= :iso-smap input-format) (= :iso-smap output-format))
          ;; ISO SMAP umm-lib parsing and generation support is limited and the conversion
          ;; from/to it is lossy. So we only compare the GranuleUR for now, the rest of the
          ;; UMM fields will be added when ISO SMAP granule support is added.
          (is (= (:granule-ur expected) (:granule-ur actual-parsed)))
          (is (= expected actual-parsed))))))

  (testing "Failure cases"
    (testing "unsupported input format"
      (assert-translate-failure
       #"The mime types specified in the content-type header \[application/xml\] are not supported"
       :granule :xml "notread" :umm-json))

    (testing "not specified input format"
      (assert-translate-failure
       #"The mime types specified in the content-type header \[\] are not supported"
       :granule nil "notread" :umm-json))

    (testing "unsupported output format"
      (assert-translate-failure
       #"The mime types specified in the accept header \[application/xml\] are not supported"
       :granule :echo10 "notread" :xml))

    (testing "not specified output format"
      (assert-translate-failure
       #"The mime types specified in the accept header \[\] are not supported"
       :granule :echo10 "notread" nil))

    (testing "invalid metadata"
      (testing "bad xml"
        (assert-translate-failure
         #"Cannot find the declaration of element 'this'"
         :granule :echo10 "<this> is not good XML</this>" :umm-json))

      (testing "wrong xml format"
        (assert-translate-failure
         #"Cannot find the declaration of element 'Granule'"
         :granule :iso-smap (umm-legacy/generate-metadata
                             test-context expected-util/expected-sample-granule :echo10) :umm-json))

      (testing "bad json"
        (assert-translate-failure #"object has missing required properties"
                                  :granule :umm-json "{}" :echo10)))))
