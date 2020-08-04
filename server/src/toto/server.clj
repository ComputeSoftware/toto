(ns ^:no-doc toto.server
  (:require
    [ring.middleware.defaults]
    [ring.middleware.gzip :as gzip]
    [ring.middleware.cljsjs :as cljsjs]
    [ring.middleware.anti-forgery :as anti-forgery]
    [ring.util.response :as response]
    [compojure.core :as compojure]
    [compojure.route :as route]
    [taoensso.timbre :as log]
    [taoensso.sente :as sente]
    [org.httpkit.server :as httpkit.server]
    [hiccup2.core :as hiccup]
    [taoensso.sente.server-adapters.http-kit :as sente.http-kit]
    [taoensso.sente.packers.transit :as sente-transit]
    [clojure.java.io :as io]
    [toto.live :as live])
  (:gen-class))

(def default-port 10666)

(log/set-level! :info)
;; (reset! sente/debug-mode?_ true)

(let [packer (sente-transit/get-transit-packer)
      ;; TODO CSRF token set to nil for now; Need to fix this https://github.com/metasoarous/oz/issues/122
      chsk-server (sente/make-channel-socket-server!
                    (sente.http-kit/get-sch-adapter)
                    {:packer        packer
                     :csrf-token-fn nil})
      {:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]} chsk-server]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def connected-uids connected-uids))

(defn send-all!
  [data]
  (doseq [uid (:any @connected-uids)]
    (chsk-send! uid data)))

(add-watch connected-uids :connected-uids
           (fn [_ _ old new]
             (when (not= old new)
               (log/infof "Connected uids change: %s" new))))

(defn connected-uids? []
  @connected-uids)

(defn unique-id
  "Get a unique id for a session."
  []
  (str (java.util.UUID/randomUUID)))

(defn session-uid
  "Get session uuid from a request."
  [req]
  (get-in req [:session :uid]))

(def default-mathjax
  )

(defn mathjax-src-url
  [version]
  (format "https://cdnjs.cloudflare.com/ajax/libs/mathjax/%s/MathJax.js?config=TeX-MML-AM_CHTML"
          version))

(defn index-view
  [{:keys [title
           css-hrefs
           mathjax?
           mathjax-src
           mathjax-version]
    :or   {title           "Oz document"
           css-hrefs       ["http://ozviz.io/css/style.css"]
           mathjax-version "2.7.7"}}]
  (str
    (hiccup/html
      [:html
       [:head
        [:meta {:charset "UTF-8"}]
        [:meta {:content "width=device-width, initial-scale=1"
                :name    "viewport"}]
        [:title title]
        [:link {:href "http://ozviz.io/oz.svg"
                :rel  "shortcut icon"
                :type "image/x-icon"}]
        (map (fn [href]
               [:link {:href href
                       :rel  "stylesheet"
                       :type "text/css"}])
             css-hrefs)
        (when (or mathjax? mathjax-src)
          [:script {:src   (or mathjax-src (mathjax-src-url mathjax-version))
                    :type  "text/javascript"
                    :async true}])]
       [:body
        [:div#app]
        [:script {:src  "js/app.js"
                  :type "text/javascript"}]]])))

(defonce current-root-dir (atom ""))
(defonce *index-view-argm (atom {}))

(compojure/defroutes my-routes
  (compojure/GET "/" req
    (response/content-type
      {:status  200
       :session (if (session-uid req)
                  (:session req)
                  (assoc (:session req) :uid (unique-id)))
       :body    (index-view @*index-view-argm)}
      "text/html"))
  (compojure/GET "/token" req {:csrf-token anti-forgery/*anti-forgery-token*})
  (compojure/GET "/chsk" req
    (log/debugf "/chsk got: %s" req)
    (ring-ajax-get-or-ws-handshake req))
  (compojure/POST "/chsk" req (ring-ajax-post req))
  (route/resources "/" {:root "toto/public"})
  (compojure/GET "*" req (let [reqpath (live/join-paths @current-root-dir (-> req :params :*))
                               reqfile (io/file reqpath)
                               altpath (str reqpath ".html")
                               dirpath (live/join-paths reqpath "index.html")]
                           (cond
                             ;; If the path exists, use that
                             (and (.exists reqfile) (not (.isDirectory reqfile)))
                             (response/file-response reqpath)
                             ;; If not, look for a `.html` version and if found serve that instead
                             (.exists (io/file altpath))
                             (response/content-type (response/file-response altpath) "text/html")
                             ;; If the path is a directory, check for index.html
                             (and (.exists reqfile) (.isDirectory reqfile))
                             (response/file-response dirpath)
                             ;; Otherwise, not found
                             :else (response/redirect "/"))))
  (route/not-found "<h1>There's no place like home</h1>"))


(def main-ring-handler
  (-> my-routes
      (ring.middleware.defaults/wrap-defaults
        (dissoc ring.middleware.defaults/site-defaults
                :security))
      (cljsjs/wrap-cljsjs)
      (gzip/wrap-gzip)))

(defmulti -event-msg-handler :id)

(defn event-msg-handler [{:as ev-msg :keys [id ?data event]}]
  (log/tracef "Event: %s" event)
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler :default
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid (:uid session)]
    (log/tracef "Unhandled event: %s" event)
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

(defonce router_ (atom nil))

(defn stop-router!
  []
  (when-let [stop-fn @router_] (stop-fn)))

(defn start-router! []
  (stop-router!)
  (reset! router_
          (sente/start-server-chsk-router! ch-chsk event-msg-handler)))

(defonce ^:private *web-server (atom nil))

(defn web-server-started?
  []
  (boolean @*web-server))

(defn stop-web-server!
  []
  (when-let [stop-fn (:stop-fn @*web-server)] (stop-fn)))

(defn open-browser-at-uri!
  [uri]
  (try
    (if (and (java.awt.Desktop/isDesktopSupported)
             (.isSupported (java.awt.Desktop/getDesktop) java.awt.Desktop$Action/BROWSE))
      (.browse (java.awt.Desktop/getDesktop) (java.net.URI. uri))
      (.exec (java.lang.Runtime/getRuntime) (str "xdg-open " uri)))
    (Thread/sleep 7500)
    (catch java.awt.HeadlessException _)))

(defn start-web-server!
  [{::keys [port open-browser?]
    :or    {port default-port}}]
  (stop-web-server!)
  (let [ring-handler (var main-ring-handler)
        server (httpkit.server/run-server
                 ring-handler
                 {:port                 port
                  :legacy-return-value? false})
        stop-fn #(deref (httpkit.server/server-stop! server {}))
        uri (format "http://localhost:%s/" port)]
    (log/infof "Web server is running at `%s`" uri)
    (reset! *web-server {:port port :stop-fn stop-fn})
    (when open-browser?
      (open-browser-at-uri! uri))
    server))

(defn get-server-port
  []
  (:port @*web-server))

(defn stop! []
  (stop-router!)
  (stop-web-server!))

(defn set-index-view-args!
  [index-view-argm]
  (reset! *index-view-argm index-view-argm)
  index-view-argm)

(defn start!
  "Start the oz plot server (on localhost:10666 by default)."
  [{::keys [port index-view]}]
  (set-index-view-args! index-view)
  (start-web-server! {::port (or port default-port)})
  (start-router!))

(defn -main [& [port]]
  (start!
    (cond-> {}
      port (assoc ::port (Integer/parseInt port)))))