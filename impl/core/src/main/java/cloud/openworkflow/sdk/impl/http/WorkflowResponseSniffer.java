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
package cloud.openworkflow.sdk.impl.http;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class WorkflowResponseSniffer {
  private static final byte UTF8_BOM_0 = (byte) 0xEF;
  private static final byte UTF8_BOM_1 = (byte) 0xBB;
  private static final byte UTF8_BOM_2 = (byte) 0xBF;

  private static final byte UTF16_BE_BOM_0 = (byte) 0xFE;
  private static final byte UTF16_BE_BOM_1 = (byte) 0xFF;
  private static final byte UTF16_LE_BOM_0 = (byte) 0xFF;
  private static final byte UTF16_LE_BOM_1 = (byte) 0xFE;

  private static final byte NUL = 0x00;
  private static final byte BS = 0x08;
  private static final byte VT = 0x0B;
  private static final byte SO = 0x0E;
  private static final byte SUB = 0x1A;
  private static final byte FS = 0x1C;
  private static final byte US = 0x1F;
  private static final byte DEL = 0x7F;

  public static Optional<String> asString(byte[] data) {
    Charset charset = StandardCharsets.UTF_8;
    int offset = 0;

    if (data.length >= 2) {
      if (data[0] == UTF16_BE_BOM_0 && data[1] == UTF16_BE_BOM_1) {
        charset = StandardCharsets.UTF_16BE;
        offset = 2;
      } else if (data[0] == UTF16_LE_BOM_0 && data[1] == UTF16_LE_BOM_1) {
        charset = StandardCharsets.UTF_16LE;
        offset = 2;
      } else if (data.length >= 3
          && data[0] == UTF8_BOM_0
          && data[1] == UTF8_BOM_1
          && data[2] == UTF8_BOM_2) {
        offset = 3;
      }
    }

    // Scans first 512 bytes if not UTF16
    if (offset != 2) {
      int bytesToScan = Math.min(data.length, 512);
      for (int i = offset; i < bytesToScan; i++) {
        byte b = data[i];
        if ((b >= NUL && b <= BS)
            || b == VT
            || (b >= SO && b <= SUB)
            || (b >= FS && b <= US)
            || b == DEL) {
          return Optional.empty();
        }
      }
    }

    try {
      return Optional.of(
          charset
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(data, offset, data.length - offset))
              .toString());
    } catch (CharacterCodingException ex) {
      return Optional.empty();
    }
  }

  private WorkflowResponseSniffer() {}
}
