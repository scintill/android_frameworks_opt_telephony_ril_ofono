package net.scintill;

import android.annotation.NonNull;
import android.net.LocalSocketAddress;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;

import dalvik.system.BlockGuard;

/*
 * LocalSocket that fixes some bad behavior on timeouts, and adds StrictMode enforcement.
 *
 * I was tempted to see if I could just implement it from "scratch" on top of the posix
 * wrapper APIs that already do the enforcement, but for now I will just extend the existing
 * LocalSocket.
 */
public class LocalSocket extends android.net.LocalSocket {

    public LocalSocket(int type) {
        super(type);
    }

    @Override
    public void connect(LocalSocketAddress endpoint) throws IOException {
        onNetwork(getCallerMethodName());
        super.connect(endpoint);
    }

    @Override
    public void connect(LocalSocketAddress endpoint, int timeout) throws IOException {
        onNetwork(getCallerMethodName());
        super.connect(endpoint, timeout);
    }

    @Override
    public void bind(LocalSocketAddress bindpoint) throws IOException {
        onNetwork(getCallerMethodName());
        super.bind(bindpoint);
    }

    @Override
    public void close() throws IOException {
        onNetwork(getCallerMethodName());
        super.close();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        final InputStream is = super.getInputStream();
        // XXX eww, we have to copy the overrides from LocalSocketImpl's impl to be sure we don't change any behavior
        return new InputStream() {
            @Override
            public int available() throws IOException {
                onNetwork(getCallerMethodName());
                return is.available();
            }

            /** {@inheritDoc} */
            @Override
            public void close() throws IOException {
                onNetwork(getCallerMethodName());
                is.close();
            }

            /** {@inheritDoc} */
            @Override
            public int read() throws IOException {
                onNetwork(getCallerMethodName());
                try {
                    return is.read();
                } catch (IOException e) {
                    throw convertTimeout(e);
                }
            }

            /** {@inheritDoc} */
            @Override
            public int read(@NonNull byte[] b) throws IOException {
                onNetwork(getCallerMethodName());
                try {
                    return is.read(b);
                } catch (IOException e) {
                    throw convertTimeout(e);
                }
            }

            /** {@inheritDoc} */
            @Override
            public int read(@NonNull byte[] b, int off, int len) throws IOException {
                onNetwork(getCallerMethodName());
                try {
                    return is.read(b, off, len);
                } catch (IOException e) {
                    throw convertTimeout(e);
                }
            }

            @Override
            public long skip(long l) throws IOException {
                onNetwork(getCallerMethodName());
                try {
                    return is.skip(l);
                } catch (IOException e) {
                    throw convertTimeout(e);
                }
            }

            private IOException convertTimeout(IOException e) {
                if (e.getMessage().equals("Try again")) {
                    return new SocketTimeoutException();
                } else {
                    return e;
                }
            }
        };
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        final OutputStream os = super.getOutputStream();
        return new OutputStream() {
            @Override
            public void write(@NonNull  byte[] bytes) throws IOException {
                onNetwork(getCallerMethodName());
                os.write(bytes);
            }

            @Override
            public void write(@NonNull  byte[] bytes, int i, int i1) throws IOException {
                onNetwork(getCallerMethodName());
                os.write(bytes, i, i1);
            }

            @Override
            public void write(int i) throws IOException {
                onNetwork(getCallerMethodName());
                os.write(i);
            }

            @Override
            public void flush() throws IOException {
                onNetwork(getCallerMethodName());
                os.flush();
            }

            @Override
            public void close() throws IOException {
                onNetwork(getCallerMethodName());
                os.close();
            }
        };
    }

    private static void onNetwork(String caller) {
        //Log.d("StrictMode", "onNetwork "+caller+"(): "+Thread.currentThread().getName());
        BlockGuard.getThreadPolicy().onNetwork();
    }

    private static String getCallerMethodName() {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        return elements[3].getMethodName();
    }

}