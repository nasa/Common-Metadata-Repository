(ns cmr.search.services.parameters.provider-short-name
  "Contains functions for tranforming provider_short_name parameter to provider parameter."
  (:require [clojure.string :as s]
            [cmr.common.util :as util]
            [cmr.common.services.errors :as errors]
            [cmr.search.services.transformer :as transformer]
            [cmr.metadata-db.services.provider-service :as metadata-db]))

(def short-name-no-match-provider
  "The dummy provider name used when the provider_short_name search parameter values failed to
  match to any providers and there is also no other provider in the search parameters.
  In this case, we want to provide a dummy provider name that does not match any existing providers."
  "ShortNameNoMatchProvider")

(defn- validate-provider-short-name
  "Validates that provider-short-name value must be a single value or a vector of values,
  and no pattern option is provided."
  [value options]
  (when-not (or (string? value) (sequential? value))
    (errors/throw-service-error
      :bad-request "Parameter [provider_short_name] must have a single value or multiple values."))
  (when (:pattern options)
    (errors/throw-service-error
      :bad-request "Parameter [provider_short_name] does not support search by pattern.")))

(defn- add-case-sensitivity-to-string
  "Returns the string with case sensitivity added to the string."
  [value case-sensitive?]
  (if case-sensitive?
    value
    (str "(?i)" value)))

(defn- short-name->provider-ids
  "Performs the provider short-name search with search options (case sensitivity and pattern)
  against the given providers. Returns the provider-ids of the matching providers."
  [providers case-sensitive? short-name]
  (let [matcher (-> short-name
                    (java.util.regex.Pattern/quote)
                    (add-case-sensitivity-to-string case-sensitive?))
        matched-providers (filter #(re-matches (re-pattern matcher) (:short-name %)) providers)]
    (map :provider-id matched-providers)))

(defn- provider-short-names->provider-ids
  [providers provider-short-names case-sensitive?]
  (if (sequential? provider-short-names)
    (mapcat (partial short-name->provider-ids providers case-sensitive?) provider-short-names)
    (short-name->provider-ids providers case-sensitive? provider-short-names)))

(defn- add-provider-ids-to-params
  "Returns the params with provider-ids added to the :provider value"
  [params provider-ids]
  (if (seq provider-ids)
    (if (string? (:provider params))
      (assoc-in params [:provider] (conj provider-ids (:provider params)))
      (update-in params [:provider] concat provider-ids))
    (if (:provider params)
      params
      ;; the provider_short_name does not match to any providers and there is also no provider
      ;; in search parameters. In this case, we want to search on a dummy provider that will
      ;; match no providers.
      (assoc params :provider short-name-no-match-provider))))

(defn- do-replace-provider-short-names
  "Performs the conversion of provider-short-names params into provider params."
  [context params]
  (let [provider-short-names (:provider-short-name params)
        options (get-in params [:options :provider-short-name])]
    (validate-provider-short-name provider-short-names options)
    (let [mdb-context (transformer/context->metadata-db-context context)
          providers (metadata-db/get-providers mdb-context)
          case-sensitive? (= "false" (:ignore-case options))
          provider-ids (provider-short-names->provider-ids
                         providers provider-short-names case-sensitive?)]
      (-> params
          (dissoc :provider-short-name)
          (util/dissoc-in [:options :provider-short-name])
          (add-provider-ids-to-params provider-ids)))))

(defn replace-provider-short-names
  "Converts provider-short-names params if any into provider params, returns the params."
  [context params]
  (if (:provider-short-name params)
    (do-replace-provider-short-names context params)
    params))

