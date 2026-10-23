/*
 * Copyright 2020-Present The Open Workflow Specification Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openworkflow.sdk.impl.http;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;

public class DefaultURITemplateResolver implements URITemplateResolver {

  @Override
  public URI resolveTemplates(
      String uri, WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel model) {
    return URI.create(expandImpl(uri, model.asMap().orElse(Map.of())));
  }

  // Private implementation
  private enum Operator {
    NO_OP,
    PLUS,
    HASH,
    DOT,
    SLASH,
    SEMICOLON,
    QUESTION_MARK,
    AMP;
  }

  private static void checkVarname(String token, int col) {
    if (token.startsWith(".") || token.endsWith(".")) {
      throw new IllegalArgumentException(
          "Variable name cannot start or end with a dot at col:" + col);
    }
    if (token.contains("..")) {
      throw new IllegalArgumentException(
          "Variable name cannot contain consecutive dots at col:" + col);
    }
    for (int i = 0; i < token.length(); i++) {
      if (token.charAt(i) == '%') {
        if (i + 2 >= token.length()
            || !isHexDigit(token.charAt(i + 1))
            || !isHexDigit(token.charAt(i + 2))) {
          throw new IllegalArgumentException(
              "Invalid percent-encoding in variable name at col:" + col);
        }
      }
    }
  }

  private static boolean isHexDigit(char c) {
    return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
  }

  private static void validateLiteral(Character c, int col) {
    switch (c) {
      case '+':
      case '#':
      case '/':
      case ';':
      case '?':
      case '&':
      case ' ':
      case '!':
      case '=':
      case '$':
      case '|':
      case '*':
      case ':':
      case '~':
      case '-':
        throw new IllegalArgumentException(
            "Illegal character identified in the token at col:" + col);
      default:
        break;
    }
  }

  private static int getMaxChar(StringBuilder buffer, int col) {
    if (buffer == null || buffer.length() == 0) {
      return -1;
    } else {
      String value = buffer.toString();

      if (value.isEmpty()) {
        return -1;
      } else {
        try {
          if (value.charAt(0) == '0') {
            throw new IllegalArgumentException(
                "Leading zero not allowed in max chars at col:" + col);
          }
          int result = Integer.parseInt(value);
          if (result < 1 || result > 9999) {
            throw new IllegalArgumentException("Max chars out of range (1-9999) at col:" + col);
          }
          return result;
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("Cannot parse max chars at col:" + col);
        }
      }
    }
  }

  private static Operator getOperator(Character c, StringBuilder token, int col) {
    switch (c) {
      case '+':
        return Operator.PLUS;
      case '#':
        return Operator.HASH;
      case '.':
        return Operator.DOT;
      case '/':
        return Operator.SLASH;
      case ';':
        return Operator.SEMICOLON;
      case '?':
        return Operator.QUESTION_MARK;
      case '&':
        return Operator.AMP;
      default:
        validateLiteral(c, col);
        token.append(c);
        return Operator.NO_OP;
    }
  }

  private static String expandImpl(String str, Map<String, Object> substitutions) {
    final StringBuilder result = new StringBuilder(str.length() * 2);

    boolean toToken = false;
    final StringBuilder token = new StringBuilder();

    Operator operator = null;
    boolean composite = false;
    boolean toMaxCharBuffer = false;
    final StringBuilder maxCharBuffer = new StringBuilder(3);
    boolean firstToken = true;

    for (int i = 0; i < str.length(); i++) {
      char character = str.charAt(i);
      switch (character) {
        case '{':
          toToken = true;
          token.setLength(0);
          firstToken = true;
          break;
        case '}':
          if (toToken) {
            if (toMaxCharBuffer && maxCharBuffer.length() == 0) {
              throw new IllegalArgumentException("Empty prefix modifier at col:" + i);
            }
            boolean expanded =
                expandToken(
                    operator,
                    token.toString(),
                    composite,
                    getMaxChar(maxCharBuffer, i),
                    firstToken,
                    substitutions,
                    result,
                    i);
            if (expanded && firstToken) {
              firstToken = false;
            }
            toToken = false;
            token.setLength(0);
            operator = null;
            composite = false;
            toMaxCharBuffer = false;
            maxCharBuffer.setLength(0);
          } else {
            throw new IllegalArgumentException("Failed to expand token, invalid at col:" + i);
          }
          break;
        case ',':
          if (toToken) {
            if (toMaxCharBuffer && maxCharBuffer.length() == 0) {
              throw new IllegalArgumentException("Empty prefix modifier at col:" + i);
            }
            boolean expanded =
                expandToken(
                    operator,
                    token.toString(),
                    composite,
                    getMaxChar(maxCharBuffer, i),
                    firstToken,
                    substitutions,
                    result,
                    i);
            if (expanded && firstToken) {
              firstToken = false;
            }
            token.setLength(0);
            composite = false;
            toMaxCharBuffer = false;
            maxCharBuffer.setLength(0);
            break;
          }
        // Intentional fall-through for commas outside the {}
        default:
          if (toToken) {
            if (operator == null) {
              operator = getOperator(character, token, i);
            } else if (toMaxCharBuffer) {
              if (Character.isDigit(character)) {
                maxCharBuffer.append(character);
              } else {
                throw new IllegalArgumentException(
                    "Illegal character identified in the token at col:" + i);
              }
            } else {
              if (character == ':') {
                toMaxCharBuffer = true;
                maxCharBuffer.setLength(0);
              } else if (character == '*') {
                composite = true;
              } else {
                validateLiteral(character, i);
                token.append(character);
              }
            }
          } else {
            if (character > 0x7F || Character.isHighSurrogate(character)) {
              String toEncode;
              if (Character.isHighSurrogate(character)
                  && i + 1 < str.length()
                  && Character.isLowSurrogate(str.charAt(i + 1))) {
                toEncode = new String(new char[] {character, str.charAt(++i)});
              } else {
                toEncode = Character.toString(character);
              }
              for (byte b : toEncode.getBytes(StandardCharsets.UTF_8)) {
                result.append(String.format("%%%02X", b & 0xFF));
              }
            } else {
              result.append(character);
            }
          }
          break;
      }
    }

    if (!toToken) {
      return result.toString();
    } else {
      throw new IllegalArgumentException("Unterminated token");
    }
  }

  private static void addPrefix(Operator op, StringBuilder result) {
    switch (op) {
      case HASH:
        result.append('#');
        break;
      case DOT:
        result.append('.');
        break;
      case SLASH:
        result.append('/');
        break;
      case SEMICOLON:
        result.append(';');
        break;
      case QUESTION_MARK:
        result.append('?');
        break;
      case AMP:
        result.append('&');
        break;
      default:
        return;
    }
  }

  private static void addSeparator(Operator op, StringBuilder result) {
    switch (op) {
      case DOT:
        result.append('.');
        break;
      case SLASH:
        result.append('/');
        break;
      case SEMICOLON:
        result.append(';');
        break;
      case QUESTION_MARK:
      case AMP:
        result.append('&');
        break;
      default:
        result.append(',');
        return;
    }
  }

  private static void addValue(
      Operator op, String token, Object value, StringBuilder result, int maxChar) {
    switch (op) {
      case PLUS:
      case HASH:
        addExpandedValue(null, value, result, maxChar, false);
        break;
      case QUESTION_MARK:
      case AMP:
        result.append(token + '=');
        addExpandedValue(null, value, result, maxChar, true);
        break;
      case SEMICOLON:
        result.append(token);
        addExpandedValue("=", value, result, maxChar, true);
        break;
      case DOT:
      case SLASH:
      case NO_OP:
        addExpandedValue(null, value, result, maxChar, true);
    }
  }

  private static void addValueElement(
      Operator op, String token, Object value, StringBuilder result, int maxChar) {
    switch (op) {
      case PLUS:
      case HASH:
        addExpandedValue(null, value, result, maxChar, false);
        break;
      case QUESTION_MARK:
      case AMP:
      case SEMICOLON:
      case DOT:
      case SLASH:
      case NO_OP:
        addExpandedValue(null, value, result, maxChar, true);
    }
  }

  private static boolean isSurrogate(char cp) {
    return (cp >= 0xD800 && cp <= 0xDFFF);
  }

  private static boolean isIprivate(char cp) {
    return (0xE000 <= cp && cp <= 0xF8FF);
  }

  private static boolean isUcschar(char cp) {
    return (0xA0 <= cp && cp <= 0xD7FF)
        || (0xF900 <= cp && cp <= 0xFDCF)
        || (0xFDF0 <= cp && cp <= 0xFFEF);
  }

  private static void addExpandedValue(
      String prefix, Object value, StringBuilder result, int maxChar, boolean replaceReserved) {
    String stringValue = convertNativeTypes(value);
    int cpCount = stringValue.codePointCount(0, stringValue.length());
    int max = (maxChar != -1) ? Math.min(maxChar, cpCount) : cpCount;
    result.ensureCapacity(max * 2); // hint to SB
    boolean toReserved = false;
    final StringBuilder reservedBuffer = new StringBuilder(3);

    if (max > 0 && prefix != null) {
      result.append(prefix);
    }

    int charCount = 0;
    for (int i = 0; i < stringValue.length() && charCount < max; i++) {
      char character = stringValue.charAt(i);
      charCount++;

      if (character == '%' && !replaceReserved) {
        toReserved = true;
        reservedBuffer.setLength(0);
      }

      String toAppend = Character.toString(character);
      try {
        if (isSurrogate(character)) {
          StringBuilder sb = new StringBuilder();
          sb.append(Character.toChars(stringValue.codePointAt(i++)));
          toAppend = URLEncoder.encode(sb.toString(), StandardCharsets.UTF_8.name());
        } else if (replaceReserved || isUcschar(character) || isIprivate(character)) {
          toAppend = URLEncoder.encode(toAppend, StandardCharsets.UTF_8.name());
        }
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }

      if (toReserved) {
        reservedBuffer.append(toAppend);

        if (reservedBuffer.length() == 3) {
          boolean isEncoded = false;
          try {
            URLDecoder.decode(reservedBuffer.toString(), StandardCharsets.UTF_8.name());
            isEncoded = true;
          } catch (Exception e) {
            // ignore
          }

          if (isEncoded) {
            result.append(reservedBuffer);
          } else {
            result.append("%25");
            // only if !replaceReserved
            result.append(reservedBuffer.substring(1));
          }
          toReserved = false;
          reservedBuffer.setLength(0);
        }
      } else {
        if (character == ' ') {
          result.append("%20");
        } else if (character == '%') {
          result.append("%25");
        } else {
          result.append(toAppend);
        }
      }
    }

    if (toReserved) {
      result.append("%25");
      result.append(reservedBuffer.substring(1));
    }
  }

  private static boolean isList(Object value) {
    return value instanceof ArrayList
        || // checking concrete instances first as it's faster
        value instanceof List;
  }

  private static boolean isMap(Object value) {
    return value instanceof HashMap
        || // checking concrete instances first as it's faster
        value instanceof Map;
  }

  enum SubstitutionType {
    EMPTY,
    STRING,
    LIST,
    MAP;
  }

  private static SubstitutionType getSubstitutionType(Object value, int col) {
    if (value == null) {
      return SubstitutionType.EMPTY;
    } else if (isNativeType(value)) {
      return SubstitutionType.STRING;
    } else if (isList(value)) {
      return SubstitutionType.LIST;
    } else if (isMap(value)) {
      return SubstitutionType.MAP;
    } else {
      throw new IllegalArgumentException(
          "Illegal class passed as substitution, found " + value.getClass() + " at col:" + col);
    }
  }

  private static boolean isEmpty(SubstitutionType substType, Object value) {
    if (value == null) {
      return true;
    } else {
      switch (substType) {
        case STRING:
          return false;
        case LIST:
          return ((List) value).isEmpty();
        case MAP:
          return ((Map) value).isEmpty();
        default:
          return true;
      }
    }
  }

  private static boolean isNativeType(Object value) {
    if (value instanceof String
        || value instanceof Boolean
        || value instanceof Integer
        || value instanceof Long
        || value instanceof Float
        || value instanceof Double) {
      return true;
    }
    return false;
  }

  private static String convertNativeTypes(Object value) {
    if (value instanceof String) {
      return (String) value;
    } else if (value instanceof Boolean
        || value instanceof Integer
        || value instanceof Long
        || value instanceof Float
        || value instanceof Double) {
      return value.toString();
    }
    throw new IllegalArgumentException(
        "Illegal class passed as substitution, found " + value.getClass());
  }

  // returns true if expansion happened
  private static boolean expandToken(
      Operator operator,
      String token,
      boolean composite,
      int maxChar,
      boolean firstToken,
      Map<String, Object> substitutions,
      StringBuilder result,
      int col) {
    if (token.isEmpty()) {
      throw new IllegalArgumentException("Found an empty token at col:" + col);
    }

    checkVarname(token, col);

    Object value = substitutions.get(token);
    SubstitutionType substType = getSubstitutionType(value, col);
    if (substType == SubstitutionType.EMPTY || isEmpty(substType, value)) {
      return false;
    }

    if (firstToken) {
      addPrefix(operator, result);
    } else {
      addSeparator(operator, result);
    }

    switch (substType) {
      case STRING:
        addStringValue(operator, token, (Object) value, result, maxChar);
        break;
      case LIST:
        addListValue(operator, token, (List<Object>) value, result, maxChar, composite);
        break;
      case MAP:
        addMapValue(operator, token, (Map<String, Object>) value, result, maxChar, composite);
        break;
    }

    return true;
  }

  private static boolean addStringValue(
      Operator operator, String token, Object value, StringBuilder result, int maxChar) {
    addValue(operator, token, value, result, maxChar);
    return true;
  }

  private static boolean addListValue(
      Operator operator,
      String token,
      List<Object> value,
      StringBuilder result,
      int maxChar,
      boolean composite) {
    boolean first = true;
    for (Object v : value) {
      if (first) {
        addValue(operator, token, v, result, maxChar);
        first = false;
      } else {
        if (composite) {
          addSeparator(operator, result);
          addValue(operator, token, v, result, maxChar);
        } else {
          result.append(',');
          addValueElement(operator, token, v, result, maxChar);
        }
      }
    }
    return !first;
  }

  private static boolean addMapValue(
      Operator operator,
      String token,
      Map<String, Object> value,
      StringBuilder result,
      int maxChar,
      boolean composite) {
    boolean first = true;
    if (maxChar != -1) {
      throw new IllegalArgumentException("Value trimming is not allowed on Maps");
    }
    for (Map.Entry<String, Object> v : value.entrySet()) {
      if (composite) {
        if (!first) {
          addSeparator(operator, result);
        }
        addValueElement(operator, token, v.getKey(), result, maxChar);
        result.append('=');
      } else {
        if (first) {
          addValue(operator, token, v.getKey(), result, maxChar);
        } else {
          result.append(',');
          addValueElement(operator, token, v.getKey(), result, maxChar);
        }
        result.append(',');
      }
      addValueElement(operator, token, v.getValue(), result, maxChar);
      first = false;
    }
    return !first;
  }
}
