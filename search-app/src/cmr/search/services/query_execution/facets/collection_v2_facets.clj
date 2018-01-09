(ns cmr.search.services.query-execution.facets.collection-v2-facets
  "Returns facets v2 along with collection search results. See
  https://wiki.earthdata.nasa.gov/display/CMR/Updated+facet+response"
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.set :as set]
   [clojure.string :as str]
   [cmr.common-app.services.search.query-execution :as query-execution]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.util :as util]
   [cmr.search.services.query-execution.facets.facets-results-feature :as frf]
   [cmr.search.services.query-execution.facets.facets-v2-helper :as v2h]
   [cmr.search.services.query-execution.facets.hierarchical-v2-facets :as hv2]
   [cmr.search.services.query-execution.facets.links-helper :as lh]
   [cmr.transmit.connection :as conn]
   [ring.util.codec :as codec]))

(def facets-v2-params->elastic-fields
  "Defines the mapping of the base search parameters for the v2 facets fields to its field names
   in elasticsearch."
  {:science-keywords :science-keywords.humanized
   :platform :platform-sn.humanized2
   :instrument :instrument-sn.humanized2
   :data-center :organization.humanized2
   :project :project-sn.humanized2
   :processing-level-id :processing-level-id.humanized2
   :variables :variables})

(def facets-v2-params
  "Facets query params by concept-type"
  (keys facets-v2-params->elastic-fields))

(def facet-fields->aggregation-fields
  "Defines the mapping between facet fields to aggregation fields."
  (into {}
        (map (fn [field] [field (keyword (str (name field) "-h"))]) facets-v2-params)))

(def v2-facets-result-field-in-order
  "Defines the v2 facets result field in order"
  ["Keywords" "Platforms" "Instruments" "Organizations" "Projects" "Processing levels"
   "Measurements" "Output File Formats" "Reprojections"])

(def collection-v2-facets-root
  "Root element for the facet response"
  {:title "Browse Collections"
   :type :group})

(defconfig include-variable-facets
  "Controls whether or not to display variable facets. Feature toggle needed while prototyping
  with EDSC in certain environments."
  {:type Boolean :default false})
