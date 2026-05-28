(ns cmr.common-app.test.kms-lookup
  "Shared test helpers for setting up KMS caches."
  (:require
   [cmr.common-app.services.kms-lookup :as kms-lookup]))

(defn create-kms-caches-for-testing
  "Creates all KMS caches required by create-kms-index and lookup functions.
  Intended for centralized context setup to avoid missing-cache test failures when
  new KMS cache keys are introduced. This is only for testing."
  []
  {kms-lookup/kms-short-name-cache-key (kms-lookup/create-kms-short-name-cache)
   kms-lookup/kms-projects-cache-key (kms-lookup/create-kms-project-uuid-cache)
   kms-lookup/kms-umm-c-cache-key (kms-lookup/create-kms-umm-c-cache)
   kms-lookup/kms-location-cache-key (kms-lookup/create-kms-location-cache)
   kms-lookup/kms-measurement-cache-key (kms-lookup/create-kms-measurement-cache)
   kms-lookup/kms-processing-level-cache-key (kms-lookup/create-kms-processing-level-uuid-cache)
   kms-lookup/kms-science-keywords-cache-key (kms-lookup/create-kms-science-keywords-uuid-cache)
   kms-lookup/kms-platforms-cache-key (kms-lookup/create-kms-platforms-uuid-cache)
   kms-lookup/kms-instruments-cache-key (kms-lookup/create-kms-instruments-uuid-cache)
   kms-lookup/kms-providers-cache-key (kms-lookup/create-kms-providers-uuid-cache)
   kms-lookup/kms-spatial-keywords-cache-key (kms-lookup/create-kms-spatial-keywords-uuid-cache)
   kms-lookup/kms-concepts-cache-key (kms-lookup/create-kms-concepts-uuid-cache)
   kms-lookup/kms-iso-topic-categories-cache-key (kms-lookup/create-kms-iso-topic-categories-uuid-cache)
   kms-lookup/kms-granule-data-format-cache-key (kms-lookup/create-kms-granule-data-format-uuid-cache)
   kms-lookup/kms-mime-type-cache-key (kms-lookup/create-kms-mime-type-uuid-cache)
   kms-lookup/kms-related-urls-cache-key (kms-lookup/create-kms-related-urls-uuid-cache)
   kms-lookup/kms-temporal-keywords-cache-key (kms-lookup/create-kms-temporal-keywords-uuid-cache)})
