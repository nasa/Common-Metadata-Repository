(ns cmr.search.api.concepts-lookup
  "Defines the API for concepts lookup in the CMR."
  (:require
   ;; XXX REMOVE the next require once the service and associations work is complete
   [cmr-edsc-stubs.core :as stubs]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common.concepts :as concepts]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as svc-errors]
   [cmr.search.api.core :as core-api]
   [cmr.search.services.query-service :as query-svc]
   [compojure.core :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Constants

(def find-by-concept-id-concept-types
  #{:collection :granule :variable})

(def supported-concept-id-retrieval-mime-types
  {:collection #{mt/any
                 mt/html
                 mt/xml    ; allows retrieving native format
                 mt/native ; retrieve in native format
                 mt/atom
                 mt/json
                 mt/echo10
                 mt/iso19115
                 mt/iso-smap
                 mt/dif
                 mt/dif10
                 mt/umm-json
                 mt/legacy-umm-json}
   :granule #{mt/any
              mt/xml    ; allows retrieving native format
              mt/native ; retrieve in native format
              mt/atom
              mt/json
              mt/echo10
              mt/iso19115
              mt/iso-smap}
   :variable #{mt/any
               mt/xml
               mt/umm-json}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Support Functions

;; XXX too much nested functionality here; this should be refactored into
;;     additional supporting functions
(defn- find-concept-by-cmr-concept-id
  "Invokes query service to find concept metadata by cmr concept id (and
  possibly revision id) and returns the response"
  [ctx path-w-extension params headers]
  (let [concept-id (core-api/path-w-extension->concept-id path-w-extension)
        revision-id (core-api/path-w-extension->revision-id path-w-extension)
        concept-type (concepts/concept-id->type concept-id)
        concept-type-supported (supported-concept-id-retrieval-mime-types concept-type)]
    (when-not (contains? find-by-concept-id-concept-types concept-type)
      (svc-errors/throw-service-error
        :bad-request
        (format (str "Retrieving concept by concept id is not supported for "
                     "concept type [%s].")
                (name concept-type))))

    (if revision-id
      ;; We don't support Atom or JSON (yet) for lookups that include
      ;; revision-id due to limitations of the current transformer
      ;; implementation. This will be fixed with CMR-1935.
      (let [supported-mime-types (disj concept-type-supported mt/atom mt/json)
            result-format (core-api/get-search-results-format
                           concept-type
                           path-w-extension
                           headers
                           supported-mime-types
                           mt/native)
            ;; XML means native in this case
            result-format (if (= result-format :xml) :native result-format)]
        (info (format "Search for concept with cmr-concept-id [%s] and revision-id [%s]"
                      concept-id
                      revision-id))
        ;; else, revision-id is nil
        (core-api/search-response
         ctx
         (query-svc/find-concept-by-id-and-revision
          ctx
          result-format
          concept-id
          revision-id)))
      (let [result-format (core-api/get-search-results-format
                           concept-type
                           path-w-extension
                           headers
                           concept-type-supported
                           mt/native)
            ;; XML means native in this case
            result-format (if (= result-format :xml) :native result-format)]
        (info (format "Search for concept with cmr-concept-id [%s]" concept-id))
        (core-api/search-response
         ctx (query-svc/find-concept-by-id ctx result-format concept-id))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Route Definitions

(def concepts-routes
  ;; Retrieve by cmr concept id or concept id and revision id
  ;; Matches URL paths of the form /concepts/:concept-id[/:revision-id][.:format],
  ;; e.g., http://localhost:3003/concepts/C120000000-PROV1,
  ;;       http://localhost:3003/concepts/C120000000-PROV1/2
  ;;       http://localohst:3003/concepts/C120000000-PROV1/2.xml
  (context ["/concepts/:path-w-extension" :path-w-extension #"[A-Z][A-Z]?[0-9]+-[0-9A-Z_]+.*"] [path-w-extension]
    ;; OPTIONS method is needed to support CORS when custom headers are used in requests to
    ;; the endpoint. In this case, the Echo-Token header is used in the GET request.
    (OPTIONS "/" req common-routes/options-response)
    (GET "/"
      {params :params headers :headers ctx :request-context}
      ;; XXX REMOVE this check and the stubs once the service and
      ;;     the associations work is complete
      (if (headers "cmr-prototype-umm")
        (stubs/handle-prototype-request
         path-w-extension params headers)
        (find-concept-by-cmr-concept-id
         ctx path-w-extension params headers)))))
