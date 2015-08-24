(ns cmr.ingest.api.translation
  "Defines an API for translating metadata between formats."
  (:require [compojure.core :refer :all]
            [cmr.common.mime-types :as mt]
            [cmr.umm-spec.core :as umm-spec]
            [cmr.common.services.errors :as errors]

            ;; Needed for development time random metadata generation
            [cmr.umm-spec.test.umm-generators :as umm-generators]
            [clojure.test.check.generators :as test-check-gen]))

(def concept-type->supported-formats
  "A map of concept type to the list of formats that are supported both for input and output of
  translation."
  {:collection #{mt/echo10
                 mt/umm-json
                 mt/iso19115
                 mt/dif
                 mt/dif10
                 mt/iso-smap}})

(defn translate
  "Fulfills the translate request using the body, content type header, and accept header. Returns
  a ring response with translated metadata."
  [context concept-type headers body-input]
  (let [body (slurp body-input)
        supported-formats (concept-type->supported-formats concept-type)
        output-mime-type (mt/extract-header-mime-type supported-formats headers "accept" true)
        output-format (mt/mime-type->format output-mime-type)
        input-mime-type (mt/extract-header-mime-type supported-formats headers "content-type" true)
        input-format (mt/mime-type->format input-mime-type)]

    ;; Validate the input data
    (if-let [errors (seq (umm-spec/validate-metadata concept-type input-format body))]
      (errors/throw-service-errors :bad-request errors)

      (let [umm (umm-spec/parse-metadata concept-type input-format body)
            output-str (umm-spec/generate-metadata concept-type output-format umm)]
        {:status 200
         :body output-str
         :headers {"Content-Type" output-mime-type}}))))

(def translation-routes
  (context "/translate" []
    (POST "/collection" {:keys [body headers request-context]}
      (translate request-context :collection headers body))
    ;; Granule translation is not supported yet. This will be done when granules are added to the UMM spec.
    ))

(def random-metadata-routes
  "This defines routes for development purposes that can generate random metadata and return it."
  (GET "/random-metadata" {:keys [body headers request-context]}
    (let [supported-formats (concept-type->supported-formats :collection)
          output-mime-type (mt/extract-header-mime-type supported-formats headers "accept" true)
          output-format (mt/mime-type->format output-mime-type)]

      (let [umm (test-check-gen/generate umm-generators/umm-c-generator)
            output-str (umm-spec/generate-metadata :collection output-format umm)]
        {:status 200
         :body output-str
         :headers {"Content-Type" output-mime-type}}))))