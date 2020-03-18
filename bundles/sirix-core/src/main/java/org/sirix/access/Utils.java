package org.sirix.access;

import org.brackit.xquery.atomic.QNm;

import javax.xml.namespace.QName;

/**
 * Encapsulates generic stuff.
 * 
 * @author Johannes Lichtenberger
 * 
 */
public final class Utils {

  /**
   * Building name consisting of a prefix and a name. The namespace-URI is not used over here.
   * 
   * @param name the {@link QName} of an element
   * @return a string: [prefix:]localname
   */
  public static String buildName(final QNm name) {
    return name.getPrefix().isEmpty() ? name.getLocalName()
        : new StringBuilder(name.getPrefix()).append(":").append(name.getLocalName()).toString();
  }
}
