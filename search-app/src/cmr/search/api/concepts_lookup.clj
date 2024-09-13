(ns cmr.search.api.concepts-lookup
  "Defines the API for concepts lookup in the CMR."
  (:require
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common.concepts :as concepts]
   [cmr.common.log :refer (info)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as svc-errors]
   [cmr.search.api.core :as core-api]
   [cmr.search.services.query-service :as query-svc]
   [cmr.search.site.pages :as pages]
   [compojure.core :refer [GET OPTIONS context]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Constants

(def find-by-concept-id-concept-types
  (concat #{:collection :granule :service :tool :variable :subscription} (concepts/get-generic-concept-types-array))) 

(defn generate-generic-supported-concept-id-retrieval-mime-types
  "Generates the mimetypes that can be retrieved for generic documents"
  []
  (zipmap (concepts/get-generic-concept-types-array) (repeat #{mt/any
                                                               mt/xml
                                                               mt/umm-json})))

(def supported-concept-id-retrieval-mime-types
  (merge 
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
                 mt/legacy-umm-json
                 mt/stac}
   :granule #{mt/any
              mt/xml    ; allows retrieving native format
              mt/native ; retrieve in native format
              mt/atom
              mt/json
              mt/echo10
              mt/iso19115
              mt/iso-smap
              mt/umm-json
              mt/stac}
   :service #{mt/any
              mt/html
              mt/xml
              mt/umm-json}
   :tool #{mt/any
           mt/html
           mt/xml
           mt/umm-json}
   :subscription #{mt/any
                   mt/xml
                   mt/umm-json}
   :variable #{mt/any
               mt/html
               mt/xml
               mt/umm-json}}
   (generate-generic-supported-concept-id-retrieval-mime-types)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Support Functions

(defn- find-concept-by-concept-id*
  "Perfrom the retrieval of concept by concept id and revision id"
  ([ctx result-format concept-id]
   (info (format "Search for concept with cmr-concept-id [%s] and result format [%s]" concept-id result-format))
   (core-api/search-response
    ctx
    (query-svc/find-concept-by-id ctx result-format concept-id)))

  ([ctx result-format concept-id revision-id]
   (info (format "Search for concept with cmr-concept-id [%s] and revision-id [%s] and result format [%s]"
                 concept-id
                 revision-id
                 result-format))
   (core-api/search-response
    ctx
    (query-svc/find-concept-by-id-and-revision ctx result-format concept-id revision-id))))

(defn- find-concept-by-cmr-concept-id
  "Invokes query service to find concept metadata by cmr concept id (and
  possibly revision id) and returns the response"
  [ctx path-w-extension headers]
  (let [concept-id (core-api/path-w-extension->concept-id path-w-extension)
        revision-id (core-api/path-w-extension->revision-id path-w-extension)
        concept-type (concepts/concept-id->type concept-id)
        concept-type-supported (supported-concept-id-retrieval-mime-types concept-type)]

    (when-not (some #(= % concept-type) find-by-concept-id-concept-types)
      (svc-errors/throw-service-error
       :bad-request
       (format (str "Retrieving concept by concept id is not supported for "
                    "concept type [%s].")
               (name concept-type))))

    (let [supported-mime-types (if revision-id
                                 ;; We don't support Atom or JSON (yet) for lookups that include
                                 ;; revision-id due to limitations of the current transformer
                                 ;; implementation. This will be fixed with CMR-1935.
                                 (disj concept-type-supported mt/atom mt/json mt/stac)
                                 concept-type-supported)
          result-format (core-api/get-search-results-format
                         concept-type
                         path-w-extension
                         headers
                         supported-mime-types
                         mt/native)
          ;; XML means native in this case
          result-format (if (= result-format :xml) :native result-format)]
      (if (= :html result-format)
        (let [concept-type (concepts/concept-id->type concept-id)]
          (condp = concept-type
            :service
            (core-api/search-response ctx
                                      {:results (:body (pages/service-page ctx concept-id))
                                       :result-format :html})
            :tool
            (core-api/search-response ctx
                                      {:results (:body (pages/tool-page ctx concept-id))
                                       :result-format :html})
            :variable
            (core-api/search-response ctx
                                      {:results (:body (pages/variable-page ctx concept-id))
                                       :result-format :html})
            (core-api/search-response ctx
                                      {:results (:body (pages/collection-page ctx concept-id))
                                       :result-format :html})))
        (if revision-id
          (find-concept-by-concept-id* ctx result-format concept-id revision-id)
          (find-concept-by-concept-id* ctx result-format concept-id))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Route Definitions

(def concepts-routes
  ;; Retrieve by cmr concept id or concept id and revision id
  ;; Matches URL paths of the form /concepts/:concept-id[/:revision-id][.:format],
  ;; e.g., http://localhost:3003/concepts/C120000000-PROV1,
  ;;       http://localhost:3003/concepts/C120000000-PROV1/2
  ;;       http://localohst:3003/concepts/C120000000-PROV1/2.xml
  (context ["/concepts/:path-w-extension" :path-w-extension #"[A-Z][A-Z]?[A-Z]?[0-9]+-[0-9A-Z_]+.*"] [path-w-extension]
    ;; OPTIONS method is needed to support CORS when custom headers are used in requests to
    ;; the endpoint. In this case, the Authorization header is used in the GET request.
    #_{:clj-kondo/ignore [:unused-binding]}
    (OPTIONS "/" req (common-routes/options-response))
    (GET "/"
      {headers :headers ctx :request-context}
      (find-concept-by-cmr-concept-id ctx path-w-extension headers))))
