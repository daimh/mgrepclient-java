package mgrepclient;
import java.util.*;
/**
 * MgrepResult reperesents a single unit of mgrep mapping
 * @version 4.0.0
 */
public class MgrepResult implements Comparable<MgrepResult> {
  long dictionaryId;
  int locationFrom, locationTo;
  String text;
  /**
   * Constructor
   *
   * @param dictionaryId
   * @param locationFrom
   * @param locationTo
   */
  public MgrepResult (long dictionaryId, int locationFrom, int locationTo,
      String text) {
    this.dictionaryId = dictionaryId;
    this.locationFrom = locationFrom;
    this.locationTo = locationTo;
    this.text = text;
  }
  /**
   * return Dictionary ID
   */
  public long getDictionaryId() {
    return dictionaryId;
  }
  /**
   * return LocationFrom
   */
  public int getLocationFrom() {
    return locationFrom;
  }
  /**
   * return LocationTo
   */
  public int getLocationTo() {
    return locationTo;
  }
  /**
   * return Text
   */
  public String getText() {
    return text;
  }
  /**
   * return compare rsult
   */
  public int compareTo(MgrepResult ano) {
    int rtn = locationFrom - ano.locationFrom;
    if (rtn != 0) return rtn;
    rtn = locationTo - ano.locationTo;
    if (rtn != 0) return rtn;
    if (dictionaryId < ano.dictionaryId) return -1;
    if (dictionaryId > ano.dictionaryId) return 1;
    return 0;
  }
}
