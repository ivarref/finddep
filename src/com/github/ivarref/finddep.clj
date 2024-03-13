(ns com.github.ivarref.finddep
  (:refer-clojure :exclude [find])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as jio]
            [clojure.pprint :as pprint]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.deps :as deps]
            [clojure.tools.deps.extensions :as ext]
            [clojure.tools.deps.tree :as tree]
            [clojure.tools.deps.util.session :as session]
            [clojure.tools.gitlibs]
            [clojure.tools.deps.util.maven :as mvn-util]
            [clojure.tools.deps.util.io :as io :refer [printerrln]]
            [clojure.tools.deps.extensions.git]
            [clojure.tools.deps.script.make-classpath2 :as make-cp2]
            [fzf.core :as fz])
  (:gen-class)
  (:import (Janei Janei)
           (clojure.lang IExceptionInfo)
           (org.eclipse.aether.impl DefaultServiceLocator)))

;DefaultRepositorySystem
(comment
  (do
    (require '[clj-commons.pretty.repl])
    (clj-commons.pretty.repl/install-pretty-exceptions)))

(defn get-libs
  ([master-edn]
   (let [master-edn (merge {:mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"}, "clojars" {:url "https://repo.clojars.org/"}}}
                           master-edn)
         aliases []
         combined-aliases (deps/combine-aliases master-edn aliases)
         basis (session/with-session
                 (deps/calc-basis master-edn {:resolve-args   (merge combined-aliases {:trace true})
                                              :classpath-args combined-aliases}))
         libs (:libs basis)]
     (reduce-kv
       (fn [o k v]
         (assoc o k
                  (update v :dependents (fn [old]
                                          (if (not-empty old)
                                            (into (sorted-set) old)
                                            (sorted-set))))))
       (sorted-map)
       libs)))
  ([]
   (let [{:keys [root-edn user-edn project-edn]} (deps/find-edn-maps "deps.edn")
         master-edn (deps/merge-edns [root-edn user-edn project-edn])]
     (get-libs master-edn))))

(defn all-children [libs lib]
  (reduce-kv
    (fn [o k {:keys [dependents]}]
      (if (contains? dependents lib)
        (into (conj o k) (all-children libs k))
        o))
    (sorted-set)
    libs))

(defn direct-children [libs lib]
  (reduce-kv
    (fn [o k {:keys [dependents]}]
      (if (contains? dependents lib)
        (conj o k)
        o))
    (sorted-set)
    libs))

(defn height [libs lib]
  (let [dependents (get-in libs [lib :dependents] ::none)]
    (if (= ::none dependents)
      (throw (ex-info (str "Lib '" lib "' not found") {:lib lib}))
      (if (empty? dependents)
        1
        (+ 1 (reduce
               max
               0
               (mapv (partial height libs) dependents)))))))

(defn libs-with-needles [libs needles]
  (reduce-kv (fn [o k v]
               (if (or (contains? needles k)
                       (not= #{}
                             (set/intersection (all-children libs k) needles)))
                 (assoc o k (-> v
                                (assoc :height (height libs k))))
                 o))
             (sorted-map)
             libs))

(defn find-needles [libs sym]
  (reduce-kv (fn [o k _]
               (if (str/includes? (str k) (str sym))
                 (conj o k)
                 o))
             (sorted-set)
             libs))

(defn depth [libs lib]
  (+ 1 (reduce
         max
         0
         (mapv (partial depth libs) (direct-children libs lib)))))

;1 s3-wagon-private/s3-wagon-private

;  2 com.amazonaws/aws-java-sdk-sts
;    4 com.amazonaws/aws-java-sdk-core
;      5 com.fasterxml.jackson.dataformat/jackson-dataformat-cbor

;  2 com.amazonaws/aws-java-sdk-s3
;    4 com.amazonaws/aws-java-sdk-core <-- will be shown later, remove!
;      5 com.fasterxml.jackson.dataformat/jackson-dataformat-cbor

;    3 com.amazonaws/aws-java-sdk-kms
;      4 com.amazonaws/aws-java-sdk-core
;        5 com.fasterxml.jackson.dataformat/jackson-dataformat-cbor

(defn version-str [x]
  (cond
    (= :mvn (:deps/manifest x))
    (str "{:mvn/version " (pr-str (get x :mvn/version)) "}")

    (some? (:local/root x))
    (str "{:local/root " (pr-str (get x :local/root)) "}")

    (some? (:git/sha x))
    (str "{:git/tag " (pr-str (get x :git/tag))
         " :git/sha \""
         (get x :git/sha)
         "\"}")))

(defn show-tree [libs root indent seen?]
  (let [lib (get libs root)]
    (println (str (str/join "" (repeat (* 2 indent) " "))
                  #_(str seen? " ")
                  #_(:height (get libs root) " ")
                  root
                  " "
                  (version-str lib))))
  (let [children (->> libs
                      (filter (fn [[_k {:keys [dependents]}]]
                                (contains? dependents root)))
                      (sort-by (fn [[k _]] (depth libs k)))
                      (reverse))]
    (doseq [[idx [k _]] (map-indexed vector children)]
      (let [previous-seen (->> (take idx (map first children))
                               (reduce
                                 (fn [o v]
                                   (into o (all-children libs v)))
                                 #{}))
            seen? (if (true? seen?)
                    true
                    (contains? previous-seen k))]
        (when (false? seen?)
          (show-tree libs k (inc indent) seen?))))))

(comment
  (let [libs (get-libs {:deps {'s3-wagon-private/s3-wagon-private {:mvn/version "1.3.5"}}})
        libs (libs-with-needles libs (find-needles libs "jackson-dataformat"))]
    (show-tree libs 's3-wagon-private/s3-wagon-private 0 false)))

(comment
  (let [libs (get-libs {:deps {'slipset/deps-deploy {:mvn/version "0.2.1"}}})
        libs (libs-with-needles libs (find-needles libs "slf4j-api"))]
    (doseq [[root {:keys [dependents]}] libs]
      (when (= #{} dependents)
        (show-tree libs root 0 false)))))

(comment
  (let [libs (get-libs {:deps {'io.github.cognitect-labs/test-runner {:git/tag "v0.5.0" :git/sha "b3fd0d2"}}})
        libs (libs-with-needles libs (find-needles libs ""))]
    (doseq [[root {:keys [dependents]}] libs]
      (when (= #{} dependents)
        (show-tree libs root 0 false)))))

(defn require-deps-edn! []
  (when (not (.exists (jio/file "deps.edn")))
    (binding [*out* *err*]
      (println "Error. Not a tools.deps project. Missing deps.edn"))
    (System/exit 1)))

(defn find [{:keys [name]}]
  (require-deps-edn!)
  (let [libs (get-libs)
        needles (find-needles libs (if (or (= name :all)
                                           (= name :*))
                                     ""
                                     name))
        libs (libs-with-needles libs needles)]
    (if (= needles #{})
      (binding [*out* *err*]
        (println (str "No matches found for '" name "'."))
        (println "Was is a typo?")
        (System/exit 1))
      (let [roots (->> libs
                       (filter (fn [[_k {:keys [dependents]}]]
                                 (= dependents #{})))
                       (sort-by (fn [[k _]] (depth libs k)))
                       (reverse))]
        (doseq [[root _] roots]
          (show-tree libs root 0 false))))))

(defn fzf [_]
  (require-deps-edn!)
  (let [libs (try
               (time (get-libs))
               (catch Throwable t
                 (println "Error during get-libs:")
                 (.printStackTrace t)
                 (throw t)))]
    (if-let [v (fz/fzf {:preview-fn (fn [selected]
                                      (with-out-str (find {:name (str selected)})))}
                       (into [] (mapv str (sort (keys libs)))))]
      (find {:name v})
      (println "Nothing selected, exiting"))))


(defn run
  "Run make-classpath script. See -main for details."
  [{:keys [config-user config-project project-deps cp-file jvm-file main-file basis-file manifest-file skip-cp trace tree] :as opts}]
  (println "opts is:" opts)
  (prn opts)
  (spit "opts.edn" (with-out-str (prn opts)))
  (let [project-deps (edn/read-string "{:deps {com.datomic/peer #:mvn{:version \"1.0.7075\"}}}")
        opts' (merge opts {:install-deps  (deps/root-deps)
                           :user-deps     (make-cp2/read-deps config-user)
                           :project-deps  project-deps #_(make-cp2/read-deps config-project)
                           :tool-resolver make-cp2/resolve-tool-args})
        {:keys [basis manifests], trace-log :trace} (make-cp2/run-core opts')
        {:keys [argmap libs classpath-roots]} basis
        {:keys [jvm-opts main-opts]} argmap]
    (when trace
      (spit "trace.edn" (binding [*print-namespace-maps* false] (with-out-str (pprint/pprint trace-log)))))
    (when tree
      (-> trace-log tree/trace->tree (tree/print-tree nil)))
    libs))

(defn runrun [args]
  (let [{:keys [options errors]} (make-cp2/parse-opts args)]
    (when (seq errors)
      (run! println errors)
      (System/exit 1))
    (run options)))

(comment
  (def dbg (runrun ["--config-project" "/home/ire/code/gh/finddep/deps.edn"])))


(defn entrypoint [_]
  (prn (runrun ["--config-project" "/home/ire/code/gh/finddep/deps.edn"])))

(comment
  (do
    (require '[wiretap.wiretap :as wiretap])
    (wiretap/install!
      #(do
         (when (:pre? %)
           (println (pr-str (:args %)))
           (prn "args:" (:args %))
           (prn "args:" (count (:args %))))
         (println (if (:pre? %) ">" "<") (:name %)))
      [#'clojure.tools.deps/calc-basis])
    (def dbg (runrun ["--config-project" "/home/ire/code/gh/finddep/deps.edn"]))))

(def s "({:paths [\"src\"], :deps {org.eclipse.jetty/jetty-http #:mvn{:version \"9.4.48.v20220622\"}}, :aliases {:deps {:replace-paths [], :replace-deps #:org.clojure{tools.deps.cli #:mvn{:version \"0.10.55\"}}, :ns-default clojure.tools.deps.cli.api, :ns-aliases {help clojure.tools.deps.cli.help}}, :test {:extra-paths [\"test\"]}}, :mvn/repos {\"central\" {:url \"https://repo1.maven.org/maven2/\"}, \"clojars\" {:url \"https://repo.clojars.org/\"}}} {:resolve-args {}, :classpath-args {}})\n")

(comment
  (def xyz (apply clojure.tools.deps/calc-basis
                  (edn/read-string s))))

(comment
  (ext/coord-deps 'org.eclipse.jetty/jetty-http
                  {:mvn/version "9.4.48.v20220622", :deps/manifest :mvn}
                  :mvn (edn/read-string s)))

; org.eclipse.jetty/jetty-http 9.4.48.v20220622
; org.apache.activemq/artemis-core-client 2.31.2

; "nREPL-session-8a50fd58-9287-4c1a-ac28-92c0664621a8" "ext/coord-deps" :descriptor #object[org.eclipse.aether.resolution.ArtifactDescriptorResult 0x68334c49 "org.eclipse.jetty:jetty-http:jar:9.4.48.v20220622 -> [org.eclipse.jetty:jetty-util:jar:9.4.48.v20220622 (compile), org.eclipse.jetty:jetty-io:jar:9.4.48.v20220622 (compile), javax.servlet:javax.servlet-api:jar:3.1.0 (provided), org.eclipse.jetty.toolchain:jetty-test-helper:jar:5.9 (test), org.junit.jupiter:junit-jupiter:jar:5.8.2 (test)]"]
; "main" "ext/coord-deps" :descriptor #object[org.eclipse.aether.resolution.ArtifactDescriptorResult 0x6cf6ffa6 "org.eclipse.jetty:jetty-http:jar:9.4.48.v20220622 -> [org.eclipse.jetty:jetty-util:jar:${project.version} (compile), org.eclipse.jetty:jetty-io:jar:${project.version} (compile), javax.servlet:javax.servlet-api:jar:3.1.0 (provided), org.eclipse.jetty.toolchain:jetty-test-helper:jar:5.9 (test), org.junit.jupiter:junit-jupiter:jar:5.8.2 (test)]"]


;"nREPL-session-03ea315c-3d0e-481c-a0c6-94f89e7595c2" "ext/coord-paths" :mvn org.clojure/clojure {:mvn/version "1.11.2", :deps/manifest :mvn, :parents #{[]}}
;"nREPL-session-03ea315c-3d0e-481c-a0c6-94f89e7595c2" "ext/coord-paths" :mvn org.eclipse.jetty/jetty-http {:mvn/version "9.4.48.v20220622", :deps/manifest :mvn, :parents #{[]}}
;"nREPL-session-03ea315c-3d0e-481c-a0c6-94f89e7595c2" "ext/coord-paths" :mvn org.clojure/spec.alpha {:mvn/version "0.3.218", :deps/manifest :mvn, :dependents [org.clojure/clojure], :parents #{[org.clojure/clojure]}}
;"nREPL-session-03ea315c-3d0e-481c-a0c6-94f89e7595c2" "ext/coord-paths" :mvn org.clojure/core.specs.alpha {:mvn/version "0.2.62", :deps/manifest :mvn, :dependents [org.clojure/clojure], :parents #{[org.clojure/clojure]}}
;"nREPL-session-03ea315c-3d0e-481c-a0c6-94f89e7595c2" "ext/coord-paths" :mvn org.eclipse.jetty/jetty-util {:mvn/version "9.4.48.v20220622", :deps/manifest :mvn, :dependents [org.eclipse.jetty/jetty-http org.eclipse.jetty/jetty-io], :parents #{[org.eclipse.jetty/jetty-http] [org.eclipse.jetty/jetty-http org.eclipse.jetty/jetty-io]}}
;"nREPL-session-03ea315c-3d0e-481c-a0c6-94f89e7595c2" "ext/coord-paths" :mvn org.eclipse.jetty/jetty-io {:mvn/version "9.4.48.v20220622", :deps/manifest :mvn, :dependents [org.eclipse.jetty/jetty-http], :parents #{[org.eclipse.jetty/jetty-http]}}

;"expanding deps ..."
;"nREPL-session-02d2f16f-713f-42e3-a732-b554d4abf2c5" "ext/coord-deps" :mvn org.clojure/clojure {:mvn/version "1.11.2", :deps/manifest :mvn}
;"nREPL-session-02d2f16f-713f-42e3-a732-b554d4abf2c5" "ext/canonicalize" :mvn :version "0.3.218"
;"nREPL-session-02d2f16f-713f-42e3-a732-b554d4abf2c5" "ext/canonicalize" :mvn :version "0.2.62"
;"nREPL-session-02d2f16f-713f-42e3-a732-b554d4abf2c5" "ext/coord-deps" :mvn org.eclipse.jetty/jetty-http {:mvn/version "9.4.48.v20220622", :deps/manifest :mvn}
;"nREPL-session-02d2f16f-713f-42e3-a732-b554d4abf2c5" "ext/canonicalize" :mvn :version "9.4.48.v20220622"
;"nREPL-session-02d2f16f-713f-42e3-a732-b554d4abf2c5" "ext/canonicalize" :mvn :version "9.4.48.v20220622"
;"nREPL-session-02d2f16f-713f-42e3-a732-b554d4abf2c5" "ext/coord-deps" :mvn org.clojure/spec.alpha {:mvn/version "0.3.218", :deps/manifest :mvn}
;"nREPL-session-02d2f16f-713f-42e3-a732-b554d4abf2c5" "ext/coord-deps" :mvn org.clojure/core.specs.alpha {:mvn/version "0.2.62", :deps/manifest :mvn}
;"nREPL-session-02d2f16f-713f-42e3-a732-b554d4abf2c5" "ext/coord-deps" :mvn org.eclipse.jetty/jetty-util {:mvn/version "9.4.48.v20220622", :deps/manifest :mvn}
;"nREPL-session-02d2f16f-713f-42e3-a732-b554d4abf2c5" "ext/coord-deps" :mvn org.eclipse.jetty/jetty-io {:mvn/version "9.4.48.v20220622", :deps/manifest :mvn}
;"nREPL-session-02d2f16f-713f-42e3-a732-b554d4abf2c5" "ext/canonicalize" :mvn :version "9.4.48.v20220622"
;"nREPL-session-02d2f16f-713f-42e3-a732-b554d4abf2c5" "ext/coord-deps" :mvn org.eclipse.jetty/jetty-util {:mvn/version "9.4.48.v20220622", :deps/manifest :mvn}
;"done expanding deps ..."

; graalvm:
;"expanding deps ..."
;"main" "ext/coord-deps" :mvn org.clojure/clojure {:mvn/version "1.11.2", :deps/manifest :mvn}
;"main" "ext/canonicalize" :mvn :version "0.3.218"
;"main" "ext/canonicalize" :mvn :version "0.2.62"
;"main" "ext/coord-deps" :mvn org.eclipse.jetty/jetty-http {:mvn/version "9.4.48.v20220622", :deps/manifest :mvn}
;"main" "ext/canonicalize" :mvn :version "${project.version}"
;"main" "ext/canonicalize" :mvn :version "${project.version}"
;"main" "ext/coord-deps" :mvn org.clojure/spec.alpha {:mvn/version "0.3.218", :deps/manifest :mvn}
;"main" "ext/coord-deps" :mvn org.clojure/core.specs.alpha {:mvn/version "0.2.62", :deps/manifest :mvn}
;"main" "ext/coord-deps" :mvn org.eclipse.jetty/jetty-util {:mvn/version "${project.version}", :deps/manifest :mvn}
;"main" "ext/coord-deps" :mvn org.eclipse.jetty/jetty-io {:mvn/version "${project.version}", :deps/manifest :mvn}
;"done expanding deps ..."
;

;"main" "ext/coord-paths" :mvn org.clojure/clojure {:mvn/version "1.11.2", :deps/manifest :mvn, :parents #{[]}}
;"main" "ext/coord-paths" :mvn org.eclipse.jetty/jetty-http {:mvn/version "9.4.48.v20220622", :deps/manifest :mvn, :parents #{[]}}
;"main" "ext/coord-paths" :mvn org.clojure/spec.alpha {:mvn/version "0.3.218", :deps/manifest :mvn, :dependents [org.clojure/clojure], :parents #{[org.clojure/clojure]}}
;"main" "ext/coord-paths" :mvn org.clojure/core.specs.alpha {:mvn/version "0.2.62", :deps/manifest :mvn, :dependents [org.clojure/clojure], :parents #{[org.clojure/clojure]}}
;"main" "ext/coord-paths" :mvn org.eclipse.jetty/jetty-util {:mvn/version "${project.version}", :deps/manifest :mvn, :dependents [org.eclipse.jetty/jetty-http], :parents #{[org.eclipse.jetty/jetty-http]}}
;"main" "ext/coord-paths" :mvn org.eclipse.jetty/jetty-io {:mvn/version "${project.version}", :deps/manifest :mvn, :dependents [org.eclipse.jetty/jetty-http], :parents #{[org.eclipse.jetty/jetty-http]}}

(defn -main
  "Entrypoint for finddep app"
  [& args]
  (try
    #_(runrun args)
    ;(prn (clojure.tools.deps.util.dir/*the-dir*))
    (ext/coord-deps 'org.eclipse.jetty/jetty-http
                    {:mvn/version "9.4.48.v20220622", :deps/manifest :mvn}
                    :mvn (edn/read-string s))
    #_(let [lib 'org.eclipse.jetty/jetty-http
            coord {:mvn/version "9.4.48.v20220622", :deps/manifest :mvn}]
        (ext/coord-deps lib coord '_manifest :janei))
    (catch Throwable t
      (printerrln "Error building classpath." (.getMessage t))
      (printerrln "Ex-data:" (ex-data t))
      (.printStackTrace t)
      (printerrln "Error building classpath." (.getMessage t))
      (printerrln "Ex-data:" (ex-data t))
      (System/exit 1)))
  (shutdown-agents))
