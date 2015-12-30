(ns cmr.system-int-test.ingest.ops-collections-translation-test
  "Translate OPS collections into various supported metadata formats."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cmr.common.log :refer [error]]
            [cmr.common.mime-types :as mt]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.system-int-test.system :as s]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def valid-formats
  [:dif
   :dif10
   :echo10
   :iso19115
   :iso-smap
   ])

(def collection-search-url
  ; "http://localhost:3003/collections")
  "https://cmr.earthdata.nasa.gov/search/collections")

(defn- verify-translation
  [reference]
  (let [{:keys [id location]} reference
        response (client/get location
                             {:connection-manager (s/conn-mgr)})
        metadata (:body response)
        metadata-format (mt/mime-type->format (mt/content-type-mime-type (:headers response)))]
    (doseq [output-format (remove #{metadata-format} valid-formats)]
      (let [{:keys [status headers body]} (ingest/translate-metadata
                                            :collection metadata-format metadata output-format
                                            {:query-params {"skip_umm_validation" "true"}})
            response (client/request
                       {:method :post
                        :url (url/validate-url "PROV1" :collection "native-id")
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
                             (name metadata-format) (name output-format) id response)))))))))

(deftest ops-collections-translation
  (testing "Translate OPS collections into various supported metadata formats and make sure they pass ingest validation."
    (try
      (loop [page-num 1]
        (let [page-size 10
              {:keys [refs]} (search/parse-reference-response
                               false (client/get collection-search-url
                                                 {:query-params {:page_size page-size
                                                                 :page_num page-num}
                                                  :connection-manager (s/conn-mgr)}))]
          (doseq [ref refs]
            (verify-translation ref))
          (when (>= (count refs) page-size)
            (recur (+ page-num 1)))))
      (catch Throwable e
        (is false (format "ops-collections-translation failed. %s" e))))))
