(ns cmr.search.services.humanizers.humanizer-messages)

(def token-required-for-humanizer-modification
  "Humanizers cannot be modified without a valid user token.")

(def humanizer-deleted
  "Humanizer has been deleted.")

(def non-existant-humanizer
  "Humanizer does not exist.")

(def returning-empty-report
  "Returning empty report.")

(def trouble-getting-humanizers
  "Cannot get humanizers, default range facet humanizers will be used.")
