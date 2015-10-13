;;; run with lein exec -p create_user.clj
(require '[cmr.db :as db])

(try
  (db/drop-user)
  (finally
    (shutdown-agents)))
