(ns cmr.collection-renderer.api.routes
  "Defines routes for fetching resources used in the Collection HTML"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [cmr.common.services.errors :as errors]
   [compojure.core :refer :all]))

(def cmr-metadata-preview-gem
  "Define the cmr_metadata_preview gem name. Update this when a new version of the gem is created."
  "cmr_metadata_preview-0.0.1")

(def assets-path
  "Defines path to cmr_metadata_preview gem assets"
  (format "gems/%s/app/assets" cmr-metadata-preview-gem))

(defn- resource-or-not-found
  "Returns a URL to the resource on the classpath or throws a not found error"
  [resource]
  (or (io/resource resource)
      (errors/throw-service-error :not-found
                                  (str "Could not find " resource))))

(defn- replace-relative-root-url
  "Replaces any occurences of %RELATIVE_ROOT_URL% with the applications relative root url"
  [system content]
  (str/replace content "%RELATIVE_ROOT_URL%" (get-in system [:public-conf :relative-root-url])))

(defn resource-routes
  "Defines routes for returning resources used in the generated collection HTML"
  [system]
  (routes
    (context "/javascripts" []
      (GET "/:resource" {{resource :resource} :params}
        {:status 200
         :headers {"content-type" "application/javascript"}
         :body (slurp (resource-or-not-found
                       (str assets-path "/javascripts/cmr_metadata_preview/" resource)))}))
    (context "/stylesheets" []
      (GET "/:resource" {{resource :resource} :params}
        {:status 200
         :headers {"content-type" "text/css"}
         :body
         (replace-relative-root-url
          system
          (slurp (resource-or-not-found
                  (str assets-path "/stylesheets/cmr_metadata_preview/" resource))))}))
    (context "/images/cmr_metadata_preview" []
      (GET "/:resource" {{resource :resource} :params}
        {:status 200
         :headers {"content-type" (str "image/" (last (str/split resource #"\.")))}
         :body (io/input-stream
                (resource-or-not-found (str assets-path "/images/cmr_metadata_preview/" resource)))}))
    (context "/assets/cmr_metadata_preview/ed-images" []
      (GET "/:resource" {{resource :resource} :params}
        {:status 200
         :headers {"content-type" (str "image/" (last (str/split resource #"\.")))}
         :body (io/input-stream
                (resource-or-not-found
                 (str assets-path "/images/cmr_metadata_preview/ed-images/" resource)))}))))
