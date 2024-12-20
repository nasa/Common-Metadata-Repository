(ns cmr.search.api.association
  "Defines common functions used by associations with collections in the CMR."
  (:require
   [cheshire.core :as json]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common.log :refer (info)]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.search.services.association-service :as assoc-service]
   [cmr.search.services.association-validation :as assoc-validation]
   [compojure.core :refer :all]))

(defn- validate-association-content-type
  "Validates that content type sent with a association is JSON."
  [headers]
  (mt/extract-header-mime-type #{mt/json} headers "content-type" true))

(defn add-individual-statuses
  "When given a list of response entities, will set the http response status of each response entity based on its content.
  Ex) If the response entity contains an error message, will set the status as 400."
  [list]
  (map #(assoc % :status (if (or (:errors %) (:warning %)) 400 200))
       list))

(defn- api-response
  "Creates an association response with the given data response"
  ([status-code data]
   ;; For association responses that partially fail we want to return a 207 and
   ;; detail which associations failed and/or succeeded in the given body
   (let [data-value (if (= 207 status-code)
                      (add-individual-statuses data)
                      data)]
     {:status status-code
      :body (json/generate-string (util/snake-case-data data-value))
      :headers {"Content-Type" mt/json}})))

(defn num-errors-in-assoc-results
  "Counts the number of errors in association-results"
  [results]
  (count (filter :errors results)))

(defn association-results->status-code
  "Check for concept-types requiring error status to be returned.
  If the concept-type is error-sensitive the function will check for any errors in the results.
  Will return:
  - 200 OK -- if response has no errors
  - 207 MULTI-STATUS -- if response has some errors and some successes
  - 400 BAD REQUEST -- if response has all errors"
  [concept-type results]
  (if (some #{concept-type} '(:variable :service :tool))
    (let [result-count (count results)
          num-errors (num-errors-in-assoc-results results)]
      (if (= 0 result-count)
        200
        (if (= num-errors result-count)
          400
          (if (> num-errors 0)
            207
            200))))
    200))

(defn associate-concept-to-collections
  "Associate the given concept by concept type and concept id to a list of
  collections in the request body. User has to have update permission on INGEST_MANAGEMENT_ACL for the
  provider(s) of the collection(s) in order for the association(s) to be successful."
  [context headers body concept-type concept-id]
  (common-enabled/validate-write-enabled context "search")
  (validate-association-content-type headers)
  (info (format "Associate %s [%s] on collections: %s by client: %s."
                (name concept-type) concept-id body (:client-id context)))
  (if (and (> (count (assoc-validation/associations-json->associations body)) 1)
           (= :variable concept-type))
    (api-response
      400
      {:error "Only one collection allowed in the list because a variable can only be associated with one collection."})
    (let [results (assoc-service/associate-to-collections context concept-type concept-id body)
          status-code (association-results->status-code concept-type results)]
      (api-response status-code results))))

(defn dissociate-concept-from-collections
  "Dissociate the given concept by concept type and concept id from a list of
  collections in the request body."
  [context headers body concept-type concept-id]
  (common-enabled/validate-write-enabled context "search")
  (validate-association-content-type headers)
  (info (format "Dissociating %s [%s] from collections: %s by client: %s."
                (name concept-type) concept-id body (:client-id context)))
  (if (and (> (count (map :concept-id (json/parse-string body true))) 1)
           (= :variable concept-type)) 
    (api-response
      400
      {:error "Only one variable at a time may be dissociated."})
    (let [results (assoc-service/dissociate-from-collections context concept-type concept-id body)
          status-code (association-results->status-code concept-type results)]
      (api-response status-code results))))
