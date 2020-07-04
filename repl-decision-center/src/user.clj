(ns user
  (:require [taoensso.timbre :as logger]
            [integrant.core :as ig]
            [integrant.repl :refer [clear go halt prep init reset reset-all]]
            [integrant.repl.state :as igst]
            [clojure.java.io :as io]
            [org.sysfoundry.jdbc.cp :as cp]
            [org.sysfoundry.http.server :as server]
            [org.sysfoundry.oas.v3 :as oasv3]
            [org.sysfoundry.system :as system]
            )
  
  )


(integrant.repl/set-prep! (constantly (system/system-config {:profile :dev})))

(defn system 
  "Retrieves the system from the integrant.repl.state namespace"
  []
  igst/system
  )

(defn get-svc [svc-key]
  (get (system) svc-key)
  )

(defn get-pgdb-repo []
  (get-svc :org.sysfoundry.dc.pgdb.namespace-repo/pgdb-namespace-repo)
  )
