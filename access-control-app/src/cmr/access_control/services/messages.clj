(ns cmr.access-control.services.messages
  "Common error messages for access control services."
  (:require [clojure.string :as string]
            [cmr.common.util :as util]))

(defn provider-does-not-exist
  [provider-id]
  (format "Provider with provider-id [%s] does not exist." (util/html-escape provider-id)))

(def token-required
  "Valid user token required.")

(defn users-do-not-exist
  [usernames]
  (format "The following users do not exist [%s]" (util/html-escape (string/join ", " usernames))))

(defn managing-group-does-not-exist
  [managing-group-id]
  (format "Managing group id [%s] is invalid, no group with this concept id can be found." 
          (util/html-escape managing-group-id)))

(defn invalid-revision-id
  [revision-id]
  (format "Invalid revision-id [%s]. Cmr-Revision-id in the header must be a positive integer." (util/html-escape revision-id)))
