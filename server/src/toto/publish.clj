(ns toto.publish
  (:require
    [clojure.edn :as edn]
    [clojure.data.json :as json]
    [toto.live :as live]
    [taoensso.timbre :as log]
    [tentacles.gists :as gists]))

(defn- doc-type
  [doc]
  (if (sequential? doc) :ozviz :vega))

(defn- auth-args
  [args]
  (let [the-auth-args (select-keys args [:auth :auth-token :client-id :access-token])
        auth-file (or (:auth-file args) (live/join-paths (System/getProperty "user.home") ".oz/github-creds.edn"))]
    (if (empty? the-auth-args)
      (try
        (edn/read-string (slurp auth-file))
        (catch Exception e
          (log/errorf "Unable to find/parse github authorization file `~/.oz/github-creds.edn`. Please review the output of `(doc oz/publish!)` for auth instructions.")
          (throw e)))
      the-auth-args)))

(defn gist!
  "Create a gist with the given doc

  Requires authentication, which must be provided by one of the following opts:
  * `:auth`: a Github auth token the form \"username:password\"
  * `:auth-token`: a GitHub OAuth1 / Personal access token as a string (recommended)
  * for oauth2:
    * `:client-id`: an oauth2 client id property
    * `:access-token`: oauth2 access token

  CAUTION: Note that running these options from the REPL may leave sensitive data in your `./.lein-repl-history` file.
  Thus it's best that you avoid using these options, and instead create a single edn file at `~/.oz/github-creds.edn` with these opts.
  You can run `chmod 600` on it, so that only the owner is able to access it.
  If you want to specify a different path use:
  * `:auth-file`: defaults to `~/.oz/github-creds.edn`.

  Additional options:
  * `:public`: default false
  * `:description`: auto generated based on doc"
  [doc & {:as   opts
          :keys [name description public]
          :or   {public false}}]
  (let [type (doc-type doc)
        name (or name
                 (case type
                   :ozviz "ozviz-document.edn"
                   :vega "vega-viz.json"))
        description (or description
                        (case type
                          :ozviz "Ozviz document; To load go to https://ozviz.io/#/gist/<gist-id>."
                          :vega "Vega/Vega-Lite viz; To load go to https://vega.github.io/editor"))
        doc-string (case type
                     :ozviz (pr-str doc)
                     :vega (json/write-str doc))
        create-gist-opts (merge {:description description :public public}
                                (auth-args opts))
        gist (gists/create-gist {name doc-string} create-gist-opts)]
    gist))