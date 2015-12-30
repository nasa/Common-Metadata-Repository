(ns cmr.system-int-test.ingest.ops-collections-translation-test
  "Translate OPS collections into various supported metadata formats."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cmr.common.xml :as cx]
            [cmr.system-int-test.utils.fast-xml :as fx]
            [clojure.string :as str]
            [cmr.common.log :refer [error info]]
            [cmr.common.mime-types :as mt]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.system-int-test.system :as s]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def starting-page-num
  "The starting page-num to retrieve collections for the translation test."
  1)

(def search-page-size
  "The page-size used to retrieve collections for the translation test."
  50)

(def collection-search-url
  ; "http://localhost:3003/collections")
  "https://cmr.earthdata.nasa.gov/search/collections.native")

(def skipped-collections
  "A set of collection concept-ids that will be skipped."
  #{"C1214613964-SCIOPS" ;;Temporal_Coverage Start_Date is 0000-07-01
    "C1214603044-SCIOPS" ;;Online_Resource is not a valid value for 'anyURI'
    "C1215196994-NOAA_NCEI" ;;Temporal_Coverage Start_Date is 0000-01-01

    ;; The following ASF collections all failed with similar spatial validation errors:
    ;; <errors><error><path>SpatialCoverage/Geometries/0</path><errors><error>Spatial validation error: The shape contained duplicate points. Points 1 [lon=-83.8365106476704 lat=10.2523150519645] and 17 [lon=-83.8365106476704 lat=10.2523150519645] were considered equivalent or very close.</error></errors></error></errors>
    ;; CMR-2330
    "C1214421256-ASF"
    "C1214447436-ASF"
    "C1214447971-ASF"
    "C1214435173-ASF"
    "C1214435382-ASF"
    "C1214435672-ASF"
    "C1214435901-ASF"
    "C1214434814-ASF"
    "C1214434985-ASF"
    "C1214439292-ASF"
    "C1214445358-ASF"
    "C1214421468-ASF"
    "C1214434220-ASF"
    "C1214449277-ASF"
    "C1214450717-ASF"
    "C179001728-ASF"
    "C1213921626-ASF"
    "C1213921661-ASF"
    "C1213928843-ASF"
    "C1213927035-ASF"
    "C179001730-ASF"
    "C1213925022-ASF"
    "C1213926419-ASF"
    "C1213926777-ASF"
    "C1213927939-ASF"
    "C1213928209-ASF"

    })

(def valid-formats
  [:dif :dif10 :echo10 :iso19115 :iso-smap])

(defn- verify-translation
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
          (when-not (= 200 (:status response))
            (do
              (error "Failed validation: " response)
              (throw (Exception.
                       (format "Failed validation when translating %s to %s for collection %s. %s"
                               (name metadata-format) (name output-format) concept-id response))))))))))

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
                :metadata (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" metadata)}))
           (cx/elements-at-path parsed [:result])
           metadatas))))

(deftest ops-collections-translation
  (testing "Translate OPS collections into various supported metadata formats and make sure they pass ingest validation."
    (try
      (loop [page-num starting-page-num]
        (let [colls (get-collections search-page-size page-num)]
          (info "Translating collections on page-num: " page-num)
          (doseq [coll colls]
            (verify-translation coll))
          (when (>= (count colls) search-page-size)
            (recur (+ page-num 1)))))
      (info "Finished OPS collections translation.")
      (catch Throwable e
        (is false (format "ops-collections-translation failed. %s" e))))))

