/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.jetio.util;

import java.nio.ByteBuffer;

public class ByteBufferHelper {
    private static final byte[] highDigits;
    private static final byte[] lowDigits;

    // initialize lookup tables
    static {
        byte[] digits = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

        byte[] high = new byte[256];
        byte[] low = new byte[256];

        for ( int i = 0; i < 256; i++ ) {
            high[i] = digits[i >>> 4];
            low[i] = digits[i & 0x0F];
        }

        highDigits = high;
        lowDigits = low;
    }

    public static String getHexdump( ByteBuffer in ) {
        int size = in.remaining();

        if ( size == 0 ) {
            return "empty";
        }

        StringBuilder out = new StringBuilder( ( in.remaining() * 3 ) - 1 );

        int mark = in.position();

        // fill the first
        int byteValue = in.get() & 0xFF;
        out.append( (char) highDigits[byteValue] );
        out.append( (char) lowDigits[byteValue] );
        size--;

        // and the others, too
        for ( ; size > 0; size-- ) {
            out.append( ' ' );
            byteValue = in.get() & 0xFF;
            out.append( (char) highDigits[byteValue] );
            out.append( (char) lowDigits[byteValue] );
        }

        in.position( mark );

        return out.toString();
    }
}