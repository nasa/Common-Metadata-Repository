;;; run with lein exec -p drop_user.clj
(require '[cmr.bootstrap.db :as db])

(db/drop-user)
