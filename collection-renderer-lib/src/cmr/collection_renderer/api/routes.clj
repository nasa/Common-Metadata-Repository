(ns cmr.collection-renderer.api.routes
  "Defines routes for fetching resources used in the Collection HTML"
  (require [compojure.core :refer :all]
           [cmr.common.services.errors :as errors]
           [clojure.string :as str]
           [clojure.java.io :as io]))

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
         :body (slurp (resource-or-not-found (str "public/javascripts/" resource)))}))

    (context "/stylesheets" []
      (GET "/:resource" {{resource :resource} :params}
        {:status 200
         :headers {"content-type" "text/css"}
         :body
         (replace-relative-root-url
          system
          (slurp (resource-or-not-found (str "public/stylesheets/" resource))))}))

    (context "/images" []
      (GET "/:resource" {{resource :resource} :params}
        {:status 200
         :headers {"content-type" (str "image/" (last (str/split resource #"\.")))}
         :body (io/input-stream (resource-or-not-found (str "public/images/" resource)))}))))

