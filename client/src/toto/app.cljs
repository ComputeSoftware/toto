(ns ^:no-doc toto.app
  (:require
    [reagent.core :as r]
    [reagent.dom :as rd]
    [taoensso.encore :as encore]
    [taoensso.timbre :as timbre]
    [taoensso.sente :as sente]
    [taoensso.sente.packers.transit :as sente-transit]
    [toto.core :as core]))

(if goog.DEBUG
  (timbre/set-level! :debug)
  (timbre/set-level! :info))

(defonce app-state (r/atom {:text      "Pay no attention to the man behind the curtain!"
                            :view-spec nil
                            :error     nil}))

(let [packer (sente-transit/get-transit-packer)
      {:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client!
        "/chsk"
        nil
        {:type   :auto
         :packer packer})]
  (def chsk chsk)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(defmulti -event-msg-handler :id)

(defn event-msg-handler [{:as ev-msg :keys [id ?data event]}]
  (timbre/debugf "Event: %s" event) []
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler :default
  [{:as ev-msg :keys [event]}]
  (timbre/debugf "Unhandled event: %s" event))

(defmethod -event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] (encore/have vector? ?data)]
    (if (:first-open? new-state-map)
      (timbre/debugf "Channel socket successfully established!: %s" ?data)
      (timbre/debugf "Channel socket state change: %s" ?data))))

(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (timbre/debugf "Handshake: %s" ?data)))


;; This is the main event handler; If we want to do cool things with other kinds of data going back and forth,
;; this is where we'll inject it.
(defmethod -event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (let [[id msg] ?data]
    (case id
      :toto.core/view-doc (swap! app-state merge {:view-spec msg :error nil})
      (timbre/debugf "Push event from server: %s" ?data))))


(def router_ (atom nil))

(defn stop-router! []
  (when-let [stop-f @router_] (stop-f)))

(defn start-router! []
  (stop-router!)
  (reset! router_ (sente/start-client-chsk-router! ch-chsk event-msg-handler)))


(defn application [app-state]
  (if-let [spec (:view-spec @app-state)]
    [core/live-view spec]
    [:div
     [:h1 "Waiting for first spec to load..."]
     [:p "This may take a second the first time if you call a plot function, unless you first call " [:code '(oz/start-server!)] "."]]))

(defn error-boundary
  [component]
  (r/create-class
    {:component-did-catch (fn [this e info]
                            (swap! app-state assoc :error e))
     :reagent-render      (fn [comp]
                            (if-let [error (:error @app-state)]
                              [:div
                               [:h2 "Unable to process document!"]
                               [:h3 "Error:"]
                               [:code (pr-str error)]]
                              comp))}))

(defn Root
  []
  [error-boundary [application app-state]])

(defn init []
  (enable-console-print!)
  (start-router!)
  (rd/render [Root]
             (. js/document (getElementById "app"))))