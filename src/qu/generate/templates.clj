(ns qu.generate.templates
  (:require [clojure.java.io :as io]
            [me.raynes.fs :as fs]))

(defn -main
  [& args]
  (let [resource-dir (fs/file (io/resource "qu/templates/"))
        out-dir (if-let [path (first args)]
                  (fs/file path)
                  (fs/file "resources/qu/templates"))]

    (when (fs/exists? out-dir)
      (println "Output dir exists. Delete first. Cancelling.")
      (System/exit 1))

    (fs/copy-dir resource-dir out-dir)
    (println "Qu template files copied into" (str out-dir) ".")))


