(ns cmr.system-int-test.ingest.ops-collections-translation-test
  "Translate OPS collections into various supported metadata formats."
  (:require
    [clj-http.client :as client]
    [clojure.data.csv :as csv]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.test :refer :all]
    [cmr.common.concepts :as concepts]
    [cmr.common.log :refer [error info]]
    [cmr.common.mime-types :as mt]
    [cmr.common.xml :as cx]
    [cmr.system-int-test.system :as s]
    [cmr.system-int-test.utils.fast-xml :as fx]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]
    [cmr.system-int-test.utils.url-helper :as url]
    [cmr.umm-spec.json-schema :as json-schema]
    [cmr.umm-spec.test.location-keywords-helper :as lkt]
    [cmr.umm-spec.umm-json :as umm-json]
    [cmr.umm-spec.umm-spec-core :as umm]
    [cmr.umm-spec.validation.umm-spec-validation-core :as umm-validation])
  (:import
   (java.io StringWriter)))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def context (lkt/setup-context-for-test))

(def write-errors-to-file
  true)

(def CSV_FILENAME
  "ops_umm_translation_errors.csv")

(def CSV_HEADER
  ["Provider Id" "Concept Id" "Entry Title" "Errors"])

(def starting-page-num
  "The starting page-num to retrieve collections for the translation test."
  1)

(def translation-search-page-size
  "The page-size used to retrieve collections for the translation test."
  100)

(def validation-search-page-size
  "The page-size used to retrieve collections for the translation test."
  1000)

(def collection-search-url
  "https://cmr.earthdata.nasa.gov/search/collections.native")

(def skipped-collections
  "A set of collection concept-ids that will be skipped."
  #{;; The following collections have invalid Temporal_Coverage date.
    ;; GCMD has been notified and will fix the collections.
    "C1214613964-SCIOPS" ;;Temporal_Coverage Start_Date is 0000-07-01
    "C1215196994-NOAA_NCEI" ;;Temporal_Coverage Start_Date is 0000-01-01
    "C1214603072-SCIOPS" ;;The value '<http://www.bioone.org/doi/abs/10.1672/0277-5212%282006%2926%5B528%3AACFEAW%5D2.0.CO%3B2?journalCode=wetl>' of element 'Online_Resource' is not valid.
    "C1214613924-SCIOPS" ;;'0000-01-01T00:00:00.000Z' is not a valid value of union type 'DateOrTimeOrEnumType'
    "C1215196702-NOAA_NCEI" ;;Line 192 - cvc-datatype-valid.1.2.3: '0000-01-01T00:00:00.000Z' is not a valid value of union type 'DateOrTimeOrEnumType'.
    "C1214598321-SCIOPS" ;;Line 28 - cvc-datatype-valid.1.2.3: '0000-01-01T00:00:00.000Z' is not a valid value of union type 'DateOrTimeOrEnumType'.
    "C1214584397-SCIOPS" ;;Line 93 - cvc-datatype-valid.1.2.3: '0000-01-01T00:00:00.000Z' is not a valid value of union type 'DateOrTimeOrEnumType'.
    "C1215197043-NOAA_NCEI" ;;Line 53 - cvc-datatype-valid.1.2.3: '0000-01-01T00:00:00.000Z' is not a valid value of union type 'DateOrTimeOrEnumType'.
    "C1215196954-NOAA_NCEI" ;;Line 53 - cvc-datatype-valid.1.2.3: '0000-01-01T00:00:00.000Z' is not a valid value of union type 'DateOrTimeOrEnumType'.
    "C1214611120-SCIOPS" ;;Line 53 - cvc-datatype-valid.1.2.3: '0000-01-01T00:00:00.000Z' is not a valid value of union type 'DateOrTimeOrEnumType'.
    "C1214611119-SCIOPS" ;;Line 53 - cvc-datatype-valid.1.2.3: '0000-01-01T00:00:00.000Z' is not a valid value of union type 'DateOrTimeOrEnumType'.
    "C1214611056-SCIOPS" ;;Line 53 - cvc-datatype-valid.1.2.3: '0000-01-01T00:00:00.000Z' is not a valid value of union type 'DateOrTimeOrEnumType'.
    "C1215196983-NOAA_NCEI" ;;Line 53 - cvc-datatype-valid.1.2.3: '0000-01-01T00:00:00.000Z' is not a valid value of union type 'DateOrTimeOrEnumType'.
    "C1214598111-SCIOPS" ;;Line 53 - cvc-datatype-valid.1.2.3: '0000-01-01T00:00:00.000Z' is not a valid value of union type 'DateOrTimeOrEnumType'.
    "C1215201124-NOAA_NCEI" ;;Line 53 - cvc-datatype-valid.1.2.3: '0000-01-01T00:00:00.000Z' is not a valid value of union type 'DateOrTimeOrEnumType'.
    "C1215196987-NOAA_NCEI" ;;Line 53 - cvc-datatype-valid.1.2.3: '0000-01-01T00:00:00.000Z' is not a valid value of union type 'DateOrTimeOrEnumType'.
    "C1215197064-NOAA_NCEI" ;;Line 53 - cvc-datatype-valid.1.2.3: '0000-01-01T00:00:00.000Z' is not a valid value of union type 'DateOrTimeOrEnumType'.
    "C1215197019-NOAA_NCEI" ;;Line 53 - cvc-datatype-valid.1.2.3: '0000-01-01T00:00:00.000Z' is not a valid value of union type 'DateOrTimeOrEnumType'.
    "C1215196956-NOAA_NCEI" ;;Line 53 - cvc-datatype-valid.1.2.3: '0000-01-01T00:00:00.000Z' is not a valid value of union type 'DateOrTimeOrEnumType'.
    "C1215197100-NOAA_NCEI" ;;Line 53 - cvc-datatype-valid.1.2.3: '0000-01-01T00:00:00.000Z' is not a valid value of union type 'DateOrTimeOrEnumType'.
    "C1214613961-SCIOPS" ;;Line 53 - cvc-datatype-valid.1.2.3: '0000-01-01T00:00:00.000Z' is not a valid value of union type 'DateOrTimeOrEnumType'.
    "C1215196984-NOAA_NCEI" ;;Line 53 - cvc-datatype-valid.1.2.3: '0000-01-01T00:00:00.000Z' is not a valid value of union type 'DateOrTimeOrEnumType'.

    ;; The following collections have invalid URL data
    ;; GCMD has been notified and will fix the collections.
    "C1214603044-SCIOPS" ;;Online_Resource is not a valid value for 'anyURI'
    "C1214603622-SCIOPS" ;;'&lt;http://sofia.usgs.gov/projects/remote_sens/sflsatmap.html&gt;' is not a valid value for 'anyURI'
    "C1214603943-SCIOPS" ;;Line 208 - cvc-datatype-valid.1.2.1: '<http://sofia.usgs.gov/publications/papers/uranium_and_sulfur/>' is not a valid value for 'anyURI'.
    "C1214603330-SCIOPS" ;;Line 132 - cvc-datatype-valid.1.2.1: '<http://fresc.usgs.gov/products/ProductDetails.aspx?ProductNumber=1267>' is not a valid value for 'anyURI'.
    "C1214603037-SCIOPS" ;;Line 132 - cvc-datatype-valid.1.2.1: '<http://fresc.usgs.gov/products/ProductDetails.aspx?ProductNumber=1267>' is not a valid value for 'anyURI'.
    "C1214622601-ISRO" ;;Line 97 - cvc-datatype-valid.1.2.1: '218.248.0.134:8080/OCMWebSCAT/html/controller.jsp' is not a valid value for 'anyURI'.
    "C1214604063-SCIOPS" ;;Line 291 - cvc-datatype-valid.1.2.1: '<http://pubs.usgs.gov/of/2014/1040/data/undersea_features.zip>' is not a valid value for 'anyURI'.
    "C1214595389-SCIOPS" ;;Line 108 - cvc-datatype-valid.1.2.1: 'IPY  http://ipy.antarcticanz.govt.nz/projects/southern-victoria-land-geology/' is not a valid value for 'anyURI'.
    "C1214608487-SCIOPS" ;;Line 303 - cvc-datatype-valid.1.2.1: 'Scanned images: http://atlas.gc.ca/site/english/maps/archives' is not a valid value for 'anyURI'.
    "C1214603723-SCIOPS"}) ;;Line 132 - cvc-datatype-valid.1.2.1: '<http://sofia.usgs.gov/projects/workplans12/jem.html>' is not a valid value for 'anyURI'.

    ;; The following collections failed ingest validation, but not xml schema validation.
    ;; I have changed the validation from ingest validation to xml validation, so we don't
    ;; need to skip them now.
    ;; All these collections failed ingest validation due to discrepancy between umm-lib and
    ;; umm-spec-lib. i.e. the iso-smap tiling is not supported in umm-lib. We have decided to push
    ;; this off until later. It will become obsolete once we switch to umm-spec-lib for ingest
    ;; validation. See CMR-1869.
    ; "C4695156-LARC_ASDC"
    ; "C5520300-LARC_ASDC"
    ; "C7244490-LARC_ASDC"
    ; "C1000000240-LARC_ASDC"
    ; "C5511253-LARC_ASDC"
    ; "C7146790-LARC_ASDC"
    ; "C7271330-LARC_ASDC"
    ; "C1000000260-LARC_ASDC"
    ; "C5920490-LARC_ASDC"
    ; "C5784291-LARC_ASDC"
    ; "C7092790-LARC_ASDC"
    ; "C4695163-LARC_ASDC"
    ; "C5784292-LARC_ASDC"
    ; "C7299610-LARC_ASDC"
    ; "C1000000300-LARC_ASDC"
    ; "C5862870-LARC_ASDC"
    ; "C5784310-LARC_ASDC"



(def valid-formats
  "Valid metadata formats that is supported by CMR."
  [:dif :dif10 :echo10 :iso19115 :iso-smap])

(defn- verify-translation-via-ingest-validation
  "Verify the given collection can be translated into other metadata formats and the translated
  metadata passes ingest validation. It will throw exception when the translated metadata fails
  the ingest validation."
  [reference]
  (let [{:keys [metadata-format metadata concept-id]} reference]
    (when-not (skipped-collections concept-id)
      (doseq [output-format (remove #{metadata-format} valid-formats)]
        (let [{:keys [status headers body]} (ingest/translate-metadata
                                              :collection metadata-format metadata output-format
                                              {:query-params {"skip_umm_validation" "true"}})
              response (client/request
                         {:method :post
                          :url (url/validate-url "PROV1" :collection concept-id)
                          :body  body
                          :content-type (mt/format->mime-type output-format)
                          :headers {"Cmr-Validate-Keywords" false}
                          :throw-exceptions false
                          :connection-manager (s/conn-mgr)})]
          (is (= 200 (:status response))
              (format "Failed validation when translating %s to %s for collection %s. %s"
                      (name metadata-format) (name output-format) concept-id response)))))))

(defn- verify-translation-via-schema-validation
  "Verify the given collection can be translated into other metadata formats and the translated
  metadata passes the xml schema validation. It will throw exception when the translated metadata
  fails the schema validation."
  [reference]
  (let [{:keys [metadata-format metadata concept-id]} reference]
    (when-not (skipped-collections concept-id)
      (doseq [output-format (remove #{metadata-format} valid-formats)]
        (let [{:keys [status headers body]} (ingest/translate-metadata
                                              :collection metadata-format metadata output-format
                                              {:query-params {"skip_umm_validation" "true"}})]
          (when-let [validation-errs (umm/validate-xml :collection output-format body)]
            (is false
                (format "Failed xml schema validation when translating %s to %s for collection %s. %s"
                        (name metadata-format) (name output-format) concept-id validation-errs))))))))


(defn- translate-record-to-umm
  "Translate collection from native format to UMM-C"
  [record]
  (let [{:keys [metadata-format metadata concept-id]} record]
    (umm/parse-metadata context :collection metadata-format metadata {:sanitize? false})))

(defn- validate-record-schemas
  "Validate a record against the XML schema if applicable and UMM JSON schema. For records in umm-json
  validate the json schema against the collection's schema version"
  [record]
  (let [{:keys [metadata-format metadata]} record]
    (if (= :umm-json (:format metadata-format))
      (json-schema/validate-umm-json metadata :collection (:version metadata-format))
      (do
        (umm/validate-xml :collection metadata-format metadata)
        (json-schema/validate-umm-json (umm-json/umm->json (:collection record)) :collection)))))

(defn- extract-errors-from-collection
  "During collection translation from native XML to UMM, errors are put directly on the fields that
  cannot be translated with the :_errors key. Walk through the collection and pull out the errors
  so they can be reported."
  [collection]
  (remove empty?
          (apply concat
                 [(:_errors collection)]
                 (for [key (keys collection)
                       :let [coll (get collection key)]
                       result (cond
                                (map? coll) (extract-errors-from-collection coll)
                                (coll? coll) (for [entry coll
                                                   :when (map? entry)]
                                               (extract-errors-from-collection entry)))]
                   result))))

(defn- reformat-error-message
  "For a 'string too long' error, just print out what string it is and the size so that
  the test output does not get bloated with the full string"
  [error]
  (if-let [result (re-find #".* string .*is too long \(length: \d+, maximum allowed: \d+\)" (str/replace error "\n" ""))]
    (let [string-index (str/index-of result "string")
          length-index (str/index-of result "is too long (length:")]
      (str (subs result 0 (+ 6 string-index)) " " (subs result length-index)))
    error))

(defn- get-collection-validation-errors
  "Collect the following collection validations:
   * Errors from translating from native XML to UMM-C
   * Validate metadata against the XML schema
   * Validation the UMM collection record against the current UMM JSON schema
   * Valiadte the UMM collection against UMM collection rules
  Return a list of validation errors."
  [record]
  (remove empty?
   (concat
     (extract-errors-from-collection (:collection record)) ; Errors from translating to UMM
     (map reformat-error-message (validate-record-schemas record))
     (umm-validation/validate-collection (:collection record)))))

(defn- translate-and-validation-collection
  "For a search result record, translate the metadata to UMM, validate the metadata and UMM record
  and return a validation result record including the concept id, provider id, entry title, and
  all validation errors for the record. If an exception occurs, report that."
  [record]
  (try
    (let [record (assoc record :collection (translate-record-to-umm record))
          validation-errors (get-collection-validation-errors record)
          concept-id (:concept-id record)]
      (when (seq validation-errors)
        {:concept-id concept-id
         :provider-id (:provider-id (concepts/parse-concept-id concept-id))
         :entry-title (get-in record [:collection :EntryTitle])
         :errors validation-errors}))
    (catch Exception e
      (let [concept-id (:concept-id record)]
        {:concept-id concept-id
         :provider-id (:provider-id (concepts/parse-concept-id concept-id))
         :entry-title (get-in record [:collection :EntryTitle])
         :errors [e]}))))

(defn get-collection
  "Get a collection by concept-id for debugging purposes"
  [concept-id]
  (search/get-search-failure-xml-data
   (let [response (client/get collection-search-url
                              {:query-params {:concept-id concept-id}
                               :connection-manager (s/conn-mgr)})
         body (:body response)
         parsed (fx/parse-str body)
         metadatas (for [match (drop 1 (str/split body #"(?ms)<result "))]
                     (second (re-matches #"(?ms)[^>]*>(.*)</result>.*" match)))
         result (first (cx/elements-at-path parsed [:result]))
         metadata (first metadatas)
         {{:keys [concept-id revision-id format]} :attrs} result
         metadata-format (mt/mime-type->format format)]
     {:concept-id concept-id
      :revision-id (when revision-id (Long. ^String revision-id))
      :metadata-format metadata-format
      :metadata metadata})))

(defn get-collections
  "Returns the collections as a list of maps with concept-id, revision-id, metadata-format and metadata."
  [page-size page-num]
  (search/get-search-failure-xml-data
    (let [response (client/get collection-search-url
                               {:query-params {:page_size page-size
                                               :page_num page-num}
                                :connection-manager (s/conn-mgr)})
          body (:body response)
          parsed (fx/parse-str body)
          metadatas (for [match (drop 1 (str/split body #"(?ms)<result "))]
                      (second (re-matches #"(?ms)[^>]*>(.*)</result>.*" match)))]
      (map (fn [result metadata]
             (let [{{:keys [concept-id revision-id format]} :attrs} result
                   metadata-format (mt/mime-type->format format)]
               {:concept-id concept-id
                :revision-id (when revision-id (Long. ^String revision-id))
                :metadata-format metadata-format
                :metadata metadata}))
           (cx/elements-at-path parsed [:result])
           metadatas))))

(defn- get-ops-collections-umm-validation-errors
  "Loops through the collections in ops with a paged search. For each collection, validate the
  metadata against the XML schema, validate the collection translated to UMM against the current
  UMM JSON schema, and validate the UMM collection against the validation rules.
  Return a list of a list of validation errors for each page processed to view by page
  and make error analysis more manageable."
  []
  (loop [page-num starting-page-num results []]
    (let [collections (get-collections validation-search-page-size page-num)
          error-results (remove nil? (map translate-and-validation-collection collections))
          all-results (conj results error-results)]
      (info "Processed Page " page-num " " (count error-results) " errors")
      (if (>= (count collections) validation-search-page-size)
        (recur (+ page-num 1) all-results)
        all-results))))

;; Comment out this test so that it will not be run as part of the build.
#_(deftest ops-collections-translation
   (testing "Translate OPS collections into various supported metadata formats and make sure they pass validation."
     (loop [page-num starting-page-num]
       (let [colls (get-collections translation-search-page-size page-num)]
         (info "Translating collections on page-num: " page-num)
         (doseq [coll colls]
           (verify-translation-via-schema-validation coll))
         ;; We will turn on ingest validation later when ingest is backed by umm-spec-lib
         ; (verify-translation-via-ingest-validation coll))
         (when (>= (count colls) translation-search-page-size)
           (recur (+ page-num 1)))))
     (info "Finished OPS collections translation.")))

(defn- validation-result->row
  "Take a validation result and return a row formatted for CSV"
  [result]
  (let [{:keys [provider-id concept-id entry-title errors]} result
        error-string (str/join "; " errors)]
    [provider-id concept-id entry-title error-string]))

#_(deftest ops-collections-validation
   (testing "get-collections the current collections in ops against the current UMM schema"
     (def results (get-ops-collections-umm-validation-errors))
     (let [rows (cons CSV_HEADER
                      (map validation-result->row (flatten results)))
           string-writer (StringWriter.)]
       (if write-errors-to-file
         (with-open [out-file (io/writer CSV_FILENAME)]
           (csv/write-csv out-file rows))
         (do
           (csv/write-csv string-writer rows)
           (def error-csv (str string-writer)))))
    (info "Finished OPS collections translation.")))

(comment
 ;; Translate and validate a specific collection by concept-id
 (def record (get-collection "C1000000320-SEDAC"))
 (translate-and-validation-collection record)
 (translate-record-to-umm record)
 (:metadata-format record)
 (:metadata record))
