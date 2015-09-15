(ns cmr.search.api.tags-api
  "Defines the API for tagging collections in the CMR."
  (:require [compojure.route :as route]
            [compojure.core :refer :all]
            [cheshire.core :as json]
            [cmr.common.mime-types :as mt]
            [cmr.common.validations.json-schema :as js]
            [cmr.search.services.tagging-service :as tagging-service]
            [cmr.common.services.errors :as errors]))

(def ^:private base-tag-schema-structure
  "Base Schema for tags as json. Namespace and value are combined to with a separator character to
  be used as the native id when stored in metadata db. The maximum lengths of namespace and value
  are based on the maximum length of native id - 1 for the separator character."
  {:type :object
   :additionalProperties false
   :properties {:namespace {:type :string :minLength 1 :maxLength 514}
                :value {:type :string :minLength 1 :maxLength 515}
                :category {:type :string :minLength 1 :maxLength 1030}
                :description {:type :string :minLength 1 :maxLength 4000}}
   :required [:namespace :value]})

(def ^:private create-tag-schema
  "The JSON schema used to validate tag creation requests"
  (js/parse-json-schema base-tag-schema-structure))

(def ^:private update-tag-schema
  "The JSON schema used to update update tag requests. Update requests are allowed to specify the
  originator id. They can't change it but it's allowed to be passed in because the tag fetch response
  will include it."
  (js/parse-json-schema (assoc-in base-tag-schema-structure [:properties :originator-id]
                                  {:type :string})))

(defn- validate-tag-content-type
  "Validates that content type sent with a tag is JSON"
  [headers]
  (mt/extract-header-mime-type #{mt/json} headers "content-type" true))

(defn- validate-tag-json
  "Validates the tag JSON string against the given tag schema. Throws a service error if it is invalid."
  [tag-schema json-str]
  (when-let [errors (seq (js/validate-json tag-schema json-str))]
    (errors/throw-service-errors :bad-request errors)))

(defn- tag-api-response
  "Creates a successful tag response with the given data response"
  ([data]
   (tag-api-response data true))
  ([data encode?]
   {:status 200
    :body (if encode? (json/generate-string data) data)
    :headers {"Content-Type" mt/json}}))

(defn create-tag
  "Processes a create tag request."
  [context headers body]
  (validate-tag-content-type headers)
  (validate-tag-json create-tag-schema body)
  (->> (json/parse-string body true)
       (tagging-service/create-tag context)
       tag-api-response))

(defn get-tag
  "Retrieves the tag with the given concept-id."
  [context concept-id]
  (-> (tagging-service/get-tag context concept-id)
      ;; We don't return the associated collection ids on the fetch response.
      ;; This could be changed if we wanted to. It's just not part of the requirements.
      (dissoc :associated-collection-ids)
      tag-api-response))

(defn update-tag
  "Processes a request to update a tag."
  [context headers body concept-id]
  (validate-tag-content-type headers)
  (validate-tag-json update-tag-schema body)
  (->> (json/parse-string body true)
       (tagging-service/update-tag context concept-id)
       tag-api-response))

(defn delete-tag
  "Deletes the tag with the given concept-id."
  [context concept-id]
  (tag-api-response (tagging-service/delete-tag context concept-id)))

(defn associate-tag
  "Processes a request to associate a tag."
  [context headers body concept-id]
  (validate-tag-content-type headers)

  (tag-api-response (tagging-service/associate-tag context concept-id body)))

(defn disassociate-tag
  "Processes a request to disassociate a tag."
  [context headers body concept-id]
  (validate-tag-content-type headers)

  (tag-api-response (tagging-service/disassociate-tag context concept-id body)))

(defn search-for-tags
  [context params]
  (tag-api-response (tagging-service/search-for-tags context params) false))

(def tag-api-routes
  (context "/tags" []

    ;; Create a new tag
    (POST "/" {:keys [request-context headers body]}
      (create-tag request-context headers (slurp body)))

    ;; Search for tags
    (GET "/" {:keys [request-context params]}
      (search-for-tags request-context params))

    (context "/:tag-id" [tag-id]

      ;; Get a tag
      (GET "/" {:keys [request-context]}
        (get-tag request-context tag-id))

      ;; Delete a tag
      (DELETE "/" {:keys [request-context]}
        (delete-tag request-context tag-id))

      ;; Update a tag
      (PUT "/" {:keys [request-context headers body]}
        (update-tag request-context headers (slurp body) tag-id))

      (context "/associations" []
        ;; Associate a tag with collections
        (POST "/" {:keys [request-context headers body]}
          (associate-tag request-context headers (slurp body) tag-id))

        ;; Disassociate a tag with collections
        (DELETE "/" {:keys [request-context headers body]}
          (disassociate-tag request-context headers (slurp body) tag-id))))))


