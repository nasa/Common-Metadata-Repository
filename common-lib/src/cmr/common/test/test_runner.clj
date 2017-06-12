(ns cmr.common.test.test-runner
  "DEPRECATED

  This code has moved to `cmr.common.test.runners.default`; please update your
  text editor's aliases to use the new namespace.

  This namespace is currently maintained for backwards compatibility."
  (:require
   [cmr.common.test.runners.default]
   [potemkin :refer [import-vars]]))

(import-vars
  [cmr.common.test.runners.default
   integration-test-namespaces
   unit-test-namespaces
   run-tests
   analyze-results
   print-results
   failed-test-result?
   fail-fast?->test-results-handler
   last-test-results
   run-all-tests])
