package org.powerbot.util.io;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Adler32;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.powerbot.Configuration;
import org.powerbot.util.StringUtil;

/**
 * @author Paris
 */
public final class CryptFile {
	public static final Map<File, Class<?>[]> PERMISSIONS = new ConcurrentHashMap<File, Class<?>[]>();
	private static final long VECTOR = 0x9e3779b9;
	private static final String KEY_ALGO = "ARCFOUR", CIPHER_ALGO = "RC4";
	private final SecretKey key;
	private final File store;

	public CryptFile(final String name, final Class<?>... parents) {
		this(name, true);
	}

	public CryptFile(final String name, final boolean temp, final Class<?>... parents) {
		final File root = temp ? Configuration.TEMP : Configuration.HOME;
		store = new File(root, getHashedName(name));
		PERMISSIONS.put(store, parents);

		long k = Configuration.getUID() ^ VECTOR;
		final byte[] b = new byte[16];
		for (int j = 0; j < 2; j++) {
			for (int i = 0; i < 8; i++, k >>>= 8) {
				b[j * 8 + i] = (byte) (k & 0xff);
			}
		}
		key = new SecretKeySpec(b, 0, b.length, KEY_ALGO);
	}

	public boolean exists() {
		return store.isFile() && store.length() != 0L;
	}

	public long lastModified() {
		return store.lastModified();
	}

	public void delete() {
		store.delete();
	}

	public InputStream download(final URL url) throws IOException {
		return download(HttpClient.getHttpConnection(url));
	}

	public InputStream download(final HttpURLConnection con) throws IOException {
		if (exists()) {
			try {
				con.setIfModifiedSince(store.lastModified());
			} catch (final IllegalStateException ignored) {
			}
		}

		final long mod = con.getLastModified();

		if (con.getResponseCode() != HttpURLConnection.HTTP_NOT_MODIFIED &&
				(!exists() || mod == 0L || mod > store.lastModified())) {
			IOHelper.write(HttpClient.getInputStream(con), getOutputStream());
		}

		con.disconnect();
		return getInputStream();
	}

	public InputStream download(final String link, final Object... args) throws IOException {
		final String[] s = HttpClient.splitPostURL(link, args);
		final HttpURLConnection con = HttpClient.getHttpConnection(new URL(s[0]));

		if (store.exists()) {
			try {
				con.setIfModifiedSince(store.lastModified());
			} catch (final IllegalStateException ignored) {
			}
		}

		if (s.length > 1) {
			con.setDoOutput(true);
			if (s[1] != null && !s[1].isEmpty()) {
				OutputStreamWriter out = null;
				try {
					out = new OutputStreamWriter(con.getOutputStream());
					out.write(s[1]);
					out.flush();
				} catch (final IOException ignored) {
				} finally {
					if (out != null) {
						try {
							out.close();
						} catch (final IOException ignored) {
						}
					}
				}
			}
		}

		final long mod = con.getLastModified();

		if (con.getResponseCode() != HttpURLConnection.HTTP_NOT_MODIFIED &&
				(!store.exists() || mod == 0L || mod > store.lastModified())) {
			IOHelper.write(HttpClient.getInputStream(con), getOutputStream());
		}

		con.disconnect();
		return getInputStream();
	}

	public InputStream getInputStream() throws IOException {
		if (!store.isFile()) {
			throw new FileNotFoundException(store.toString());
		}
		//Files.getFileAttributeView(store.toPath(), BasicFileAttributeView.class).setTimes(null, FileTime.fromMillis(System.currentTimeMillis()), null);
		if (store.length() == 0L) {
			return new ByteArrayInputStream(new byte[]{});
		}
		final Cipher c;
		try {
			c = Cipher.getInstance(CIPHER_ALGO);
			c.init(Cipher.ENCRYPT_MODE, key);
		} catch (final GeneralSecurityException e) {
			throw new IOException(e);
		}
		return new CipherInputStream(new FileInputStream(store), c);
	}

	public OutputStream getOutputStream() throws IOException {
		final Cipher c;
		try {
			c = Cipher.getInstance(CIPHER_ALGO);
			c.init(Cipher.DECRYPT_MODE, key);
		} catch (final GeneralSecurityException e) {
			throw new IOException(e);
		}
		store.setLastModified(System.currentTimeMillis());
		return new CipherOutputStream(new FileOutputStream(store), c);
	}

	public static String getHashedName(final String name) {
		final long uid = Configuration.getUID();
		String hash;

		final MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-1");
			md.update(StringUtil.getBytesUtf8(name));
			for (int i = 0; i < 8; i++) {
				md.update((byte) ((uid >> (i << 3)) & 0xff));
			}
			hash = StringUtil.newStringUtf8(Base64.encode(md.digest()));
			hash = "etilqs_" + hash.replaceAll("[^A-Za-z0-0]", "").substring(0, 15);
		} catch (final NoSuchAlgorithmException ignored) {
			final Adler32 c = new Adler32();
			c.update(StringUtil.getBytesUtf8(name));
			c.update((int) (uid & Integer.MAX_VALUE));
			c.update((int) (uid >> 32));
			hash = Long.toHexString(c.getValue());
		}

		return hash;
	}
}
