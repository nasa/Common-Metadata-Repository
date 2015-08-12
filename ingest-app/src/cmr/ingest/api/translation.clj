(ns cmr.ingest.api.translation
  "Defines an API for translating metadata between formats."
  (:require [compojure.core :refer :all]
            [cmr.common.mime-types :as mt]
            [cmr.umm-spec.core :as umm-spec]
            [cmr.common.services.errors :as errors]))

(def supported-formats
  "The list of formats that are supported both for input and output of translation."
  #{mt/echo10
    mt/umm-json
    mt/iso19115
    mt/dif
    mt/dif10
    mt/iso-smap})

(defn translate
  "Fulfills the translate request using the body, content type header, and accept header. Returns
  a ring response with translated metadata."
  [context concept-type headers body-input]
  (let [body (slurp body-input)
        output-mime-type (mt/extract-header-mime-type supported-formats headers "accept" true)
        output-format (mt/mime-type->format output-mime-type)
        input-mime-type (mt/extract-header-mime-type supported-formats headers "content-type" true)
        input-format (mt/mime-type->format input-mime-type)
        umm (umm-spec/parse-metadata concept-type input-format body)
        output-str (umm-spec/generate-metadata concept-type output-format umm)]
    ;; TODO validate input data against XML schema or JSON schema
    ;; I'll do this as another pull request
    {:status 200
     :body output-str
     :headers {"Content-Type" output-mime-type}}))

(def translation-routes
  (context "/translate" []
    (POST "/collection" {:keys [body headers request-context]}
      (translate request-context :collection headers body))
    ;; Granule translation is not supported yet. This will be done when granules are added to the UMM spec.
    ))

