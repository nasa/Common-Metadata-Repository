(ns cmr.search.validators.opendata
 "Validate Opendata formatted collections against JSON schema in /resources/schema"
 (:require
  [clojure.java.io :as io]
  [cheshire.core :as cheshire]
  [cmr.common.validations.json-schema :as json-schema]
  [cmr.common.mime-types :as mime-types]
  [cmr.search.models.query :as query-model]))

(defn- load-opendata-schema
  "Load and parse named opendata schema from resources"
  [schema-name]
  (json-schema/parse-json-schema-from-uri
   (io/resource (str "schema/opendata/" schema-name ".json"))))

(def opendata-schemas
  {:catalog (load-opendata-schema "catalog")
   :dataset (load-opendata-schema "dataset")
   :distribution (load-opendata-schema "distribution")
   :organization (load-opendata-schema "organization")
   :vcard (load-opendata-schema "vcard")})

(defn validate-dataset
  "Validate a given opendata record"
  [dataset]
  (json-schema/validate-json (load-opendata-schema "catalog") dataset))

; (empty? (validate-dataset (slurp (io/resource "problem_collection.json"))))
