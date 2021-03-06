(ns ^{ :doc 
      "Functions for handling MIDI event and controller input

      Pink's design  uses a virtual device system so that projects 
      can be written to depend on the virtual device, and the
      real device can be configured per-system using a .pinkrc
      file. This allows both swapping in and out of hardware as 
      well as creating mock hardware devices.

      Conventions are to use the following for virtual hardware
      names:

      * \"keyboard x\" - number of keyboard
      * \"knobs/sliders x\" - number of knobs/slider device

      Note, a hardware device can map to multiple virtual devices."

      :author "Steven Yi"}
  pink.io.midi
  (:import [javax.sound.midi MidiSystem MidiDevice MidiDevice$Info

                             Receiver ShortMessage]
           [clojure.lang IFn]))


;; functions for listing registered MIDI devices

(defn list-midi-devices []
  (let [infos (MidiSystem/getMidiDeviceInfo)]
    (loop [[^MidiDevice$Info x & xs] infos 
           retval []]
      (if x 
        (recur xs 
               (conj retval {:name (.getName x) 
                             :description (.getDescription x) 
                             :device-info x
                             :device (MidiSystem/getMidiDevice x)}))
        retval))))

(defn midi-input-device?
  [d] 
  (not (zero? (.getMaxTransmitters ^MidiDevice (:device d)))))

(defn midi-output-device?
  [d] 
  (not (zero? (.getMaxReceivers ^MidiDevice (:device d)))))

(defn list-midi-input-devices []
  (filter midi-input-device? (list-midi-devices)))


(defn list-midi-output-devices []
  (filter midi-output-device?  (list-midi-devices)))

;; Pink MIDI Manager

(defn create-midi-manager []
  (atom {}))

;; processors set per channel
(defn add-virtual-device
  [midi-manager device-name] 

  (let [vd {:name device-name
            :keys (boolean-array 128 false)
            :event-processors (make-array IFn 16) 
            :cc-processors (into-array (for [i (range 16)] 
                                         (into-array 
                                           (for [x (range 128)] 
                                             (atom 0.0)))))
            :listener nil
            }] 
    (swap! midi-manager assoc device-name vd)
    vd
    ))

(defn list-devices 
  [midi-manager]
  @midi-manager)

(comment
  (let [f (create-midi-manager)]
    (add-virtual-device f "slider/knobs 1") 
    (add-virtual-device f "keyboard 1") 
    (println (list-devices f))))

;; Binding

(defn find-midi-device [^String device-name device-type]
  (let [devices (list-midi-devices)
        found (filter 
                #(and (>= (.indexOf ^String (:description %) device-name) 0) 
                (if (= :in device-type)  
                  (>= (.getMaxReceivers ^MidiDevice (:device %) ) 0)
                  (>= (.getMaxTransmitters ^MidiDevice (:device %) ) 0)
                  ))
                devices)

        num-found (count found)]
    (cond
      (<= num-found 0) 
      (throw (Exception. "No devices found"))
      (> num-found 1) 
      (throw (Exception. (format "Multiple devices found (%d)." num-found)))
      :else (first found))))


(defn create-receiver [virtual-device]
  (let [^"[[Lclojure.lang.Atom;" cc-processors 
        (:cc-processors virtual-device)
        ^"[Lclojure.lang.IFn;" event-processors 
        (:event-processors virtual-device)
        ] 
    (reify Receiver
    (send [this msg timestamp] 
      (when (instance? ShortMessage msg)
        (let [smsg ^ShortMessage msg
              cmd (.getCommand smsg)
              channel (.getChannel smsg)
              data1 (.getData1 smsg)
              data2 (.getData2 smsg)] 
          (condp = cmd
            ShortMessage/CONTROL_CHANGE
            (when-let [atm (aget cc-processors channel data1)]
              (reset! atm data2))

            ShortMessage/NOTE_ON
            (when-let [efn (aget event-processors channel)]
              (efn cmd data1 data2))

            ShortMessage/NOTE_OFF
            (when-let [efn (aget event-processors channel)]
              (efn cmd data1 data2))
            ))

        )))))

(defn bind-device 
  [midi-manager ^String hardware-id ^String virtual-device-name]
  {:pre [midi-manager hardware-id virtual-device-name]}
  (println (format "Connecting %s to %s" hardware-id virtual-device-name))
  (let [device ^MidiDevice (:device (find-midi-device hardware-id :in)) 
        virtual-device (@midi-manager virtual-device-name)]
    (when (nil? virtual-device)
      (throw (Exception. (format "Unknown virtual device: %s" virtual-device-name))))
    (when (not (.isOpen device)) 
      (.open device))
    (.setReceiver (.getTransmitter device) 
                  (create-receiver virtual-device))
    ))

(defn bind-key-func
  [virtual-device ^long channel ^IFn afn]
  (aset ^"[Lclojure.lang.IFn;" ( :event-processors virtual-device) 
        channel afn))

(defn get-midi-cc-atom
  [virtual-device channel cc-num]
  (aget (:cc-processors virtual-device)
        channel cc-num))

;(defn midi-cc-trigger 
;  [trigfn]
;  (fn [key atm old-v new-v]
;    (when (and (< old-v 127) (= new-v 127))
;      (trigfn) 
;      )))

(defn set-midi-event-processor
  [virtual-device channel midi-event-func]

  )

;; MIDI Device Debugging

(defn create-debug-receiver []
  (reify Receiver
    (send [this msg timestamp] 
      (when (instance? ShortMessage msg)
        (let [smsg ^ShortMessage msg
              cmd (.getCommand smsg)
              channel (.getChannel smsg)
              data1 (.getData1 smsg)
              data2 (.getData2 smsg)] 
          (println (format "%d %d %d %d" cmd channel data1 data2)))))))

(defn midi-device-debug 
  [^String hardware-id]
  (let [device ^MidiDevice (:device (find-midi-device hardware-id :in))]
    (when (not (.isOpen device)) 
      (.open device))
    (.setReceiver (.getTransmitter device) (create-debug-receiver))
    ))


;; Utility functions


(defn midi->freq
  "Convert MIDI Note number to frequency in hertz"
  ^double [^long notenum]
  (* 440.0  (Math/pow 2.0  (/ (- notenum 57) 12))))

