package mgrepclient;
import java.net.*;
import java.io.*;
import java.util.*;
/**
 * mgrep client connection
 * @version 20190123
 */
public class MgrepConnection {
  final static String VERSION = "20190123";
  DictionarySet[] dictionarySetAll;
  Vector<DictionarySet> dictionarySetIdle;
  /**
   * Constructor
   *
   * @param connection mgrep daemons the object would connect to. format is
   * HOST:PORT[,HOST:PORT][|HOST:PORT[,HOST:PORT]]. An example is
   * HOST1:55555,HOST2:55556|HOST3:55557|HOST4:55558,HOST5:55559,HOST6:55560.
   * In this example. HOST1 and HOST2 have a complete set of dictionary, so is
   * HOST3, and so is HOST4, HOST5 and HOST6. For each annotate request, the
   * MgrepConnection object would pick a complete set, send request to all mgrep
   * daemons of the set, merge result returned by the daemons, and return the
   * merged result to user.
   */
  public MgrepConnection (String connection) throws UnknownHostException,
      IOException, MgrepException {
    String[] setConnArray = connection.trim().split("\\|");
    dictionarySetAll = new DictionarySet[setConnArray.length];
    for (int i=0; i<setConnArray.length; i++) {
      dictionarySetAll[i] = new DictionarySet(setConnArray[i].trim());
      if (dictionarySetAll[i].dictionaryPiece[0].wordDividerCount !=
          dictionarySetAll[0].dictionaryPiece[0].wordDividerCount)
        throw new MgrepException("Daemon set'" + setConnArray[i] +
            "' has different word divider count from '" + setConnArray[0] +
            "'");
    }
    dictionarySetIdle = new Vector<DictionarySet>();
    for (int i=0; i<setConnArray.length; i++)
      dictionarySetIdle.add(dictionarySetAll[i]);
    
  }
  /**
   * get word-divider count
   *
   * @return an integer
   */
  public int getWordDividerCount() {
    return dictionarySetAll[0].dictionaryPiece[0].wordDividerCount;
  }
  /**
   * annotate the text and return a vector of Dictionary ID, Location-from and
   * location-to
   *
   * @param longest longest match only
   * @param wordDividerIndex Word Divider Index
   * @param sort sort the output by location_from
   * @param text the text to be annotated
   * @return a vector of integer array, Columns of array are Dictionary ID,
   * Location-From and Location-To
   */
  public Vector<MgrepResult> annotate(boolean longest, int wordDividerIndex,
      boolean sort, String text) throws InterruptedException, MgrepException {
    if (wordDividerIndex < 0 || wordDividerIndex >=
        dictionarySetAll[0].dictionaryPiece[0].wordDividerCount)
      throw new MgrepException("Wrong word divider index " + wordDividerIndex +
          "\n");
    text = text.replace('\n', ' ');
    StringBuffer buffer = new StringBuffer("A");
    buffer.append(longest ? 'Y' : 'N');
    buffer.append((char)(wordDividerIndex + '0'));
    buffer.append(text);
    buffer.append("\n");
    DictionarySet set;
    synchronized (dictionarySetIdle) {
      while (dictionarySetIdle.size() == 0)
        dictionarySetIdle.wait();
      set = dictionarySetIdle.firstElement();
      dictionarySetIdle.removeElementAt(0);
    }
    Vector<MgrepResult> result = set.annotate(buffer.toString());
    synchronized (dictionarySetIdle) {
      dictionarySetIdle.add(set);
      dictionarySetIdle.notify();
    }
    if (sort) Collections.sort(result);
    return result;
  }
  /**
   * close all connections
   */
  public void close() throws InterruptedException, IOException {
    synchronized (dictionarySetIdle) {
      while (dictionarySetIdle.size() != dictionarySetAll.length)
        dictionarySetIdle.wait();
      for (int i=0; i<dictionarySetAll.length; i++) dictionarySetAll[i].close();
    }
  }
}
class DictionaryPiece_Thread extends Thread {
  DictionaryPiece dictionaryPiece;
  Vector<MgrepResult> result;
  DictionaryPiece_Thread(DictionaryPiece piece, Vector<MgrepResult> res) {
    result = res;
    dictionaryPiece = piece;
  }
  public void run() {
    try {
      dictionaryPiece.outStream.print(dictionaryPiece.parentDictionarySet.text);
      String line = dictionaryPiece.reader.readLine(); 
      if (line == null)
        throw new MgrepException("Mgrep daemon on (" +
            dictionaryPiece.connection +
            ") failed to accept new connections\n");
      while (!"".equals(line)) {
        String[] arr = line.split("\t");
        if (arr.length != 4)
          throw new MgrepException("Wrong protocol. Contact developer");
        synchronized (result) {
          result.add(new MgrepResult(Long.parseLong(arr[0]),
              Integer.parseInt(arr[1]), Integer.parseInt(arr[2]), arr[3]));
        }
        line = dictionaryPiece.reader.readLine(); 
        if (line == null)
          throw new MgrepException("Mgrep daemon on (" +
              dictionaryPiece.connection + ") failed to respond\n");
      }
    } catch (IOException e) {
      synchronized (dictionaryPiece.parentDictionarySet.ioException) {
        dictionaryPiece.parentDictionarySet.ioException.add(e);
      } 
    } catch (MgrepException e) {
      synchronized (dictionaryPiece.parentDictionarySet.mgrepException) {
        dictionaryPiece.parentDictionarySet.mgrepException.add(e);
      } 
    }
  }
}
class DictionaryPiece {
  DictionarySet parentDictionarySet;
  Socket socket;
  PrintStream outStream;
  BufferedReader reader;
  String connection;
  int wordDividerCount;
  DictionaryPiece(DictionarySet dictionarySet, String connection) throws
      UnknownHostException, IOException, MgrepException {
    parentDictionarySet = dictionarySet;
    String[] arg = connection.split(":");
    if (arg.length != 2)
      throw new MgrepException("wrong connection string (" + connection + ")" );
    String host = arg[0];
    int port = Integer.parseInt(arg[1]);
    socket = new Socket(host, port);
    reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
        "UTF-8"));
    outStream = new PrintStream(socket.getOutputStream(), true, "UTF-8");
    if (!"mgrep".equals(reader.readLine()))
      throw new MgrepException("cannot find mgrep daemon on (" + connection +
          ")");
    String[] info = reader.readLine().split(" ");
    if (info.length != 2)
      throw new MgrepException("wrong connection string (" + connection + ")" );
    if (!MgrepConnection.VERSION.equals(info[0]))
      throw new MgrepException("Version of mgrep daemon on (" +
          connection + ") is " + info[0] + ", but client version is " +
          MgrepConnection.VERSION);
    this.connection = connection;
    wordDividerCount = Integer.parseInt(info[1]);
  }
  void close() throws IOException {
    socket.close();
  }
}
class DictionarySet {
  DictionaryPiece[] dictionaryPiece;
  String text;
  Vector<IOException> ioException;
  Vector<MgrepException> mgrepException;
  DictionarySet(String connection) throws UnknownHostException, IOException,
      MgrepException {
    ioException = new Vector<IOException>();
    mgrepException = new Vector<MgrepException>();
    String[] aPiece = connection.split(",");
    dictionaryPiece = new DictionaryPiece[aPiece.length];
    for (int i=0; i<aPiece.length; i++) {
      dictionaryPiece[i] = new DictionaryPiece(this, aPiece[i].trim());
      if (dictionaryPiece[i].wordDividerCount !=
          dictionaryPiece[0].wordDividerCount)
        throw new MgrepException("Daemon '" + aPiece[i] +
            "' has different word divider count from '" + aPiece[0] + "'");
    }
  }
  Vector<MgrepResult> annotate(String text) throws InterruptedException,
      MgrepException {
    ioException.clear();
    mgrepException.clear();
    Vector<MgrepResult> result = new Vector<MgrepResult>();
    this.text = text;
    DictionaryPiece_Thread[] thr = new
        DictionaryPiece_Thread[dictionaryPiece.length];
    for (int i=0; i<dictionaryPiece.length; i++) {
      thr[i] = new DictionaryPiece_Thread(dictionaryPiece[i], result);
      thr[i].start();
    }
    for (int i=0; i<dictionaryPiece.length; i++) 
      thr[i].join();
    if (ioException.size() > 0 || mgrepException.size() > 0) {
      StringBuffer sbErr = new StringBuffer();
      for (IOException e : ioException) {
        sbErr.append(e.getMessage());
        sbErr.append('\n');
      }
      for (MgrepException e : mgrepException) {
        sbErr.append(e.getMessage());
        sbErr.append('\n');
      }
      throw new MgrepException(sbErr.toString());
    }
    return result;
  }
  void close() throws IOException {
    for (int i=0; i<dictionaryPiece.length; i++) dictionaryPiece[i].close();
  }
}
