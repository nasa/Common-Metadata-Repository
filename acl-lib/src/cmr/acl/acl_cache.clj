(ns cmr.acl.acl-cache
  "Deprecated namespace. This is kept around only for allowing the RefreshAclCacheJob to be moved
  to the acl-fetcher namespace."
  (:require [cmr.common.jobs :refer [defjob]]))


;; Quartz puts the full classpath of jobs into the database. If the class isn't available in the
;; application you can't unschedule or delete the job. This is here to allow the application to
;; startup and reschedule the job. That will remove the reference to this class and then it can
;; be removed in a subsequent release.
;; TODO remove this namespace in sprint 25.
(defjob RefreshAclCacheJob
  [_ _]
  (throw (Exception. "Old version of RefreshAclCacheJob is running when it shouldn't be.")))