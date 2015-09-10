(ns cmr.search.api.tags-api
  "Defines the API for tagging collections in the CMR."
  (:require [compojure.route :as route]
            [compojure.core :refer :all]
            [cheshire.core :as json]
            [cmr.common.mime-types :as mt]
            [cmr.common.validations.json-schema :as js]
            [cmr.search.services.tagging-service :as tagging-service]
            [cmr.common.services.errors :as errors]))

;; TODO add another schema for what's required or allowed when updating a tag

(def tag-schema
  "Schema for tags as json. Namespace and value are combined to with a separator character to be used
  as the native id when stored in metadata db. The maximum lengths of namespace and value are based
  on the maximum length of native id - 1 for the separator character."
  (js/parse-json-schema-from-string
    (json/generate-string
      {:$schema "http://json-schema.org/draft-04/schema#"
       :title "Tags"
       :type :object
       :properties {:namespace {:type :string
                                :minLength 1
                                :maxLength 514}
                    :value {:type :string
                            :minLength 1
                            :maxLength 515}
                    :category {:type :string
                               :minLength 1
                               :maxLength 1030}
                    :description {:type :string
                                  :minLength 1
                                  :maxLength 4000}}
       :required [:namespace :value]})))


(defn create-tag
  "Processes a create tag request."
  [context params headers body]
  ;; TODO validate that content type is json
  ;; TODO validate that accept header is JSON (maybe)

  (let [body (slurp body)]
    (when-let [errors (seq (js/validate-json tag-schema body))]
      (errors/throw-service-errors :bad-request errors))
    {:status 200
     :body (json/generate-string (tagging-service/create-tag context (json/parse-string body true)))
     :headers {"Content-Type" mt/json}}))

(def tag-api-routes
  (context "/tags" []
    ;; Create a new tag
    (POST "/" {:keys [request-context body params headers]}
      (create-tag request-context params headers body))))


