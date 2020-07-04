(ns org.sysfoundry.http.server
  (:require [ring.adapter.jetty :as jetty]
            [clojure.spec.alpha :as spec]
            [taoensso.timbre :as log]
            [integrant.core :as ig]
            )
  )

(spec/def ::jetty map?)

(spec/def ::request-handler fn?)

(spec/def ::on-init-fn fn?)

(spec/def ::on-stop-fn fn?)

(spec/def ::config (spec/keys :req [::jetty ::request-handler]
                              :opt [::init-fn ::stop-fn]
                              ))

(spec/def ::start-config (spec/keys :req [::jetty ::request-handler ::start-server-fn]
                                    :opt [::on-init-fn]))

(spec/def ::stop-config (spec/keys :req [::jetty ::request-handler ::stop-server-fn]
                                    :opt [::on-stop-fn]))

(def default-config {::jetty {:port 9090 :join? false} ::request-handler (fn [req] {:status 200 :body "This is the default handler. Please change it!" :headers {"Content-Type" "application/json"}})})

(defn create-server [server-config-map]
  (let [explain-data (spec/explain-data ::config server-config-map)]
    (if-not (nil? explain-data)
      (throw (ex-info (spec/explain ::config server-config-map) {:cause explain-data}))
      (assoc server-config-map
             ::started false
             ::start-server-fn (fn [config-map]
                                 (let [jetty-config (::jetty config-map)
                                       join? (:join? jetty-config)
                                       request-handler (::request-handler config-map)
                                       server (jetty/run-jetty request-handler jetty-config)]
                                   (if-not join?
                                     (assoc config-map ::server server
                                            ::started? true
                                            ::stop-server-fn (fn [config-map]
                                                               (let [svr (::server config-map)]
                                                                 (.stop svr)
                                                                 (assoc config-map ::started? false
                                                                        ::server nil))))
                                     config-map))))
      )
    )
  )

(defn start-server [server-config-map]
  (let [explain-data (spec/explain-data ::start-config server-config-map)]
    (if-not (nil? explain-data)
      (throw (ex-info (spec/explain ::config server-config-map) {:cause explain-data}))
      (let [start-fn (::start-server-fn server-config-map)]
        (start-fn server-config-map)
        )
      )
    )
  )

(defn stop-server [server-config-map]
  (let [explain-data (spec/explain-data ::stop-config server-config-map)]
    (if-not (nil? explain-data)
      (throw (ex-info (spec/explain ::config server-config-map) {:cause explain-data}))
      (let [stop-fn (::stop-server-fn server-config-map)]
        (stop-fn server-config-map)))))


(defmethod ig/init-key ::listener [_ config]
  (let [final-config (merge default-config config)
        server-map (-> final-config
                       create-server
                       start-server)]
    server-map
    )
  )

(defmethod ig/halt-key! ::listener [_ server-map]
  (log/info "Stopping http listener " server-map)
  (let [stopped-server-map (stop-server server-map)]
    (log/info "Stopped http listener " stopped-server-map))
  
  )