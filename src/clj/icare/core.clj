(ns icare.core

  "Use I2C and communicate with slave devices"

  {:author "Adam Helinski"}

  (:import (java.io RandomAccessFile
                    IOException)
           (sun.misc SharedSecrets
                     JavaIOFileDescriptorAccess)
           com.sun.jna.NativeLong
           dvlopt.icare.CLib))




;;;;;;;;;;


(def ^:private -I2C_SLAVE

  "ioctl request for selecting a slave device"

  (NativeLong. 0x0703
               true))




(def ^:private -I2C_TENBIT

  "ioctl request for choosing between 7 bits and 10 bits addressing"

  (NativeLong. 0x0704
               true))


  

(def ^:private ^JavaIOFileDescriptorAccess -fd-access

  "Object for accessing the file descriptor of a java file"
  
  (SharedSecrets/getJavaIOFileDescriptorAccess))




(defn- -fd

  "Get the linux file descriptor of a java file"

  [^RandomAccessFile file]

  (.get -fd-access
        (.getFD file)))




(defn- -lz-seq

  "Create a lazy sequence for reading a file byte per byte"

  [^RandomAccessFile file]

  (lazy-seq (cons (.read file)
                  (-lz-seq file))))




;;;;;;;;;;


(defprotocol II2C

  "Command an I2C bus"


  (extended-addressing [bus extended?]

    "Use 10 bits addressing ?

     Issue an ioctl call for choosing the addressing mode, 7 bits or 10 bits addresses.
  
     Throws an IOException if the system call fail.")


  (select [bus address]

    "Issue an ioctl call for selecting a slave device.

     Throws an IOException if the system call fail.

     Cf. `extend-addressing`")


  (write-byte [bus b]
              [bus register b]

    "Write a single byte directly or to a register.
    
     Throws an IOException if something goes wrong.")


  (write-bytes [bus ba]
               [bus register ba]
               [bus ba offset n]
               [bus register ba offset n]

    "Write a byte array directly or to a register.

     Throws an IOException if something goes wrong.")



  (read-byte [bus]
             [bus register]

    "Read a single byte directly or from a register.
    
     Throws an IOException if something goes wrong.")


  (read-bytes [bus ba]
              [bus register ba]
              [bus ba offset n]
              [bus register ba offset n]

    "Read several bytes into a byte array, directly or from a register.

     Returns the number of bytes read into the byte array.

     Throws an IOException if something goes wrong.")


  (status [bus]

    "Returns a map describing the current state of this bus")


  (closed? [bus]

    "Is this bus closed ?")


  (close [bus]
         
    "Close this bus.
    
     Throws an IOException if something goes wrong."))




(deftype I2C [-path
              ^RandomAccessFile  file
              ^long              -fd
              ^:volatile-mutable -extended?
              ^:volatile-mutable -slave
              ^:volatile-mutable -closed?]


  II2C


    (extended-addressing [bus extended?]
      (when (not= extended?
                  -extended?)
        (if (= (CLib/ioctl -fd
                           -I2C_TENBIT
                           (if extended?
                             1
                             0))
               -1)
          (throw (IOException. "ioctl call for using 10-bits addressing failed"))
          (set! -extended?
                extended?)))
      bus)


    (select [bus address]
      (when (not= address
                  -slave)
        (if (= (CLib/ioctl -fd
                           -I2C_SLAVE
                           address)
               -1)
          (throw (IOException. "ioctl call for selecting a slave device failed"))
          (set! -slave
                address)))
      bus)


    (write-byte [bus b]
      (.write file
              ^long b)
      bus)


    (write-byte [bus register b]
      (.write file
              ^long register)
      (.write file
              ^long b)
      bus)


    (write-bytes [bus ba]
      (.write file
              ^bytes ba)
      bus)


    (write-bytes [bus register ba]
      (.write file
              ^long register)
      (.write file
              ^bytes ba)
      bus)


    (write-bytes [bus ba offset n]
      (.write file
              ba
              offset
              n)
      bus)


    (write-bytes [bus register ba offset n]
      (.write file
              ^long register)
      (.write file
              ba
              offset
              n)
      bus)


    (read-byte [_]
      (.read file))


    (read-byte [_ register]
      (.write file
              ^long register)
      (.read file))


    (read-bytes [_ ba]
      (.read file
             ba))


    (read-bytes [_ register ba]
      (.write file
              ^long register)
      (.read file
             ba))


    (read-bytes [_ ba offset n]
      (.read file
             ba
             offset
             n))

    (read-bytes [_ register ba offset n]
      (.write file
              ^long register)
      (.read file
             ba
             offset
             n))


    (status [_]
      {:path                 -path
       :extended-addressing? -extended?
       :slave                -slave
       :closed?              -closed?})


    (closed? [_]
      -closed?)


    (close [bus]
      (when-not -closed?
        (.close file)
        (set! -closed?
              true))
      bus)




  clojure.lang.Seqable


    (seq [_]
      (-lz-seq file)))




;;;;;;;;;;


(defn open

  "Open an I2C bus at the given path.

   Make sure the user running the program has the permission to actually read
   and write this file.
  
   Returns an I2C object implementing the II2C protocol.

   Throws FileNotFoundException if the file doesn't exists
          SecurityException     if file permissions are wrong"

  [path]

  (let [file ^RandomAccessFile (RandomAccessFile. ^String path
                                                  "rw")]
    (I2C. path
          file
          (-fd file)
          false
          nil
          false)))
