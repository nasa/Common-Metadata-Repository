(ns cmr.common-app.api.launchpad-token-validation
  "Validate Launchpad token."
  (:require
   [cmr.acl.core :as acl]
   [cmr.common-app.config :as config]
   [cmr.common.log :refer [debug]]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as common-util]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.tokens :as tokens]))

;; TODO - remove legacy token check after legacy token retirement
(defn get-token-type
  "Returns the type of a given token"
  [token]
  (when (string? token)
    (cond
      (= (transmit-config/echo-system-token) token) "System"
      (re-seq #"[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}" token) "Echo-Token"
      (common-util/is-legacy-token? token) "Legacy-EDL"
      (tokens/is-launchpad-federated-jwt? token) "JWT-Launchpad-Level-5"
      (tokens/is-edl-mfa-jwt? token) "JWT-EDL-MFA-Level-4"
      (common-util/is-jwt-token? token) "JWT"
      :else "Launchpad")))

(defn- get-token-info
  "Returns token validation info. Returns map with :type, :valid?, :assurance-level, :requires-draft-acl?, :config-error?"
  [token]
  (when token
    (let [is-jwt? (common-util/is-jwt-token? token)
          assurance-level (when is-jwt? (tokens/get-assurance-level token))
          is-saml? (common-util/is-launchpad-token? token)
          saml-enabled? (config/enable-launchpad-saml-authentication)
          jwt-enabled? (config/enable-idfed-jwt-authentication)
          auth-disabled? (not (or saml-enabled? jwt-enabled?))
          valid-saml? (and is-saml? saml-enabled?)
          valid-jwt? (and is-jwt?
                          jwt-enabled?
                          assurance-level
                          (>= assurance-level (config/required-assurance-level)))]
      {:type (cond
               (= token (transmit-config/echo-system-token)) :system
               is-saml? :saml
               (and is-jwt? (= assurance-level 5)) :jwt-level-5
               (and is-jwt? (= assurance-level 4)) :jwt-level-4
               (and is-jwt? (= assurance-level 3)) :jwt-level-3
               is-jwt? :jwt-other
               :else :unknown)
       :valid? (and (not auth-disabled?) (or valid-saml? valid-jwt?))
       :assurance-level assurance-level
       :requires-draft-acl? (and is-jwt? (= assurance-level 4))
       :config-error? auth-disabled?})))

(defn- build-error-message
  "Builds error message for invalid token."
  [token-info]
  (if (:config-error? token-info)
    "Write operations are disabled. Both Launchpad SAML and IDFed JWT authentication are turned off."
    "You do not have permission to perform that action."))

(defn validate-write-token
  "Validates token is authorized for write operations. Level 4 JWT tokens require NON_NASA_DRAFT_USER ACL."
  ([request-context]
   (validate-write-token request-context nil))
  ([request-context provider-id]
   (let [token (:token request-context)]
     (when (and (config/launchpad-token-enforced)
                (not= token (transmit-config/echo-system-token)))

       (let [token-info (get-token-info token)
             ;; Level 4 tokens without provider-id are treated as invalid
             valid? (and (:valid? token-info)
                         (or (not (:requires-draft-acl? token-info))
                             provider-id))]
         (debug (format "Token validation (write) - Type: %s, Valid: %s, Assurance Level: %s, Provider ID: %s, Requires Draft ACL: %s, Token: %s"
                        (:type token-info)
                        valid?
                        (:assurance-level token-info)
                        (or provider-id "none")
                        (:requires-draft-acl? token-info)
                        (common-util/scrub-token token)))

         (when-not valid?
           (errors/throw-service-error :unauthorized (build-error-message token-info)))

         (when (:requires-draft-acl? token-info)
           (acl/verify-non-nasa-draft-permission request-context :update :provider-object provider-id)))))))

(defn validate-read-token
  "Validates token is valid for read operations."
  [request-context]
  (let [token (:token request-context)]
    (when (and (config/launchpad-token-enforced)
               (not= token (transmit-config/echo-system-token)))

      (let [token-info (get-token-info token)
            valid? (not= :unknown (:type token-info))]
        (debug (format "Token validation (read)- Type: %s, Valid: %s, Assurance Level: %s, Requires Draft ACL: %s, Token: %s"
                       (:type token-info)
                       valid?
                       (:assurance-level token-info)
                       (:requires-draft-acl? token-info)
                       (common-util/scrub-token token)))

        (when-not valid?
          (errors/throw-service-error :unauthorized (build-error-message token-info)))))))
