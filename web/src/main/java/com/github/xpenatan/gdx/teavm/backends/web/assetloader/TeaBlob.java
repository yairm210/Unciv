package com.github.xpenatan.gdx.teavm.backends.web.assetloader;

import java.io.IOException;
import java.io.InputStream;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Int8Array;

/**
 * Compatibility shim for snapshot artifact skew where backend runtime still
 * references TeaBlob while asset-loader renamed the class.
 */
public final class TeaBlob {

    private ArrayBuffer response;
    private final Int8Array data;

    public TeaBlob(ArrayBuffer response, Int8Array data) {
        this.data = data;
        this.response = response;
    }

    public Int8Array getData() {
        return data;
    }

    public ArrayBuffer getResponse() {
        return response;
    }

    public int length() {
        return data.getLength();
    }

    public byte get(int index) {
        return data.get(index);
    }

    public InputStream read() {
        return new InputStream() {
            private int position;

            @Override
            public int read() throws IOException {
                if (position == length()) return -1;
                return get(position++) & 0xff;
            }

            @Override
            public int available() {
                return length() - position;
            }
        };
    }
}
