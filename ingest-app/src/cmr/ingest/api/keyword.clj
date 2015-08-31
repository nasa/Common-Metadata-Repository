(ns cmr.ingest.api.keyword
  "Defines the HTTP URL routes for the keyword endpoint in the ingest application."
  (:require [compojure.core :refer :all]
            [cheshire.core :as json]
            [cmr.common-app.services.kms-fetcher :as kf]
            [cmr.transmit.kms :as kms]
            [cmr.common.mime-types :as mt]
            [cmr.common.services.errors :as errors]))

(defn- validate-keyword-scheme
  "Throws a service error if the provided keyword-scheme is invalid."
  [keyword-scheme]
  (when-not (some? (keyword-scheme kms/keyword-scheme->field-names))
    (errors/throw-service-error
      :bad-request (format "The keyword scheme [%s] is not supported. Valid schemes are: %s"
                           (name keyword-scheme)
                           (pr-str (map name (keys kms/keyword-scheme->field-names)))))))

(def keyword-api-routes
  (context "/keywords" []
    ;; Return a map of keyword-schemes to the list of keywords for each scheme
    (GET "/" {:keys [request-context]}
      (let [keywords (into {}
                           (for [[keyword-scheme short-name-value-maps]
                                 (kf/get-gcmd-keywords-map request-context)]
                             [keyword-scheme (vals short-name-value-maps)]))]
        {:status 200
         :headers {"Content-Type" (mt/format->mime-type :json)}
         :body (json/generate-string keywords)}))

    ;; Return a list of keywords for the given scheme
    (GET "/:keyword-scheme" {{:keys [keyword-scheme] :as params} :params
                             request-context :request-context}
      (let [keyword-scheme (keyword keyword-scheme)]
        (validate-keyword-scheme keyword-scheme)
        (let [keywords (vals (keyword-scheme (kf/get-gcmd-keywords-map request-context)))]
          {:staus 200
           :headers {"Content-Type" (mt/format->mime-type :json)}
           :body (json/generate-string keywords)})))))


