(ns cmr.ingest.api.translation
  (:require [compojure.core :refer :all]
            [cmr.common.mime-types :as mt]
            [cmr.umm-spec.core :as umm-spec]))

(def supported-formats
  #{mt/echo10
    mt/umm-json
    mt/iso19115
    mt/dif
    mt/dif10
    mt/iso-smap})

(defn translate
  [context concept-type headers body-input]
  (let [body (slurp body-input)
        ;; TODO test that output format is provided
        output-mime-type (mt/accept-mime-type headers supported-formats)
        output-format (mt/mime-type->format output-mime-type)
        ;; TODO test that content type is provided
        input-mime-type (mt/content-type-mime-type headers supported-formats)
        input-format (mt/mime-type->format input-mime-type)
        umm (umm-spec/parse-metadata concept-type input-format body)
        output-str (umm-spec/generate-metadata concept-type output-format umm)]
    ;; TODO validate formats and add integration tests

    ;; TODO validate input data against XML schema or JSON schema
    ;; I'll do this as another pull request
    {:status 200
     :body output-str
     :headers {"Content-Type" output-mime-type}}))

(def translation-routes
  (context "/translate" []
    (POST "/collection" {:keys [body headers request-context]}
      (translate request-context :collection headers body))

    ;; TODO granule translation

    ))

;; TODO think about integration tests and failure cases
;; - metadata does not match
