(ns org.sysfoundry.jdbc.cp
  (:require [hikari-cp.core :as cp]
            [integrant.core :as ig]
            [taoensso.timbre :as logger]
            )
  )

(def db-config-defaults {:pool-name          "default-pool"
                         :adapter            "postgresql"
                         :database-name      "postgres"
                         :server-name        "localhost"
                         :port-number        5432
                         :register-mbeans    false})

(defmethod ig/init-key ::hikari-cp [_ config]
  (let [final-config (merge db-config-defaults config)]
    (logger/debug "Creating hikari connection pool with config : " final-config)
    (cp/make-datasource final-config)
    )
  )

(defmethod ig/halt-key! ::hikari-cp [_ cp]
  (logger/debug "Closing hikari connection pool : " cp)
  (.close cp)
  (logger/debug "Done closing connection pool : " cp)
  )